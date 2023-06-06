(ns clj-ts.views.card-list
  (:require [clj-ts.mode :as mode]
            [reagent.core]
            [clj-ts.highlight :as highlight]
            [clj-ts.view :as view]
            [clj-ts.views.inner-html-card :refer [inner-html]]
            [clj-ts.views.card-shell :refer [card-shell]]
            [clj-ts.views.workspace-card :refer [workspace]]))

(defn card->component [db card]
  (let [render-type (get card "render_type")
        data (get card "server_prepared_data")
        inner-component (condp = render-type

                          "code"
                          {:reagent-render (fn [] (inner-html (str "<code>" data "</code>")))}

                          "raw"
                          {:reagent-render (fn [] (inner-html (str "<pre>" data "</pre>")))}

                          "markdown" {:reagent-render      (fn [] (inner-html (view/card->html card)))
                                       :component-did-mount highlight/highlight-all}

                          "manual-copy"
                          {:reagent-render (fn [] (inner-html
                                                    (str "<div class='manual-copy'>"
                                                         (view/card->html card)
                                                         "</div>")))}

                          "html"
                          {:reagent-render (fn [] (inner-html (str data)))}

                          "stamp"
                          {:reagent-render (fn [] (inner-html (str data)))}

                          "hiccup"
                          {:reagent-render (fn [] "THIS SHOULD BE HICCUP RENDERED")}

                          "workspace"
                          {:reagent-render (fn [] [workspace db card])}

                          (str "UNKNOWN TYPE ( " render-type " ) " data))
        class (reagent.core/create-class inner-component)]
    class))

(defn card-list [db]
  (reagent.core/create-class
    {:component-did-mount
     (fn [_this] (let [set-key (fn [card] (assoc card :key (random-uuid)))
                       cards (->> (:cards @db) (mapv set-key))
                       system-cards (->> (:system-cards @db) (mapv set-key))]
                   (swap! db assoc :cards cards)
                   (swap! db assoc :system-cards system-cards)))

     :reagent-render
     (fn [_this]
       (let [key-fn (fn [card] (or (get card "hash") (:key card)))]
         [:div {:on-double-click (fn [] (mode/set-edit-mode! db))}
          [:div
           (try
             (let [cards (-> @db :cards)]
               (for [card (filter view/not-blank? cards)]
                 (try
                   [:div {:key (key-fn card)} [(card-shell db) card (card->component db card)]]
                   (catch :default e
                     [:article {:class :card-outer}
                      [:div {:class :card}
                       [:h4 "Error"]
                       (str e)]]))))
             (catch :default e
               (do
                 (js/console.log "ERROR")
                 (js/console.log (str e))
                 (js/alert e))))]
          [:div
           (try
             (let [cards (-> @db :system-cards)]
               (for [card cards]
                 [:div {:key (key-fn card)} [(card-shell db) card (card->component db card)]]))
             (catch :default e
               (js/alert e)))]]))}))