(ns clj-ts.views.nav-bar
  (:require [clj-ts.http :as http]
            [clj-ts.view :as view]
            [clojure.string :as str]
            [reagent.core :as r]
            [promesa.core :as p]
            [clj-ts.navigation :as nav]
            [sci.core :as sci]))

;; region input

(defn clear-input! [inputValue]
  (reset! inputValue nil))

;; endregion

;; region navigation

(defn- navigate-async! [db page-name]
  (-> (nav/go-new-async! db page-name)
      (p/then (fn [] (nav/navigate-to page-name)))))

;; endregion

;; region search

(defn- updated-transcript [code result transcript]
  (str "<p> > " code "\n<br/>\n" result "\n</p>\n" transcript))

(defn- prepend-transcript! [db code result]
  (let [current-transcript (-> @db :transcript)
        updated-transcript (updated-transcript code result current-transcript)]
    (swap! db assoc :transcript updated-transcript)
    (swap! db assoc :mode :transcript)))

(defn- load-search-results! [db cleaned-query e]
  (let [edn (-> e .-target .getResponseJson js->clj)
        result (get edn "result_text")]
    (prepend-transcript! db
                         (str "Searching for " cleaned-query)
                         (view/string->html result))))

(defn- search-text-async! [db query-text]
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

;; endregion

;; region eval

(defn- eval-input! [db current]
  (let [code (-> @current str)
        result (sci/eval-string code)]
    (prepend-transcript! db code result)))

;; endregion

(defn nav-input [value]
  [:input {:type        "text"
           :class       :nav-input-text
           :value       @value
           :on-change   #(reset! value (-> % .-target .-value))
           :placeholder "Navigate, Search, or Eval"}])

(defn nav-bar [db]
  (let [inputValue (r/atom nil)]
    (fn []
      (let [nav-links (-> @db :nav-links)
            on-link-click (fn [target]
                            (if (= target "Transcript")
                              (swap! db assoc :mode :transcript)
                              (navigate-async! db target)))]
        [:div {:class :nav-container}
         [:nav {:id :header-nav}
          (->> nav-links
               (mapcat #(vector [:span {:key      %
                                        :on-click (fn [] (on-link-click %))} %]
                                [:span {:key   (str % "-spacer")
                                        :class :nav-spacer}])))
          [:a {:href "/api/exportallpages"} "Export All"]
          [:a.rss_link {:href "/api/rss/recentchanges"}
           [:span {:class [:material-symbols-sharp :clickable]} "rss_feed"]]]
         [:div {:id :header-input}
          [nav-input inputValue]
          [:button.header-input-button
           {:id       :close-button
            :style    {:display (view/->display (not (nil? @inputValue)) :flex)}
            :on-click (fn [] (clear-input! inputValue))}
           [:span {:class [:material-symbols-sharp :clickable]} "close"]
           [:span.header-input-separator]]
          [:button.header-input-button
           {:id       :go-button
            :on-click (fn [] (navigate-async! db @inputValue))}
           [:span {:class [:material-symbols-sharp :clickable]} "navigate_next"]]
          [:button.header-input-button
           {:on-click (fn [] (search-text-async! db (-> @inputValue str)))}
           [:span {:class [:material-symbols-sharp :clickable]} "search"]]
          [:button.header-input-button
           {:id       :lambda-button
            :on-click (fn [] (eval-input! db inputValue))}
           [:span {:class [:material-symbols-sharp :clickable]} "(λ)"]]]]))))
