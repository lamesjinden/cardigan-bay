(ns clj-ts.server
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.cli :as cli]
            [clj-ts.util :as util]
            [clj-ts.render :as render]
            [clj-ts.card-server :as card-server]
            [clj-ts.export.static-export :as export]
            [clj-ts.storage.page_store :as pagestore]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :as resp]
            [clojure.data.json :as json]
            [selmer.parser :as selmer])
  (:gen-class)
  (:import (clojure.lang Atom)))

(defn handle-api-system-db [{:keys [card-server] :as _request}]
  ;; todo - questionable: need to call regenerate-db! on a GET
  (card-server/regenerate-db! card-server)
  (-> @card-server
      (render/raw-db)
      (util/->html-response)))

(defn handle-api-move-card [{:keys [card-server] :as request}]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:from form-body)
        hash (:hash form-body)
        new-page-name (:to form-body)]
    (card-server/move-card! card-server page-name hash new-page-name)
    (util/create-ok)))

(defn handle-api-reorder-card [{:keys [card-server] :as request}]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:page form-body)
        hash (:hash form-body)
        direction (:direction form-body)]
    (card-server/reorder-card! card-server page-name hash direction)
    (util/create-ok)))

(defn handle-api-replace-card [{:keys [card-server] :as request}]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:page form-body)
        hash (:hash form-body)
        new-val (:data form-body)]
    (card-server/replace-card! card-server page-name hash new-val)
    ;; todo - return full page or card
    (util/create-ok)))

(defn export-page-handler [{:keys [card-server] :as request}]
  (let [page-name (-> request :params :page)
        server-snapshot @card-server]
    (export/export-one-page page-name server-snapshot)
    (resp/redirect (str "/view/" page-name) :see-other)))

(defn export-all-pages-handler [{:keys [card-server] :as _request}]
  (let [server-snapshot @card-server]
    (export/export-all-pages server-snapshot)
    (resp/redirect (str "/view/" (-> server-snapshot :start-page)) :see-other)))

(defn get-page-data [server-snapshot body]
  (let [source-page (card-server/resolve-source-page server-snapshot nil body nil)
        server-prepared-page (card-server/resolve-page server-snapshot nil body nil)]
    {:source_page          source-page
     :server_prepared_page server-prepared-page}))

;; using custom tag to take advantage of overriding :tag-second
;; as simple variable substitution is not as customizable
(selmer/add-tag! :identity (fn [args context-map]
                             (let [kw (keyword (first args))]
                               (get context-map kw))))

(def index-local-path "public/index.html")

