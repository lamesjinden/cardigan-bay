(ns clj-ts.handle
  (:require
    [clojure.string :as str]
    [promesa.core :as p]
    [sci.core :as sci]
    [clj-ts.http :as http]
    [clj-ts.view :as view]
    [clj-ts.events.navigation :as nav]
    [clj-ts.ace :refer [configure-ace-instance! ace-mode-markdown]]))

(defn updated-transcript [code result transcript]
  (str "<p> > " code "\n<br/>\n" result "\n</p>\n" transcript))

(defn prepend-transcript! [db code result]
  (let [current-transcript (-> @db :transcript)
        updated-transcript (updated-transcript code result current-transcript)]
    (swap! db assoc :transcript updated-transcript)
    (swap! db assoc :mode :transcript)))

(defn load-search-results! [db cleaned-query e]
  (let [edn (-> e .-target .getResponseJson js->clj)
        result (get edn "result_text")]
    (prepend-transcript! db
                         (str "Searching for " cleaned-query)
                         (view/string->html result))))

(defn search-text-async! [db query-text]
  (let [cleaned-query (-> query-text
                          (#(str/replace % "\"" ""))
                          (#(str/replace % "'" "")))
        query (->> {:query_string cleaned-query}
                   (clj->js)
                   (.stringify js/JSON))
        callback (fn [e] (load-search-results! db cleaned-query e))]
    (http/http-post-async
      "/api/search"
      callback
      query
      {:headers {"Content-Type" "application/json"}})))

(defn execute-clicked [db current]
  (let [code (-> @current str)
        result (sci/eval-string code)]
    (prepend-transcript! db code result)))

(defn cancel-async! [db]
  (nav/reload-async! db))

(defn save-page!
  ([db http-callback]
   (let [page-name (-> @db :current-page)
         ace-instance (:ace-instance @db)
         new-data (.getValue ace-instance)]
     (http/http-post-async
       "/clj_ts/save"
       http-callback
       (pr-str {:page page-name
                :data new-data}))))
  ([db]
   (let [http-callback (fn [] (nav/reload-async! db))]
     (save-page! db http-callback))))

(defn card-reorder-async! [db page-name hash direction]
  (http/http-post-async
    "/api/reordercard"
    (fn [] (nav/reload-async! db))
    (pr-str {:page      page-name
             :hash      hash
             :direction direction})))

(defn insert-text-at-cursor! [db s]
  (when s
    (when-let [ace-instance (:ace-instance @db)]
      (.insert ace-instance s))))

(defn card-send-to-page-async! [db page-name hash new-page-name]
  (let [move-card-p (http/http-post-async
                      "/api/movecard"
                      identity
                      (pr-str {:from page-name
                               :to   new-page-name
                               :hash hash}))]
    (p/then move-card-p
            (fn []
              (nav/go-new-async! db new-page-name)))))

(defn on-click-for-links-async! [db e]
  (let [tag (-> e .-target)
        classname (.getAttribute tag "class")
        data (.getAttribute tag "data")]
    (if (= classname "wikilink")
      (-> (nav/go-new-async! db data)
          (p/then (fn [] (clj-ts.events.navigation/navigate-to data))))
      (p/resolved nil))))

(defn on-click-for-nav-links-async! [db page-name]
  (-> (nav/go-new-async! db page-name)
      (p/then (fn [] (clj-ts.events.navigation/navigate-to page-name)))))

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

(def key-escape-code 27)

(defn editor-on-escape-press [db]
  (nav/reload-async! db))

(def key-s-code 83)

(defn editor-on-key-s-press [db e]
  (.preventDefault e)
  (save-page! db identity))

(defn editor-on-key-press [db e]
  (when (= (-> @db :mode) :editing)
    (let [key-code (.-keyCode e)
          control? (.-ctrlKey e)]
      (cond
        (and (= key-code key-s-code)
             control?)
        (editor-on-key-s-press db e)))))

(defn editor-on-key-up [db e]
  ;; note - escape doesn't fire for key-press, only key-up
  (when (= (-> @db :mode) :editing)
    (let [key-code (.-keyCode e)]
      (cond
        (= key-code key-escape-code)
        (editor-on-escape-press db)))))