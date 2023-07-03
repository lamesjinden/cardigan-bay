(ns clj-ts.views.app-page-controls
  (:require [clj-ts.mode :as mode]))

(defn expand-all-cards [db]
  (swap! db assoc :card-list-expanded-state :expanded))

(defn collapse-all-cards [db]
  (swap! db assoc :card-list-expanded-state :collapsed))

(defn app-page-controls [db]
  (when (mode/viewing? db)
    [:div.page-controls-container
     [:span {:class    [:material-symbols-sharp :clickable :left]
             :on-click (fn [] (expand-all-cards db))} "unfold_more_double"]
     [:span {:class    [:material-symbols-sharp :clickable :right]
             :on-click (fn [] (collapse-all-cards db))} "unfold_less_double"]]))