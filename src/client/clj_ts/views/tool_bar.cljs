(ns clj-ts.views.tool-bar
  (:require [clj-ts.mode :as mode]
            [clj-ts.page :as page]))

(defn tool-bar [db db-mode db-current-page]
  (fn []
    (let [mode @db-mode]
      [:div.toolbar-container
       (condp = mode

         :editing
         [:div
          [:span.button-container
           [:button.big-btn.big-btn-left
            {:on-click (fn []
                         (mode/set-view-mode! db)
                         (page/cancel-async! db))}
            [:span {:class [:material-symbols-sharp :clickable]} "close"]]
           [:button.big-btn.big-btn-right
            {:on-click (fn []
                         (mode/set-view-mode! db)
                         (page/save-page-async! db))}
            [:span {:class [:material-symbols-sharp :clickable]} "save"]]]]

         :viewing
         [:span.button-container
          [:button.big-btn.big-btn-left
           {:on-click #(swap! db assoc :mode :editing)}
           [:span {:class [:material-symbols-sharp :clickable]} "edit"]]
          [:button.big-btn.big-btn-right
           [:a {:href (str "/api/exportpage?page=" @db-current-page)}
            [:span {:class [:material-symbols-sharp :clickable]} "deployed_code_update"]]]]

         :transcript
         [:button.big-btn
          {:on-click #(mode/set-view-mode! db)}
          [:span {:class [:material-symbols-sharp :clickable]} "close"]])])))
