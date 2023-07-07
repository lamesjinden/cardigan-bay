(ns clj-ts.views.nav-bar
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [promesa.core :as p]
            [sci.core :as sci]
            [clj-ts.http :as http]
            [clj-ts.mode :as mode]
            [clj-ts.view :as view]
            [clj-ts.navigation :as nav]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.views.app-menu :refer [app-menu]]))

;; region input

(defn- clear-input! [input-value]
  (reset! input-value nil))

(defn- on-clear-clicked [^Atom input-value]
  (clear-input! input-value))

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
    (mode/set-transcript-mode! db)))

(defn- load-search-results! [db cleaned-query e]
  (let [edn (-> e .-target .getResponseJson js->clj)
        result (get edn "result_text")]
    (prepend-transcript! db
                         (str "Searching for " cleaned-query)
                         (view/string->html result))))

(defn- search-text-async! [db query-text]
  (let [cleaned-query (-> (or query-text "")
                          (str/replace "\"" "")
                          (str/replace "'" "")
                          (str/trim))]
    (when (not (str/blank? cleaned-query))
      (let [query (->> {:query_string cleaned-query}
                       (clj->js)
                       (.stringify js/JSON))
            callback (fn [e] (load-search-results! db cleaned-query e))]
        (http/http-post-async
          "/api/search"
          callback
          query
          {:headers {"Content-Type" "application/json"}})))))

(defn- on-search-clicked [db query-text]
  (let [query-text (-> (or query-text "")
                       (str/trim))]
    (when (not (str/blank? query-text))
      (search-text-async! db query-text))))

(defn- on-navigate-clicked [db input-value]
  (let [inputValue (-> (or input-value "")
                       (str/trim))]
    (when (not (str/blank? inputValue))
      (navigate-async! db inputValue))))

;; endregion

;; region eval

(defn- eval-input! [db input-value]
  (let [code input-value
        result (sci/eval-string code)]
    (prepend-transcript! db code result)))

(defn- on-eval-clicked [db input-value]
  (let [current (-> (or input-value "")
                    (str/trim))]
    (when (not (str/blank? current))
      (eval-input! db current))))

;; endregion

(defn nav-input [db value]
  [:input {:type        "text"
           :class       :nav-input-text
           :value       @value
           :on-change   #(reset! value (-> % .-target .-value))
           :on-key-up   #(keyboard/nav-input-on-key-enter db %)
           :placeholder "Navigate, Search, or Eval"}])

(defn nav-bar [db db-nav-links]
  (let [inputValue (r/atom nil)]
    (fn []
      (let [nav-links @db-nav-links
            on-link-click (fn [target]
                            (if (= target "Transcript")
                              (swap! db assoc :mode :transcript)
                              (navigate-async! db target)))]
        [:div.nav-container
         [:nav#header-nav
          (->> nav-links
               (mapcat #(vector [:span {:key      %
                                        :on-click (fn [] (on-link-click %))} %])))
          [app-menu db (r/cursor db [:theme])]]
         [:div#header-input
          [nav-input db inputValue]
          [:div.header-input-actions
           (when (not (nil? @inputValue))
             [:button#close-button.header-input-button
              {:on-click (fn [] (on-clear-clicked inputValue))}
              [:span {:class [:material-symbols-sharp :clickable]} "close"]])
           (when (not (nil? @inputValue))
             [:div.header-input-separator])
           [:button#go-button.header-input-button
            {:on-click (fn [] (on-navigate-clicked db @inputValue))}
            [:span {:class [:material-symbols-sharp :clickable]} "navigate_next"]]
           [:button.header-input-button
            {:on-click (fn [] (on-search-clicked db @inputValue))}
            [:span {:class [:material-symbols-sharp :clickable]} "search"]]
           [:button#lambda-button.header-input-button
            {:on-click (fn [] (on-eval-clicked db @inputValue))}
            [:span {:class [:material-symbols-sharp :clickable]} "λ"]]]]]))))
