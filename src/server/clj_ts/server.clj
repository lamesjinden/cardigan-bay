(ns clj-ts.server
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
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

(defn get-page-data [server-snapshot arguments]
  (let [source-page (card-server/resolve-source-page server-snapshot nil arguments nil)
        server-prepared-page (card-server/resolve-page server-snapshot nil arguments nil)]
    {:source_page          source-page
     :server_prepared_page server-prepared-page}))

(defn handle-api-replace-card [{:keys [card-server] :as request}]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:page form-body)
        hash (:hash form-body)
        new-val (:data form-body)
        new-card (card-server/replace-card! card-server page-name hash new-val)]
    (if (= :not-found new-card)
      (util/create-not-found (str page-name "/" hash))
      (let [server-snapshot @card-server
            arguments {:page_name page-name}
            page-data (get-page-data server-snapshot arguments)
            response (-> (select-keys page-data [:source_page])
                         (assoc :replaced-hash hash)
                         (assoc :new-card new-card))]
        (-> response
            (json/write-str)
            (util/->json-response))))))

(defn export-page-handler [{:keys [card-server] :as request}]
  (let [page-name (-> request :params :page)
        server-snapshot @card-server
        result (export/export-one-page server-snapshot page-name)]
    (if (= result :not-found)
      (util/create-not-found page-name)
      (util/->zip-file-response result))))

(defn export-all-pages-handler [{:keys [card-server] :as _request}]
  (let [server-snapshot @card-server
        result (export/export-all-pages server-snapshot)]
    (if (= result :not-exported)
      (util/create-not-available "export all pages is not available")
      (util/->zip-file-response result))))

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

(defn handle-pages-request [{:keys [card-server] :as request}]
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

(defn handle-not-found [{:keys [uri] :as _request}]
  (util/create-not-found uri))

(def routes {:root                   {:get handle-root-request}
             :api-init               {:get handle-api-init}
             :api-page               {:post handle-api-page} ;; implement as a get
             :pages                  {:get handle-pages-request}
             :api-system-db          {:get handle-api-system-db}
             :api-search             {:post handle-api-search} ;; implement as a get
             :api-save               {:post handle-api-save}
             :api-move-card          {:post handle-api-move-card}
             :api-reorder-card       {:post handle-api-reorder-card}
             :api-replace-card       {:post handle-api-replace-card}
             :api-rss-recent-changes {:get handle-api-rss-recent-changes}
             :api-export-page        {:get export-page-handler}
             :api-export-all-ages    {:get export-all-pages-handler}
             :media                  {:get handle-media}
             :not-found              nil})                  ;; todo/note - how to return something that is get'able where any key maps to a single thing?

(defn router [uri]
  (cond
    (= uri "/") :root
    (= uri "/api/init") :api-init
    (= uri "/api/page") :api-page
    (re-matches pages-request-pattern uri) :pages
    (= uri "/api/system/db") :api-system-db
    (= uri "/api/search") :api-search
    (= uri "/api/save") :api-save
    (= uri "/api/movecard") :api-move-card
    (= uri "/api/reordercard") :api-reorder-card
    (= uri "/api/replacecard") :api-replace-card
    (= uri "/api/rss/recentchanges") :api-rss-recent-changes
    (= uri "/api/exportpage") :api-export-page
    (= uri "/api/exportallpages") :api-export-all-ages
    (re-matches #"/media/\S+" uri) :media
    :default :not-found))

(defn request-handler [request]
  (let [uri (:uri request)
        method (:request-method request)
        handler (as-> (router uri) $
                      (get routes $ {})
                      (get $ method handle-not-found))]
    (handler request)))

;; region main entry

(defn wrap-card-server [handler card-server-ref]
  (fn [request]
    (let [request (assoc request :card-server card-server-ref)]
      (handler request))))

(defn create-app
  "returns the ring request-handling pipeline"
  [^Atom card-server-ref]
  (-> #'request-handler
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
   :config     "./bedrock/system/config.edn"
   :nav-links  ["HelloWorld" "InQueue" "Transcript" "RecentChanges" "Help"]})

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-d" "--directory DIR" "Pages directory"]
   ["-n" "--name NAME" "Wiki Name"]
   ["-s" "--site SITE" "Site URL "]
   ["-i" "--init INIT" "Start Page"]
   ["-a" "--navlinks NAVLINKS" "Navigation Header Links"
    :id :nav-links
    :parse-fn (fn [arg] (mapv str/trim (str/split arg #",")))]
   ["-l" "--links LINK" "Export Links"]
   ["-x" "--extension EXPORTED_EXTENSION" "Exported Extension"]
   ["-e" "--export-dir DIR" "Export Directory"]
   ["-b" "--beginner IS_BEGINNER" "Is Beginner Rather Than Expert"
    :parse-fn boolean]
   ["-f" "--config CONFIG_PATH" "Path to configuration parameters file"]])

(defn- args->opts [args]
  (let [opts (cli/parse-opts args cli-options)
        options (get opts :options)]
    options))

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
         "Nav Links:\t" (:nav-links card-server-state) "\n"
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
  [settings]
  (let [page-store (pagestore/make-page-store (:directory settings) (:export-dir settings))
        page-exporter (export/make-page-exporter page-store (:extension settings) (:links settings))]

    (println (str "\n"
                  "Welcome to Cardigan Bay\n"
                  "======================="))

    (let [card-server-ref (card-server/create-card-server (:name settings) (:site settings) (:port settings) (:init settings) (:nav-links settings) nil page-store page-exporter)
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
