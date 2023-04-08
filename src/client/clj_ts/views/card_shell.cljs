(ns clj-ts.views.card-shell
  (:require [reagent.core :as r]
            [clj-ts.handle :as handle]
            [clj-ts.views.card-bar :refer [card-bar]]))

(defn card-shell [db]
  (let [state2 (r/atom {:toggle "block"})
        toggle! (fn [_]
                  (if (= (-> @state2 :toggle) "none")
                    (swap! state2 #(conj % {:toggle "block"}))
                    (swap! state2 #(conj % {:toggle "none"}))))]
    (fn [card component]
      [:div {:class :card-outer}
       [:div {:class :card-meta}
        [:span {:on-click toggle! :style {:size "smaller" :float "right"}}
         (if (= (-> @state2 :toggle) "none")
           [:img {:src "/icons/maximize-2.svg"}]
           [:img {:src "/icons/minimize-2.svg"}])]]
       [:div
        {:style {:spacing-top "5px"
                 :display     (-> @state2 :toggle)}}
        [:div
         {:class    "card"
          :on-click (fn [e] (handle/on-click-for-links-async! db e))}
         [component]]]
       [(card-bar card) db card]])))