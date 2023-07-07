(ns clj-ts.views.card-bar
  (:require [clj-ts.http :as http]
            [clj-ts.page :as page]
            [reagent.core :as r]
            [clj-ts.mode :as mode]
            [clj-ts.navigation :as nav]
            [clj-ts.view :as view]
            [promesa.core :as p]))

(defn clip-hash [from-page hash]
  (view/send-to-clipboard
    (str "----
:transclude

{:from \"" from-page "\"
 :ids [\"" hash "\"] } ")))

(defn on-save-clicked-async! [db card]
  (-> (page/save-card-async!
        (-> @db :current-page)
        (get card "hash")
        (-> js/document
            (.getElementById (str "edit-" (get card "hash")))
            .-value))
      (p/then (fn [_] (nav/reload-async! db)))
      (p/then (fn [_] (mode/set-view-mode! db)))))

(defn card-send-to-page-async! [db page-name hash new-page-name]
  (let [move-card-p (http/http-post-async
                      "/api/movecard"
                      identity
                      (pr-str {:from page-name
                               :to   new-page-name
                               :hash hash}))]
    (p/then move-card-p
            (fn []
              (nav/go-new-async! db new-page-name)))))

(defn card-reorder-async! [db page-name hash direction]
  (http/http-post-async
    "/api/reordercard"
    (fn [] (nav/reload-async! db))
    (pr-str {:page      page-name
             :hash      hash
             :direction direction})))

(defn toggle! [state]
  (if (= (-> @state :toggle) "none")
    (swap! state #(conj % {:toggle "block"}))
    (swap! state #(conj % {:toggle "none"}))))

(defn card-bar [db card]
  (let [meta-id (str "cardmeta" (get card "hash"))
        state (r/atom {:toggle "none"})
        send-value (r/atom "")
        toggle-fn! (fn [] (toggle! state))]
    (fn [db card]
      [:div
       [:div {:class :card-gutter}
        [:div {:on-click (fn [] (card-reorder-async!
                                  db
                                  (-> @db :current-page)
                                  (get card "hash")
                                  "up"))
               :class    [:material-symbols-sharp :clickable]}
         "expand_less"]
        [:div {:on-click (fn [] (card-reorder-async!
                                  db
                                  (-> @db :current-page)
                                  (get card "hash")
                                  "down"))
               :class    [:material-symbols-sharp :clickable]}
         "expand_more"]
        [:span {:class    :card-gutter-expansion-toggle
                :on-click toggle-fn!}
         (if (= (-> @state :toggle) "none")
           [:span {:class [:material-symbols-sharp :clickable]} "expand_circle_down"]
           [:span {:class [:material-symbols-sharp :clickable]} "expand_circle_up"])]]
       [:div {:id    meta-id
              :class :card-bar
              :style {:display (-> @state :toggle)}}
        [:div [:h3 "Card Bar"]]
        [:div
         [:span "ID: " (get card "id")] " | Hash: "
         [:span {:class "mini-button" :on-click (fn [] (clip-hash (-> @db :current-page)
                                                                  (get card "hash")))}
          "Hash: " (get card "hash")] " | Source type: "
         [:span (get card "source_type")] " | Render type: "
         [:span (get card "render_type")]]
        [:hr]
        [:div {:class :send-to-bar}
         [:h4 "Send to Another Page"]
         [:div {:class :send-to-inner}
          [:input {:name "hash" :id "sendhash" :type "hidden" :value (get card "hash")}]
          [:input {:name "from" :id "sendcurrent" :type "hidden" :value (-> @db :current-page)}]
          [:input {:type      "text"
                   :id        "sendto-inputbox"
                   :value     @send-value
                   :on-change #(reset! send-value (-> % .-target .-value))}]
          [:button {:on-click
                    (fn []
                      (card-send-to-page-async!
                        db
                        (-> @db :current-page)
                        (get card "hash")
                        @send-value))} "Send"]]]]])))