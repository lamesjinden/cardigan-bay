(ns clj-ts.views.footer
  (:require [clj-ts.views.bookmarklet-footer :refer [bookmarklet-footer-link]]))

(defn footer [db]
  [:div {:class "footer"}
   [:span
    [:span "This " (-> @db :wiki-name) " wiki!"]
    [:span " || Home : " [:a {:href (-> @db :site-url)} (-> @db :site-url)] " || "]
    [:span [:a {:href "/api/system/db"} "DB"] " || "]
    [:a {:href "https://github.com/interstar/cardigan-bay"} "Cardigan Bay "]
    "(c) Phil Jones 2020-2022  || "
    [:span "IP: " (str (-> @db :ip)) " || "]
    [bookmarklet-footer-link db]]])