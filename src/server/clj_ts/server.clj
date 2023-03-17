(ns clj-ts.server
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.tools.cli :as cli]
            [clj-ts.card-server :as card-server]
            [clj-ts.common :as common]
            [clj-ts.static-export :as export]
            [clj-ts.pagestore :as pagestore]
            [clj-ts.embed :as embed]
            [markdown.core :as md]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :as resp]
            [com.walmartlabs.lacinia :refer [execute]]
            [clojure.data.json :as json])
  (:import (java.time LocalDateTime))
  (:gen-class))

;; Requests

(defn page-request [request]
  (let [qs (:query-string request)
        p-name (second (string/split qs #"="))
        raw (if (card-server/page-exists? p-name)
              (card-server/read-page p-name)
              "PAGE DOES NOT EXIST")]
    {:p-name p-name :raw raw}))

(defn render-page [raw]
  (let [cards (string/split raw #"----")
        card #(str "<div class='card'>" (md/md-to-html-string %) "</div>")]
    (apply str (map card cards))))

(defn get-page [request]
  (let [{:keys [raw]} (page-request request)]
    (-> (render-page raw)
        (resp/response)
        (resp/content-type "text/html"))))

(defn get-raw [request]
  (let [{:keys [raw]} (page-request request)]
    (-> raw
        (resp/response)
        (resp/content-type "text/html"))))

(defn get-edn-cards [request]
  (let [{:keys [raw]} (page-request request)
        cards (card-server/raw->cards raw :false nil)]
    (-> cards
        (resp/response)
        (resp/content-type "text/html"))))

(defn save-page [request]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        p-name (:page form-body)
        body (:data form-body)]
    (card-server/write-page-to-file! p-name body)
    (-> "thank you"
        (resp/response)
        (resp/content-type "text/html"))))

(defn get-flattened [request]
  (let [{:keys [p-name]} (page-request request)
        cards (-> p-name card-server/load->cards common/cards->raw)]
    (-> cards
        (resp/response)
        (resp/content-type "text/html"))))

; Logic using pages

(defn wrap-results-as-list [res]
  (str
    "<div>"
    (apply str
           "<ul>"
           (for [p res]
             (apply str "<li>"
                    (if (coll? p)
                      (string/join ",," (for [q p] (str "<a href=''>" q "</a>")))
                      (str "<a href=''>" p "</a>"))
                    "</li>")))
    "</ul></div>"))

(defn retn [res]
  (-> (wrap-results-as-list res)
      (resp/response)
      (resp/content-type "text/html")))

(defn raw-db [_request]
  (card-server/regenerate-db!)
  (-> (str "<pre>" (with-out-str (pp/pprint (.raw-db (card-server/server-state)))) "</pre>")
      (resp/response)
      (resp/content-type "text/html")))

(defn get-start-page [_request]
  (-> (-> (card-server/server-state) .start-page)
      (resp/response)
      (resp/content-type "text/html")))

;; GraphQL handler

(defn extract-query
  "Reads the `query` query parameters, which contains a JSON string
  for the GraphQL query associated with this request. Returns a
  string.  Note that this differs from the PersistentArrayMap returned
  by variable-map. e.g. The variable map is a hashmap whereas the
  query is still a plain string."
  [request]
  (let [body (-> request :body .bytes slurp (json/read-str :key-fn keyword) :query)]
    (case (:request-method request)
      :get (get-in request [:query-params "query"])
      ;; Additional error handling because the clojure ring server still
      ;; hasn't handed over the values of the request to lacinia GraphQL
      ;; (-> request :body .bytes slurp edn/read-string)
      :post (try (-> request
                     :body
                     .bytes
                     slurp
                     (json/read-str :key-fn keyword)
                     :query)
                 (catch Exception e ""))
      :else "")))

(defn graphql-handler [request]
  (let [query (extract-query request)
        result (execute card-server/pagestore-schema query nil nil)
        body (json/write-str result)]
    (-> body
        (resp/response)
        (resp/content-type "application/json"))))

(defn icons-handler [request]
  (let [uri (:uri request)
        file (io/file (System/getProperty "user.dir") (str "." uri))]
    (when (.isFile file)
      (-> file
          (resp/response)
          (resp/content-type "image/png")))))

(defn move-card-handler [request]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:from form-body)
        hash (:hash form-body)
        new-page-name (:to form-body)]
    (card-server/move-card page-name hash new-page-name)
    (-> "thank you"
        (resp/response)
        (resp/content-type "text/html"))))

