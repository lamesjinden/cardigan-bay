(ns clj-ts.server
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.tools.cli :as cli]
            [clj-ts.card-server :as card-server]
            [clj-ts.static-export :as export]
            [clj-ts.pagestore :as pagestore]
            [clj-ts.embed :as embed]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :as resp]
            [clojure.data.json :as json]
            [selmer.parser :as selmer])
  (:import (java.time LocalDateTime))
  (:gen-class))

;; Response

(defn create-not-found [uri-or-page-name]
  (-> (resp/not-found (str "Not found " uri-or-page-name))
      (resp/content-type "text")))

(defn create-ok []
  (-> "thank you"
      (resp/response)
      (resp/content-type "text/html")))

; Logic using pages

(defn raw-db []
  (card-server/regenerate-db!)
  (-> (str "<pre>" (with-out-str (pp/pprint (.raw-db (card-server/server-state)))) "</pre>")
      (resp/response)
      (resp/content-type "text/html")))

(defn move-card-handler [request]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:from form-body)
        hash (:hash form-body)
        new-page-name (:to form-body)]
    (card-server/move-card page-name hash new-page-name)
    (create-ok)))

(defn reorder-card-handler [request]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:page form-body)
        hash (:hash form-body)
        direction (:direction form-body)]
    (card-server/reorder-card page-name hash direction)
    (create-ok)))

(defn bookmarklet-handler [request]
  (let [url (-> request :params :url)
        data (embed/boilerplate url (LocalDateTime/now))]
    (if (string/includes? data "Bookmarked at ")
      (card-server/prepend-card-to-page! "InQueue" :markdown data)
      (card-server/prepend-card-to-page! "InQueue" :embed data))
    (resp/redirect "/view/InQueue" :see-other)))

(defn export-page-handler [request]
  (let [page-name (-> request :params :page)]
    (export/export-one-page page-name (card-server/server-state))
    (resp/redirect (str "/view/" page-name) :see-other)))

(defn export-all-pages-handler [_request]
  (export/export-all-pages (card-server/server-state))
  (resp/redirect (str "/view/" (-> card-server/server-state :start-page)) :see-other))

