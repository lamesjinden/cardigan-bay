(ns clj-ts.views.app-page-controls
  (:require [clj-ts.view :as view]))

(defn expand-all-cards [db]
  (swap! db assoc :card-list-expanded-state :expanded))

(defn collapse-all-cards [db]
  (swap! db assoc :card-list-expanded-state :collapsed))

(defn app-page-controls [db]
  [:div.page-controls-outer
   [:div.page-controls-container
    [:span {:class    [:material-symbols-sharp :clickable :left]
            :style    {:display (view/->display (= :viewing (:mode @db)))}
            :on-click (fn [e] (expand-all-cards db))} "unfold_more_double"]
    [:span {:class    [:material-symbols-sharp :clickable :right]
            :style    {:display (view/->display (= :viewing (:mode @db)))}
            :on-click (fn [e] (collapse-all-cards db))} "unfold_less_double"]]])