(defn reorder-card-handler [request]
  (let [form-body (-> request :body .bytes slurp edn/read-string)
        page-name (:page form-body)
        hash (:hash form-body)
        direction (:direction form-body)]
    (card-server/reorder-card page-name hash direction)
    (-> "thank you"
        (resp/response)
        (resp/content-type "text/html"))))

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
  (future
    (export/export-all-pages (card-server/server-state))
    (println "Export finished"))
  (resp/redirect (str "/view/" (-> card-server/server-state :start-page)) :see-other))

(defn media-file-handler [request]
  (let [file-name (-> request :uri
                      (#(re-matches #"/media/(\S+)" %))
                      second)
        file (card-server/load-media-file file-name)]
    (println "Media file request " file-name)
    (if (.isFile file)
      (resp/response file)
      (resp/not-found "Media file not found"))))

(defn custom-file-handler [request]
  (let [file-name (-> request :uri
                      (#(re-matches #"/custom/(\S+)" %))
                      second)
        file (card-server/load-custom-file file-name)]
    (if (.isFile file)
      (resp/response file)
      (resp/not-found "Media file not found"))))

(defn handler [{:keys [uri] :as request}]
  (let [view-matches (re-matches #"/view/(\S+)" uri)]

    (cond
      (= uri "/")
      (->
        (resp/resource-response "index.html" {:root "public"})
        (resp/content-type "text/html"))

      (= uri "/startpage")
      (get-start-page request)

      (= uri "/clj_ts/old")
      (get-page request)

      (= uri "/clj_ts/save")
      (save-page request)

      (= uri "/clj_ts/graphql")
      (graphql-handler request)

      (= uri "/api/system/db")
      (raw-db request)

      (= uri "/api/movecard")
      (move-card-handler request)

      (= uri "/api/reordercard")
      (reorder-card-handler request)

      (= uri "/api/rss/recentchanges")
      {:status  200
       :headers {"Content-Type" "application/rss+xml"}
       :body    (card-server/rss-recent-changes
                  (fn [p-name]
                    (str (-> (card-server/server-state)
                             :page-exporter
                             (.page-name->exported-link p-name)))))}

      (= uri "/api/bookmarklet")
      (bookmarklet-handler request)

      (= uri "/api/exportpage")
      (export-page-handler request)

      (= uri "/api/exportallpages")
      (export-all-pages-handler request)

      (= uri "/custom/main.css")
      (custom-file-handler request)

      (re-matches #"/icons/(\S+)" uri)
      (icons-handler request)

      (re-matches #"/media/(\S+)" uri)
      (media-file-handler request)

      (re-matches #"/custom/(\S+)" uri)
      (custom-file-handler request)

      view-matches
      (let [pagename (-> view-matches second)]
        (do
          (card-server/set-start-page! pagename)
          {:status  303
           :headers {"Location" "/index.html"}}))

      :otherwise
      (do
        (or
          ;; if the request is a static file
          (let [file (io/file (System/getProperty "user.dir") (str "." uri))]
            (when (.isFile file)
              {:status 200
               :body   file}))
          (resp/not-found
            (do
              (println "Page not found " uri)
              "Page not found")))))))

;; Parse command line args
(def cli-options
  [
   ["-p" "--port PORT" "Port number"
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
      (wrap-params)))

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
           "\n"
           ))

    (card-server/regenerate-db!)))

; runs when the server starts
(defn -main [& args]
  (let [opts (args->opts args)
        server-opts (select-keys opts [:port])]
    (init-app opts)
    (let [app (create-app)]
      (run-server app server-opts))))
