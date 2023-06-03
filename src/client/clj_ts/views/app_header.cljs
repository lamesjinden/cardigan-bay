(ns clj-ts.views.app_header
  (:require [clj-ts.views.nav-bar :refer [nav-bar]]
            [clj-ts.views.page-header :refer [page-header]]))

(defn app-header [db]
  [:header {:class :header-bar}
   [nav-bar db]
   [page-header db]])