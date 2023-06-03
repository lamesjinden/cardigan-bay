(ns clj-ts.views.card-shell
  (:require [reagent.core :as r]
            [clj-ts.handle :as handle]
            [clj-ts.views.card-bar :refer [card-bar]]))

(defn card-shell [db]
  (let [state2 (r/atom {:toggle :block})
        toggle! (fn [_]
                  (if (= (-> @state2 :toggle) :none)
                    (swap! state2 #(conj % {:toggle :block}))
                    (swap! state2 #(conj % {:toggle :none}))))]
    (fn [card component]
      [:article {:class :card-outer}
       [:div {:class :card-meta}
        [:span {:class    :toggle-container
                :on-click toggle!}
         (if (= (-> @state2 :toggle) :none)
           [:span {:class [:material-symbols-sharp :clickable]} "open_in_full"]
           [:span {:class [:material-symbols-sharp :clickable]} "close_fullscreen"])]]
       [:div
        {:class :card-inner
         :style {:display (-> @state2 :toggle)}}
        [:div
         {:class    :card
          :on-click (fn [e] (handle/on-click-for-links-async! db e))}
         [component]]]
       [(card-bar card) db card]])))