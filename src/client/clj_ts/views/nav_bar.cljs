(ns clj-ts.views.nav-bar
  (:require [clj-ts.views.nav-input :refer [nav-input]]
            [clj-ts.handle :as handle]
            [reagent.core :as r]))

(defn nav-bar [db]
  (let [current (r/atom (-> @db :future last))]
    (fn []
      (let [start-page-name (-> @db :start-page-name)]
        [:div {:class "navbar"}
         [:div {:class "breadcrumbs"}
          [:span (-> @db :wiki-name)]]
         [:div {:id "nav1"}
          [:span {:on-click (fn [] (handle/go-new! db start-page-name))} start-page-name]
          " || "
          [:span {:on-click (fn [] (handle/go-new! db "ToDo"))} "Todo"]
          " || "
          [:span {:on-click (fn [] (handle/go-new! db "Work"))} "Work"]
          " || "
          [:span {:on-click (fn [] (handle/go-new! db "Projects"))} "Projects"]
          " || "
          [:span {:on-click (fn [] (handle/go-new! db "SandBox"))} "SandBox"]
          " || "
          [:a {:href "/api/exportallpages"} "Export All Pages"]]
         [:div {:id "nav2"}
          [:button
           {:class    "big-btn"
            :on-click (fn [] (handle/back! db))}
           [:img {:src "/icons/skip-back.png"}] " Back"]
          [:button
           {:class    "big-btn"
            :on-click (fn [] (handle/forward! db (-> @db :future last)))}
           ""
           [:img {:src "/icons/skip-forward.png"}] " Forward"]
          [:button {:class "big-btn"}
           [:a {:href "/api/rss/recentchanges"} [:img {:src "/icons/rss.png"}]]]]
         [:div {:id "nav3"}
          [nav-input current]
          [:button
           {:class    "big-btn"
            :on-click (fn [] (handle/go-new! db @current))}
           " Go!"]
          [:button
           {:class    "big-btn"
            :on-click (fn [] (handle/execute-clicked db current))}
           "Execute"]
          [:button
           {:class    "big-btn"
            :on-click (fn [] (handle/search-text! db (-> @current str)))}
           "Search"]]]))))
