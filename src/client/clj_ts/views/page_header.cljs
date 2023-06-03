(ns clj-ts.views.page-header
  (:require [clj-ts.views.tool-bar :refer [tool-bar]]))

(defn page-header [db]
  (let [transcript? (= (-> @db :mode) :transcript)]
    (if transcript?
      [:div {:class [:page-header-container]}
       [:div {:class [:page-title-container]} [:h1 "Transcript"]]
       [tool-bar db]]
      [:div {:class [:page-header-container]}
       [:div {:class [:page-title-container]} [:h1 (-> @db :current-page)]]
       [tool-bar db]])))