(defn render-page-config
  ([card-server subject-file page-name]
   (let [subject-content (slurp (io/resource subject-file))]
     (if page-name
       (let [server-snapshot @card-server
             page-config (get-page-data server-snapshot {:page_name page-name})
             page-config-str (json/write-str page-config)
             rendered (selmer.util/without-escaping
                        (selmer.parser/render
                          subject-content
                          {:page-config page-config-str}
                          {:tag-open   \[
                           :tag-close  \]
                           :tag-second \"}))]
         rendered)
       subject-content))))

(defn handle-root-request [{:keys [card-server] :as _request}]
  (let [server-snapshot @card-server]
    (-> (render-page-config card-server index-local-path (.start-page server-snapshot))
        (util/->html-response))))

(defn handle-api-init [{:keys [card-server] :as _request}]
  (let [server-snapshot @card-server
        init-page-name (.start-page server-snapshot)
        page-config (get-page-data server-snapshot {:page_name init-page-name})
        page-config-str (json/write-str page-config)]
    (util/->json-response page-config-str)))

(defn get-page-body [card-server page-name]
  (let [arguments {:page_name page-name}
        server-snapshot @card-server]
    (json/write-str (get-page-data server-snapshot arguments))))

(defn get-page-response [card-server page-name]
  (-> (get-page-body card-server page-name)
      (util/->json-response)))

(defn handle-api-page [{:keys [card-server] :as request}]
  (let [body (:body request)
        page-name (:page_name body)]
    (get-page-response card-server page-name)))

(defn handle-api-search [{:keys [card-server] :as request}]
  (let [body (:body request)
        server-snapshot @card-server]
    (-> (clj-ts.card-server/resolve-text-search server-snapshot nil body nil)
        (json/write-str)
        (util/->json-response))))

(def pages-request-pattern #"/pages/(.+)")

(defn handle-page-request [{:keys [card-server] :as request}]
  (let [uri (:uri request)
        match (re-matches pages-request-pattern uri)
        page-name (ring.util.codec/url-decode (get match 1))
        server-snapshot @card-server]
    (if (clj-ts.card-server/page-exists? server-snapshot page-name)
      (-> (render-page-config card-server index-local-path page-name)
          (util/->html-response))
      (-> (resp/not-found (str "Page not found " page-name))
          (resp/content-type "text")))))

(defn handle-api-save [{:keys [card-server] :as request}]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:page form-body)
        body (:data form-body)]
    (card-server/write-page-to-file! card-server page-name body)
    (get-page-response card-server page-name)))

(defn handle-api-rss-recent-changes [{:keys [card-server] :as _request}]
  (let [server-snapshot @card-server]
    (-> (card-server/rss-recent-changes
          server-snapshot
          (fn [page-name]
            (str (-> server-snapshot
                     :page-exporter
                     (.page-name->exported-link page-name)))))
        (resp/response)
        (resp/content-type "application/rss+xml"))))

(defn handle-view [{:keys [uri card-server] :as _request}]
  (let [page-name (second (re-matches #"/view/(\S+)" uri))]
    (card-server/set-start-page! card-server page-name)
    {:status  303
     :headers {"Location" "/index.html"}}))

(defn handle-media [{:keys [card-server uri] :as _request}]
  (let [file-name (-> uri
                      (#(re-matches #"/media/(\S+)" %))
                      second)
        server-snapshot @card-server
        file (card-server/load-media-file server-snapshot file-name)]
    (if (.isFile file)
      {:status 200
       :body   file}
      (util/create-not-found uri))))

(defn handler [request]
  (let [uri (:uri request)]
    (cond

      ;; read requests
      (= uri "/") (handle-root-request request)
      (= uri "/api/init") (handle-api-init request)
      (= uri "/api/page") (handle-api-page request)
      (re-matches pages-request-pattern uri) (handle-page-request request)
      (= uri "/api/system/db") (handle-api-system-db request)
      (= uri "/api/search") (handle-api-search request)

      ;; write requests
      (= uri "/api/save") (handle-api-save request)
      (= uri "/api/movecard") (handle-api-move-card request)
      (= uri "/api/reordercard") (handle-api-reorder-card request)
      (= uri "/api/replacecard") (handle-api-replace-card request)

      ;; rss requests
      (= uri "/api/rss/recentchanges") (handle-api-rss-recent-changes request)

      ;; export requests
      ;; todo - redirects to /view, why?
      (= uri "/api/exportpage") (export-page-handler request)
      ;; todo - redirects to /view, why?
      (= uri "/api/exportallpages") (export-all-pages-handler request)
      ;; todo - what is happening here?
      ;; todo - a: sets page-name as the start page
      ;;        then redirects back to index.html
      ;;        which renders the new start-page
      (re-matches #"/view/\S+" uri)
      (handle-view request)

      ;; media requests
      (re-matches #"/media/\S+" uri)
      (handle-media request)

      :default
      (util/create-not-found uri))))

;; region main entry

(defn wrap-card-server [handler card-server-ref]
  (fn [request]
    (let [request (assoc request :card-server card-server-ref)]
      (handler request))))

(defn create-app
  "returns the ring request-handling pipeline"
  [^Atom card-server-ref]
  (-> #'handler
      (wrap-card-server card-server-ref)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-body {:keywords? true})))

(def default-options
  {:port       4545
   :directory  "./bedrock/"
   :name       "Yet Another CardiganBay Wiki"
   :site       "/"
   :init       "HelloWorld"
   :links      "./"
   :extension  ".html"
   :export-dir "./bedrock/exported/"
   :beginner   false
   :config     "./bedrock/system/config.edn"})

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-d" "--directory DIR" "Pages directory"]
   ["-n" "--name NAME" "Wiki Name"]
   ["-s" "--site SITE" "Site URL "]
   ["-i" "--init INIT" "Start Page"]
   ["-l" "--links LINK" "Export Links"]
   ["-x" "--extension EXPORTED_EXTENSION" "Exported Extension"]
   ["-e" "--export-dir DIR" "Export Directory"]
   ["-b" "--beginner IS_BEGINNER" "Is Beginner Rather Than Expert"
    :parse-fn boolean]
   ["-f" "--config CONFIG_PATH" "Path to configuration parameters file"]])

(defn- args->opts [args]
  (let [as (if *command-line-args* *command-line-args* args)
        xs (cli/parse-opts as cli-options)
        opts (get xs :options)]
    opts))

(defn- read-config-file [config-file-path]
  (try
    (-> config-file-path
        slurp
        edn/read-string)
    (catch Exception _
      {})))

(defn gather-settings [cli-args]
  (let [cli-settings (args->opts cli-args)
        config-file-path (or (:config cli-settings) (:config default-options))
        config-settings (read-config-file config-file-path)
        settings (merge default-options config-settings cli-settings)]
    settings))

(defn- print-card-server-state [card-server-state]
  (println
    (str "\n"
         "Wiki Name:\t" (:wiki-name card-server-state) "\n"
         "Site URL:\t" (:site-url card-server-state) "\n"
         "Start Page:\t" (:start-page card-server-state) "\n"
         "Port No:\t" (:port-no card-server-state) "\n"
         "\n"
         "==PageStore Report==\n"
         "\n"
         (-> card-server-state :page-store .report)
         "\n"
         "==PageExporter Report==\n"
         "\n"
         (-> card-server-state :page-exporter .report)
         "\n"
         "\n"
         "-----------------------------------------------------------------------------------------------"
         "\n")))

(defn initialize-state
  "initializes server state contained within an Atom and returns it"
  [opts]
  (let [page-store (pagestore/make-page-store (:directory opts) (:export-dir opts))
        page-exporter (export/make-page-exporter page-store (:extension opts) (:links opts))]

    (println (str "\n"
                  "Welcome to Cardigan Bay\n"
                  "======================="))

    (let [card-server-ref (card-server/create-card-server (:name opts) (:site opts) (:port opts) (:init opts) nil page-store page-exporter)
          card-server-state @card-server-ref]
      (print-card-server-state card-server-state)
      (card-server/regenerate-db! card-server-ref)
      card-server-ref)))

(defn -main [& args]
  (let [settings (gather-settings args)
        server-opts (select-keys settings [:port])
        card-server (initialize-state settings)]
    (let [app (create-app card-server)]
      (run-server app server-opts))))

;; endregion
