(ns clj-ts.views.nav-bar
  (:require [clj-ts.handle :as handle]
            [reagent.core :as r]))

(defn nav-input [value]
  [:input {:type      "text"
           :id        "navinputbox"
           :value     @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn nav-bar [db]
  (let [current (r/atom (-> @db :future last))]
    (fn []
      (let [start-page-name (-> @db :start-page-name)]
        [:div {:class :navbar}
         [:div {:id "nav1"}
          [:span {:on-click (fn [] (handle/on-click-for-nav-links-async! db start-page-name))} start-page-name]
          [:span {:class :nav-spacer}]
          [:span {:on-click (fn [] (handle/on-click-for-nav-links-async! db "ToDo"))} "Todo"]
          [:span {:class :nav-spacer}]
          [:span {:on-click (fn [] (handle/on-click-for-nav-links-async! db "Work"))} "Work"]
          [:span {:class :nav-spacer}]
          [:span {:on-click (fn [] (handle/on-click-for-nav-links-async! db "Projects"))} "Projects"]
          [:span {:class :nav-spacer}]
          [:span {:on-click (fn [] (handle/on-click-for-nav-links-async! db "SandBox"))} "SandBox"]
          [:span {:class :nav-spacer}]
          [:a {:href "/api/exportallpages"} "Export All"]
          [:button {:id    "rss-button"
                    :class :big-btn}
           [:a {:href "/api/rss/recentchanges"}
            [:span {:class [:material-symbols-sharp :clickable]} "rss_feed"]]]]
         [:div {:id "nav3"}
          [nav-input current]
          [:button
           {:id       "go-button"
            :class    :big-btn
            :on-click (fn [] (handle/on-click-for-nav-links-async! db @current))}
           [:span {:class [:material-symbols-sharp :clickable]} "navigate_next"]]
          [:button
           {:id       :lambda-button
            :class    :big-btn
            :on-click (fn [] (handle/execute-clicked db current))}
           [:span {:class [:material-symbols-sharp :clickable]} "(λ)"]]
          [:button
           {:class    :big-btn
            :on-click (fn [] (handle/search-text-async! db (-> @current str)))}
           [:span {:class [:material-symbols-sharp :clickable]} "search"]]]]))))
