(ns clj-ts.views.card-bar
  (:require [reagent.core :as r]
            [clj-ts.handle :as handle]))

(defn send-to-input-box [value]
  [:input {:type      "text"
           :id        "sendto-inputbox"
           :value     @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn card-bar [card]
  (let [meta-id (str "cardmeta" (get card "hash"))
        state (r/atom {:toggle "none"})
        send-value (r/atom "")
        toggle! (fn []
                  (if (= (-> @state :toggle) "none")
                    (swap! state #(conj % {:toggle "block"}))
                    (swap! state #(conj % {:toggle "none"}))))]
    (fn [db card]
      [:div {:class :card-meta}
       [:div
        [:span {:on-click (fn [e] (handle/card-reorder!
                                    db
                                    (-> @db :current-page)
                                    (get card "hash")
                                    "up"))}
         [:img {:src "/icons/chevrons-up.png"}]]
        [:span {:on-click (fn [e] (handle/card-reorder!
                                    db
                                    (-> @db :current-page)
                                    (get card "hash")
                                    "down"))}
         [:img {:src "/icons/chevrons-down.png"}]]
        [:span {:on-click toggle! :style {:size "smaller" :float "right"}}
         (if (= (-> @state :toggle) "none")
           [:img {:src "/icons/eye.png"}]
           [:img {:src "/icons/eye-off.png"}])]]
       [:div {:id meta-id :style {:spacing-top "5px" :display (-> @state :toggle)}}
        [:div [:h4 "Card Bar"]]
        [:div
         [:span "ID: " (get card "id")] " | Hash: "
         [:span (get card "hash")] " | Source type: "
         [:span (get card "source_type")] " | Render type: "
         [:span (get card "render_type")]]
        [:div
         [:span
          "Send to Another Page : "
          [send-to-input-box send-value]
          [:input {:name "hash" :id "sendhash" :type "hidden" :value (get card "hash")}]
          [:input {:name "from" :id "sendcurrent" :type "hidden" :value (-> @db :current-page)}]
          [:img {:src "/icons/send.png"}]
          [:button {:on-click
                    (fn []
                      (handle/card-send-to-page!
                        db
                        (-> @db :current-page)
                        (get card "hash")
                        @send-value))} "Send"]]]]])))