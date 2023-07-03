(ns clj-ts.views.app-header
  (:require [reagent.core :as r]
            [clj-ts.views.nav-bar :refer [nav-bar]]
            [clj-ts.views.page-header :refer [page-header]]))

(defn app-header [db]
  (let [rx-mode (r/cursor db [:mode])
        rx-current-page (r/cursor db [:current-page])]
    [:header {:class :header-bar}
     [nav-bar db]
     [page-header db rx-mode rx-current-page]]))