(ns clj-ts.views.card-list
  (:require [reagent.core :as r]
            [clj-ts.highlight :as highlight]
            [clj-ts.view :as view]
            [clj-ts.views.inner-html-card :refer [inner-html]]
            [clj-ts.views.card-shell :refer [card-shell]]
            [clj-ts.views.workspace-card :refer [workspace]]))

(defn error-boundary
  [& children]
  (let [err-state (r/atom nil)]
    (r/create-class
      {:display-name        "ErrorBoundary"
       :component-did-catch (fn [err info]
                              (reset! err-state [err info]))
       :reagent-render      (fn [& children]
                              (if (nil? @err-state)
                                (into [:<>] children)
                                (let [[_ info] @err-state]
                                  [:pre.error-boundary
                                   [:code (pr-str info)]])))})))

(defn card->component [db card]
  (let [render-type (get card "render_type")
        data (get card "server_prepared_data")
        inner-component (condp = render-type

                          "markdown"
                          (r/create-class {:reagent-render      (fn [] (inner-html (view/card->html card)))
                                           :component-did-mount highlight/highlight-all})

                          "manual-copy"
                          [inner-html
                           (str "<div class='manual-copy'>"
                                (view/card->html card)
                                "</div>")]

                          "raw"
                          [inner-html (str "<pre>" data "</pre>")]

                          "code"
                          [inner-html (str "<code>" data "</code>")]

                          "workspace"
                          [workspace db card]

                          "html"
                          (r/create-class {:reagent-render      (fn [] (inner-html data))
                                           :component-did-mount highlight/highlight-all})

                          "hiccup"
                          [data]

                          (str "UNKNOWN TYPE ( " render-type " ) " data))]
    inner-component))

(defn error-card [exception]
  {"render_type"          "hiccup"
   "server_prepared_data" [:div
                           [:h4 "Error"]
                           [:div (str exception)]
                           [:div (.-stack exception)]]})

(defn card-list [db db-cards db-system-cards]
  (r/create-class
    {:component-did-mount
     (fn [_this] (let [set-key (fn [card] (assoc card :key (random-uuid)))
                       cards (->> @db-cards (mapv set-key))
                       system-cards (->> @db-system-cards (mapv set-key))]
                   (swap! db assoc :cards cards)
                   (swap! db assoc :system-cards system-cards)))

     :reagent-render
     (fn [_this]
       (let [key-fn (fn [card] (or (get card "hash") (:key card)))]
         [:<>
          [:div.user-card-list
           (let [cards @db-cards]
             (for [card (filter view/not-blank? cards)]
               [:div.user-card-list-item {:key (key-fn card)}
                (try
                  [card-shell db card
                   [error-boundary
                    [card->component db card]]]
                  (catch :default e
                    (let [error-card (error-card e)]
                      [card-shell db error-card
                       [error-boundary
                        [card->component db error-card]]])))]))]
          [:div.system-card-list
           (try
             (let [system-cards @db-system-cards]
               (for [system-card system-cards]
                 [:div.system-card-list-item {:key (key-fn system-card)}
                  [card-shell db system-card
                   [error-boundary
                    [card->component db system-card]]]]))
             (catch :default e
               (let [error-card (error-card e)]
                 [card-shell db error-card
                  [error-boundary
                   [card->component db error-card]]])))]]))}))