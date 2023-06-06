(ns clj-ts.views.tool-bar
  (:require [clj-ts.mode :as mode]
            [clj-ts.page :as page]))

(defn tool-bar [db]
  (fn []
    (let [mode (-> @db :mode)]
      [:div
       {:class ["toolbar-container"]}
       (condp = mode

         :editing
         [:div
          [:div
           [:span {:class ["button-container"]}
            [:button
             {:class    ["big-btn" "image-button"]
              :on-click (fn []
                          (mode/set-view-mode! db)
                          (page/cancel-async! db))}
             [:div [:span {:class [:material-symbols-sharp :clickable]} "close"] "Cancel"]]
            [:button
             {:class    ["big-btn" "image-button"]
              :on-click (fn []
                          (mode/set-view-mode! db)
                          (page/save-page-async! db))}
             [:div
              [:span {:class [:material-symbols-sharp :clickable]} "save"] "Save"]]]]]

         :viewing
         [:span {:class ["button-container"]}
          [:button
           {:class    ["big-btn" "image-button"]
            :on-click #(swap! db assoc :mode :editing)}
           [:div
            [:span {:class [:material-symbols-sharp :clickable]} "edit"] [:span "Edit"]]]
          [:button
           {:class ["big-btn" "image-button"]}
           [:div
            [:a {:href (str "/api/exportpage?page=" (-> @db :current-page))}
             [:span {:class [:material-symbols-sharp :clickable]} "deployed_code_update"]
             [:span "Export"]]]]]

         :transcript
         [:span {:class ["button-container"]}
          [:button
           {:class    ["big-btn" "image-button"]
            :on-click #(mode/set-view-mode! db)}
           [:div [:span {:class [:material-symbols-sharp :clickable]} "close"] "Cancel"]]])])))
