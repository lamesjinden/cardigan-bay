(ns clj-ts.views.nav-bar
  (:require [clj-ts.http :as http]
            [clj-ts.view :as view]
            [clojure.string :as str]
            [reagent.core :as r]
            [promesa.core :as p]
            [clj-ts.navigation :as nav]
            [sci.core :as sci]))

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
  [:input {:type         "text"
           :class        :nav-input-text
           :value        @value
           :on-change    #(reset! value (-> % .-target .-value))
           :placeholder "Navigate, Search, or Eval"}])

(defn nav-bar [db]
  (let [current (r/atom (-> @db :future last))]
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
          [:button {:id    :rss-button
                    :class :big-btn}
           [:a {:href "/api/rss/recentchanges"}
            [:span {:class [:material-symbols-sharp :clickable]} "rss_feed"]]]]
         [:div {:id :header-input}
          [nav-input current]
          [:button
           {:id       :go-button
            :class    :big-btn
            :on-click (fn [] (navigate-async! db @current))}
           [:span {:class [:material-symbols-sharp :clickable]} "navigate_next"]]
          [:button
           {:class    :big-btn
            :on-click (fn [] (search-text-async! db (-> @current str)))}
           [:span {:class [:material-symbols-sharp :clickable]} "search"]]
          [:button
           {:id       :lambda-button
            :class    :big-btn
            :on-click (fn [] (eval-input! db current))}
           [:span {:class [:material-symbols-sharp :clickable]} "(λ)"]]]]))))
