(ns clj-ts.views.card-bar
  (:require [reagent.core :as r]
            [clj-ts.handle :as handle]
            [clj-ts.view :as view]))

(defn send-to-input-box [value]
  [:input {:type      "text"
           :id        "sendto-inputbox"
           :value     @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn clip-hash [from-page hash]
  (view/send-to-clipboard
    (str "----
:transclude

{:from \"" from-page "\"
 :ids [\"" hash "\"] } ")))

(defn toggle! [state card]
  (if (= (-> @state :toggle) "none")
    (do
      (swap! state #(conj % {:toggle "block"}))
      (-> js/document
          (.getElementById (str "edit-" (get card "hash")))
          (.-value)
          (set! (get card "source_data"))))
    (swap! state #(conj % {:toggle "none"}))))

(defn card-bar [card]
  (let [meta-id (str "cardmeta" (get card "hash"))
        state (r/atom {:toggle "none"})
        send-value (r/atom "")
        toggle-fn! (fn [] (toggle! state card))]
    (fn [db card]
      [:div {:class :card-meta}
       [:div
        [:span {:on-click (fn [] (handle/card-reorder-async!
                                   db
                                   (-> @db :current-page)
                                   (get card "hash")
                                   "up"))}
         [:img {:src "/icons/chevrons-up.png"}]]
        [:span {:on-click (fn [] (handle/card-reorder-async!
                                   db
                                   (-> @db :current-page)
                                   (get card "hash")
                                   "down"))}
         [:img {:src "/icons/chevrons-down.png"}]]
        [:span {:on-click toggle-fn!
                :style    {:size "smaller" :float "right"}}
         (if (= (-> @state :toggle) "none")
           [:img {:src "/icons/eye.png"}]
           [:img {:src "/icons/eye-off.png"}])]]
       [:div {:id meta-id :style {:spacing-top "5px" :display (-> @state :toggle)}}
        [:div [:h4 "Card Bar"]]
        [:div
         [:span "ID: " (get card "id")] " | Hash: "
         [:span {:class "mini-button" :on-click (fn [] (clip-hash (-> @db :current-page)
                                                                  (get card "hash")))}
          "Hash: " (get card "hash")] " | Source type: "
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
                      (handle/card-send-to-page-async!
                        db
                        (-> @db :current-page)
                        (get card "hash")
                        @send-value))} "Send"]]]
        [:div
         [:span "Edit Card"]
         [:div
          [:textarea {:id    (str "edit-" (get card "hash"))
                      :rows  10
                      :width "100%"}]]
         [:div
          [:span
           [:button {:class    "big-btn"
                     :on-click (fn [] (handle/on-edit-card-cancel-clicked db))}
            [:img {:src "/icons/x.png"}] " Cancel"]
           [:button {:class    "big-btn"
                     :on-click (fn [] (handle/on-edit-card-clicked db card))}
            [:img {:src "/icons/save.png"}] " Save"]]]]]])))