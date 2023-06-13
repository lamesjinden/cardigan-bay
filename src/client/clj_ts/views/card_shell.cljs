(ns clj-ts.views.card-shell
  (:require [reagent.core :as r]
            [clj-ts.card :refer [has-link-target? navigate-via-link-async!]]
            [clj-ts.view :refer [->display]]
            [clj-ts.views.card-bar :refer [card-bar]]))

(defn card-shell [db]
  (let [local-db (r/atom {:toggle true})
        toggle-local-expanded-state! (fn [e]
                                       (swap! local-db update :toggle not)
                                       (.preventDefault e))]

    ; listen for global expanded state changes and set local-db accordingly
    ; note: local state can still be updated via toggle-local-expanded-state!
    (swap! local-db assoc :toggle (= :expanded (:card-list-expanded-state @db)))

    (fn [card component]
      [:article.card-outer
       [:div.card-meta-parent
        [:div.card-meta
         [:span {:class    :toggle-container
                 :on-click toggle-local-expanded-state!}
          (if (not (-> @local-db :toggle))
            [:span {:class [:material-symbols-sharp :clickable]} "unfold_more"]
            [:span {:class [:material-symbols-sharp :clickable]} "unfold_less"])]]]
       [:div.card
        {:on-click (fn [e] (when (has-link-target? e)
                             (navigate-via-link-async! db e)))}
        [:div.card-parent {:class (when (-> @local-db :toggle not) :collapsed)}
         [:div.card-child.container
          [component]]
         [:div.card-child.overlay {:style {:display (-> @local-db :toggle not ->display)}}]]]
       [(card-bar card) db card]])))
