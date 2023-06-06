(ns clj-ts.views.card-shell
  (:require [reagent.core :as r]
            [clj-ts.card :refer [has-link-target? navigate-via-link-async!]]
            [clj-ts.view :refer [->display]]
            [clj-ts.views.card-bar :refer [card-bar]]))

(def transparent-background "linear-gradient(0deg, #FFFFFF 10%, rgba(255,255,255,0.5) 100%)")

(defn card-shell [db]
  (let [state2 (r/atom {:toggle true})
        toggle! (fn [e]
                  (swap! state2 assoc :toggle (not (-> @state2 :toggle)))
                  (.preventDefault e))]
    (fn [card component]
      [:article.card-outer
       [:div.card-meta-parent
        [:div.card-meta {:class :card-meta}
         [:span {:class    :toggle-container
                 :on-click toggle!}
          (if (not (-> @state2 :toggle))
            [:span {:class [:material-symbols-sharp :clickable]} "open_in_full"]
            [:span {:class [:material-symbols-sharp :clickable]} "close_fullscreen"])]]]
       [:div
        {:class :card-inner}
        [:div.card
         {:on-click (fn [e] (when (has-link-target? e)
                              (navigate-via-link-async! db e)))}
         [:div.card-parent {:style {:height (if (-> @state2 :toggle)
                                              "auto"
                                              "80px")}}
          [:div.card-child {:style {:overflow :hidden}}
           [component]]
          [:div.card-child {:style {:background transparent-background
                                    :z-index    1
                                    :display    (-> @state2 :toggle not ->display)}}]]]]
       [(card-bar card) db card]])))