(defn media-file-handler [request]
  (let [uri (:uri request)
        file-name (-> uri
                      (#(re-matches #"/media/(\S+)" %))
                      second)
        file (card-server/load-media-file file-name)]
    (if (.isFile file)
      (resp/response file)
      (create-not-found uri))))

(defn custom-file-handler [request]
  (let [uri (:uri request)
        file-name (-> uri
                      (#(re-matches #"/custom/(\S+)" %))
                      second)
        file (card-server/load-custom-file file-name)]
    (if (.isFile file)
      (resp/response file)
      (create-not-found uri))))

(defn get-page-data [body]
  (let [source-page (card-server/resolve-source-page nil body nil)
        server-prepared-page (card-server/resolve-page nil body nil)]
    {:source_page          source-page
     :server_prepared_page server-prepared-page}))

;; using custom tag to take advantage of overriding :tag-second
;; as simple variable substitution is not as customizable
(selmer/add-tag! :identity (fn [args context-map]
                             (let [kw (keyword (first args))]
                               (get context-map kw))))

(def index-local-path "public/index.html")

(defn render-page-config
  ([subject-file page-name]
   (let [subject-content (slurp (io/resource subject-file))]
     (if page-name
       (let [page-config (get-page-data {:page_name page-name})
             page-config-str (json/write-str page-config)
             rendered (selmer.util/without-escaping
                        (selmer.parser/render
                          subject-content
                          {:page-config page-config-str}
                          {:tag-open   \[
                           :tag-close  \]
                           :tag-second \"}))]
         rendered)
       subject-content)))
  ([page-name]
   (render-page-config index-local-path page-name)))

(defn handle-root-request [_request]
  (-> index-local-path
      (render-page-config (.start-page (card-server/server-state)))
      (resp/response)
      (resp/content-type "text/html")))

(defn handle-api-init [_request]
  (let [init-page-name (.start-page (card-server/server-state))
        page-config (get-page-data {:page_name init-page-name})
        page-config-str (json/write-str page-config)]
    (-> page-config-str
        (resp/response)
        (resp/content-type "application/json"))))

(defn get-page-body [page-name]
  (let [arguments {:page_name page-name}]
    (json/write-str (get-page-data arguments))))

(defn get-page-response [page-name]
  (-> (get-page-body page-name)
      (resp/response)
      (resp/content-type "application/json")))

(defn handle-api-page [request]
  (let [body (:body request)
        page-name (:page_name body)]
    (get-page-response page-name)))

(defn handle-api-search [request]
  (let [body (:body request)]
    (-> (clj-ts.card-server/resolve-text-search nil body nil)
        (json/write-str)
        (resp/response)
        (resp/content-type "application/json"))))

(def pages-request-pattern #"/pages/(.+)")

(defn handle-page-request [request]
  (let [uri (:uri request)
        match (re-matches pages-request-pattern uri)
        page-name (ring.util.codec/url-decode (get match 1))]

    (if (clj-ts.card-server/page-exists? page-name)
      (-> index-local-path
          (render-page-config page-name)
          (resp/response)
          (resp/content-type "text/html"))
      (-> (resp/not-found (str "Page not found " page-name))
          (resp/content-type "text")))))

(defn handle-api-save [request]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:page form-body)
        body (:data form-body)]
    (card-server/write-page-to-file! page-name body)
    (get-page-response page-name)))

(defn handler [request]
  (let [uri (:uri request)]
    (cond
      (= uri "/") (handle-root-request request)
      (= uri "/api/init") (handle-api-init request)
      (= uri "/api/page") (handle-api-page request)
      (= uri "/api/search") (handle-api-search request)
      (re-matches pages-request-pattern uri) (handle-page-request request)

      (= uri "/api/save")
      (handle-api-save request)

      (= uri "/api/system/db")
      (raw-db)

      (= uri "/api/movecard")
      (move-card-handler request)

      (= uri "/api/reordercard")
      (reorder-card-handler request)

      (= uri "/api/rss/recentchanges")
      (-> (card-server/rss-recent-changes (fn [page-name]
                                            (str (-> (card-server/server-state)
                                                     :page-exporter
                                                     (.page-name->exported-link page-name)))))
          (resp/response)
          (resp/content-type "application/rss+xml"))

      ;; todo - redirects to /view, why?
      (= uri "/api/bookmarklet")
      (bookmarklet-handler request)

      ;; todo - redirects to /view, why?
      (= uri "/api/exportpage")
      (export-page-handler request)

      ;; todo - redirects to /view, why?
      (= uri "/api/exportallpages")
      (export-all-pages-handler request)

      (re-matches #"/media/(\S+)" uri)
      (media-file-handler request)

      (re-matches #"/custom/(\S+)" uri)
      (custom-file-handler request)

      ;; todo - what is happening here?
      ;; todo - a: sets page-name as the start page
      ;;        then redirects back to index.html
      ;;        which renders the new start-page
      (re-matches #"/view/(\S+)" uri)
      (let [page-name (second (re-matches #"/view/(\S+)" uri))]
        (card-server/set-start-page! page-name)
        {:status  303
         :headers {"Location" "/index.html"}})

      :default
      (create-not-found uri))))

;; region main entry

(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 4545
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-d" "--directory DIR" "Pages directory"
    :default "./bedrock/"
    :parse-fn str]
   ["-n" "--name NAME" "Wiki Name"
    :default "Yet Another CardiganBay Wiki"
    :parse-fn str]
   ["-s" "--site SITE" "Site URL "
    :default "/"
    :parse-fn str]
   ["-i" "--init INIT" "Start Page"
    :default "HelloWorld"
    :parse-fn str]
   ["-l" "--links LINK" "Export Links"
    :default "./"
    :parse-fn str]
   ["-x" "--extension EXPORTED_EXTENSION" "Exported Extension"
    :default ".html"
    :parse-fn str]
   ["-e" "--export-dir DIR" "Export Directory"
    :default "./bedrock/exported/"
    :parse-fn str]
   ["-b" "--beginner IS_BEGINNER" "Is Beginner Rather Than Expert"
    :default false
    :parse-fn boolean]])

(defn args->opts [args]
  (let [as (if *command-line-args* *command-line-args* args)
        xs (cli/parse-opts as cli-options)
        opts (get xs :options)]
    opts))

(defn create-app []
  (-> #'handler
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-body {:keywords? true})))

(defn init-app [opts]
  (let [ps (pagestore/make-page-store (:directory opts) (:export-dir opts))
        pe (export/make-page-exporter ps (:extension opts) (:links opts))]

    (println (str "\n"
                  "Welcome to Cardigan Bay\n"
                  "======================="))

    (card-server/initialize-state! (:name opts) (:site opts) (:port opts) (:init opts) nil ps pe)

    (println
      (str "\n"
           "Wiki Name:\t" (:wiki-name (card-server/server-state)) "\n"
           "Site URL:\t" (:site-url (card-server/server-state)) "\n"
           "Start Page:\t" (:start-page (card-server/server-state)) "\n"
           "Port No:\t" (:port-no (card-server/server-state)) "\n"
           "\n"
           "==PageStore Report==\n"
           "\n"
           (-> (card-server/server-state) :page-store .report)
           "\n"
           "==PageExporter Report==\n"
           "\n"
           (-> (card-server/server-state) :page-exporter .report)
           "\n"
           "\n"
           "-----------------------------------------------------------------------------------------------"
           "\n"))

    (card-server/regenerate-db!)))

(defn -main [& args]
  (let [opts (args->opts args)
        server-opts (select-keys opts [:port])]
    (init-app opts)
    (let [app (create-app)]
      (run-server app server-opts))))

;; endregion