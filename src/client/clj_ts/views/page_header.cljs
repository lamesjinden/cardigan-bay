(ns clj-ts.views.page-header
  (:require [clj-ts.views.tool-bar :refer [tool-bar]]))

(defn page-header [db db-mode db-current-page]
  (let [transcript? (= @db-mode :transcript)]
    (if transcript?
      [:div.page-header-container
       [:div.page-title-container
        [:h1 "Transcript"]]
       [tool-bar db]]
      [:div.page-header-container
       [:div.page-title-container
        [:h1 @db-current-page]]
       [tool-bar db]])))
