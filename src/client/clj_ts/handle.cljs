(ns clj-ts.handle
  (:require
    [clojure.string :as str]
    [sci.core :as sci]
    [clj-ts.http :as http]
    [clj-ts.view :as view]
    [clj-ts.api.query :as query]
    [clj-ts.ace :refer [configure-ace-instance! ace-mode-markdown]]))

(defn- ->text-search-query [cleaned-query]
  (str "{\"query\" : \"query TextSearch  {
text_search(query_string:\\\"" cleaned-query "\\\"){     result_text }
}\",  \"variables\":null, \"operationName\":\"TextSearch\"   }"))

(defn updated-transcript [code result transcript]
  (str "<p> > " code "\n<br/>\n" result "\n</p>\n" transcript))

(defn prepend-transcript! [db code result]
  (let [current-transcript (-> @db :transcript)
        updated-transcript (updated-transcript code result current-transcript)]
    (swap! db assoc :transcript updated-transcript)
    (swap! db assoc :mode :transcript)))

(defn search-text! [db query-text]
  (let [cleaned-query (-> query-text
                          (#(str/replace % "\"" ""))
                          (#(str/replace % "'" "")))
        query (->text-search-query cleaned-query)
        callback (fn [e]
                   (let [edn (-> e .-target .getResponseText .toString (#(.parse js/JSON %)) js->clj)
                         data (-> edn (get "data"))
                         result (-> data (get "text_search") (get "result_text"))]
                     (prepend-transcript! db (str "Searching for " cleaned-query) (view/string->html result))))]
    (http/http-post
      "/clj_ts/graphql"
      callback
      query)))

(defn execute-clicked [db current]
  (let [code (-> @current str)
        result (sci/eval-string code)]
    (prepend-transcript! db code result)))

(defn load-page! [db page-name new-past new-future]
  (let [query (query/->load-page-query page-name)
        callback (fn [e]
                   (let [edn (-> e .-target .getResponseText .toString
                                 (#(.parse js/JSON %)) js->clj)
                         data (-> edn (get "data"))
                         raw (-> data (get "source_page") (get "body"))
                         cards (-> data (get "server_prepared_page") (get "cards"))
                         system-cards (-> data (get "server_prepared_page") (get "system_cards"))
                         site-url (-> data (get "server_prepared_page") (get "site_url"))
                         wiki-name (-> data (get "server_prepared_page") (get "wiki_name"))
                         port (-> data (get "server_prepared_page") (get "port"))
                         ip (-> data (get "server_prepared_page") (get "ip"))
                         start-page-name (-> data (get "server_prepared_page") (get "start_page_name"))]
                     (swap! db assoc
                            :current-page page-name
                            :site-url site-url
                            :wiki-name wiki-name
                            :port port
                            :ip ip
                            :start-page-name start-page-name
                            :raw raw
                            :cards cards
                            :system-cards system-cards
                            :past new-past
                            :future new-future))
                   (js/window.scroll 0 0))]
    (http/http-post "/clj_ts/graphql" callback query)))

(defn reload! [db]
  (load-page!
    db
    (:current-page @db)
    (-> @db :past)
    (-> @db :future)))

(defn go-new! [db page-name]
  (load-page!
    db
    page-name
    (conj (-> @db :past)
          (-> @db :current-page))
    [])
  (swap! db assoc :mode :viewing))

(defn forward! [db page-name]
  (when page-name
    (load-page!
      db
      page-name
      (conj (-> @db :past)
            (-> @db :current-page))
      (pop (-> @db :future)))))

(defn back! [db]
  (load-page!
    db
    (-> @db :past last)
    (pop (-> @db :past))
    (conj (-> @db :future)
          (-> @db :current-page))))

(defn save-page! [db]
  (let [page-name (-> @db :current-page)
        ace-instance (:ace-instance @db)
        new-data (.getValue ace-instance)]
    (http/http-post
      "/clj_ts/save"
      (fn [_] (reload! db))
      (pr-str {:page page-name
               :data new-data}))))

(defn card-reorder! [db page-name hash direction]
  (http/http-post
    "/api/reordercard"
    (fn [] (reload! db))
    (pr-str {:page      page-name
             :hash      hash
             :direction direction})))

(defn insert-text-at-cursor! [db s]
  (when s
    (when-let [ace-instance (:ace-instance @db)]
      (.insert ace-instance s))))

(defn card-send-to-page! [db page-name hash new-page-name]
  (http/http-post
    "/api/movecard"
    (fn [] (go-new! db new-page-name))
    (pr-str {:from page-name
             :to   new-page-name
             :hash hash})))

(defn on-click-for-links [db e]
  (let [tag (-> e .-target)
        classname (.getAttribute tag "class")
        data (.getAttribute tag "data")
        x (-> @db :dirty)]
    (if (= classname "wikilink")
      (go-new! db data))))

(defn set-edit-mode [db]
  (swap! db assoc :mode :editing))

(defn setup-editor [db]
  (let [editor-element (first (array-seq (.getElementsByClassName js/document "edit-box")))
        ace-instance (.edit js/ace editor-element)]
    (configure-ace-instance! ace-instance ace-mode-markdown {:fontSize "1.2rem"})
    (swap! db assoc :ace-instance ace-instance)))

(defn destroy-editor [db]
  (let [editor (:editor @db)]
    (when editor
      (.destroy editor))))

(defn exit-edit-mode-on-escape-press [e db]
  (let [kc (.-keyCode e)
        escape-code 27]
    (when (and (= (-> @db :mode) :editing)
               (= kc escape-code))
      (swap! db assoc :mode :viewing))))

(defn load-start-page! [db]
  (let [url "/startpage"
        callback (fn [e] (let [start-page (-> e .-target .getResponseText .toString)]
                           (go-new! db start-page)))]
    (http/http-get url callback)))