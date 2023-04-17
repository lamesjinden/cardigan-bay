(ns clj-ts.views.tool-bar
  (:require [clj-ts.handle :as handle]))

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
                          (swap! db assoc :mode :viewing)
                          (handle/cancel-async! db))}
             [:div
              [:img {:src "/icons/x.png"}] [:span "Cancel"]]]
            [:button
             {:class    ["big-btn" "image-button"]
              :on-click (fn []
                          (swap! db assoc :mode :viewing)
                          (handle/save-page-async! db))}
             [:div
              [:img {:src "/icons/save.png"}] [:span "Save"]]]]]]

         :viewing
         [:span {:class ["button-container"]}
          [:button
           {:class    ["big-btn" "image-button"]
            :on-click #(swap! db assoc :mode :editing)}
           [:div
            [:img {:src "/icons/edit.png"}] [:span "Edit"]]]
          [:button
           {:class ["big-btn" "image-button"]}
           [:div
            [:a {:href (str "/api/exportpage?page=" (-> @db :current-page))}
             [:img {:src "/icons/package.png"}]
             [:span "Export"]]]]]

         :transcript
         [:span {:class ["button-container"]}
          [:button
           {:class    ["big-btn" "image-button"]
            :on-click #(swap! db assoc :mode :viewing)}
           [:div
            [:img {:src "/icons/x.png"}] [:span "Return"]]]])])))
