(ns clj-ts.views.card-list
  (:require [reagent.core :as r]
            [clj-ts.highlight :as highlight]
            [clj-ts.view :as view]
            [clj-ts.views.inner-html-card :refer [inner-html]]
            [clj-ts.views.card-shell :refer [card-shell]]
            [clj-ts.views.workspace-card :refer [workspace]]))

(defn card->component [db card]
  (let [render-type (get card "render_type")
        data (get card "server_prepared_data")
        inner-component (condp = render-type

                          "markdown" {:reagent-render      (fn [] (inner-html (view/card->html card)))
                                      :component-did-mount highlight/highlight-all}

                          "manual-copy"
                          {:reagent-render (fn [] (inner-html
                                                    (str "<div class='manual-copy'>"
                                                         (view/card->html card)
                                                         "</div>")))}

                          "raw"
                          {:reagent-render (fn [] (inner-html (str "<pre>" data "</pre>")))}

                          "code"
                          {:reagent-render (fn [] (inner-html (str "<code>" data "</code>")))}

                          "workspace"
                          {:reagent-render (fn [] [workspace db card])}

                          "html"
                          {:reagent-render      (fn [] (inner-html data))
                           :component-did-mount highlight/highlight-all}

                          "hiccup"
                          {:reagent-render (fn [] data)}

                          (str "UNKNOWN TYPE ( " render-type " ) " data))
        class (r/create-class inner-component)]
    class))

(defn error-card [exception]
  {"render_type"          "hiccup"
   "server_prepared_data" [:div
                           [:h4 "Error"]
                           [:div (str exception)]
                           [:div (.-stack exception)]]})

(defn card-list [db db-cards db-system-cards]

  (let [rx-card-list-expanded (r/cursor db [:card-list-expanded-state])]
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
                [:div.user-car-list-item {:key (key-fn card)}
                 (try
                   [card-shell db rx-card-list-expanded card (card->component db card)]
                   (catch :default e
                     (let [error-card (error-card e)]
                       [card-shell db rx-card-list-expanded error-card (card->component db error-card)])))]))]
           [:div.system-card-list
            (try
              (let [system-cards @db-system-cards]
                (for [system-card system-cards]
                  [:div.system-card-list-item {:key (key-fn system-card)}
                   [card-shell db rx-card-list-expanded system-card (card->component db system-card)]]))
              (catch :default e
                (let [error-card (error-card e)]
                  [card-shell db rx-card-list-expanded error-card (card->component db error-card)])))]]))})))