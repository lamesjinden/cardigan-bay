(ns clj-ts.views.app-page-controls
  (:require [clojure.core.async :as a]
            [clj-ts.mode :as mode]))

(defn expand-all-cards [db]
  (a/put! (:card-list-expanded$ @db) :expanded))

(defn collapse-all-cards [db]
  (a/put! (:card-list-expanded$ @db) :collapsed))

(defn app-page-controls [db db-mode]
  (when (mode/viewing? db-mode)
    [:div.page-controls-container
     [:span {:class    [:material-symbols-sharp :clickable :left]
             :on-click (fn [] (expand-all-cards db))} "unfold_more_double"]
     [:span {:class    [:material-symbols-sharp :clickable :right]
             :on-click (fn [] (collapse-all-cards db))} "unfold_less_double"]]))