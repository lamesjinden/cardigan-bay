(ns clj-ts.views.tool-bar
  (:require [clj-ts.handle :as handle]
            [clj-ts.views.paste-bar :refer [paste-bar]]))

(defn tool-bar [db]
  (fn []
    (let [mode (-> @db :mode)]
      [:div
       (condp = mode

         :editing
         [:div
          [:div
           [:span
            [:button
             {:class    "big-btn"
              :on-click (fn []
                          (swap! db assoc :mode :viewing)
                          (handle/reload! db))}
             [:img {:src "/icons/x.png"}] " Cancel"]
            [:button
             {:class    "big-btn"
              :on-click (fn []
                          (swap! db assoc :mode :viewing)
                          (handle/save-page! db))}
             [:img {:src "/icons/save.png"}] " Save"]]]
          (paste-bar db)]

         :viewing
         [:span
          [:button
           {:class    "big-btn"
            :on-click #(swap! db assoc :mode :editing)}
           [:img {:src "/icons/edit.png"}] " Edit"]
          [:button
           {:class "big-btn"}
           [:a {:href (str "/api/exportpage?page=" (-> @db :current-page))}
            [:img {:src "/icons/package.png"}] " Export"]]]
         :transcript
         [:span
          [:button
           {:class    "big-btn"
            :on-click #(swap! db assoc :mode :viewing)}
           [:img {:src "/icons/x.png"}] " Return"]])])))
