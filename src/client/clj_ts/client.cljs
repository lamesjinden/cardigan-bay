(ns clj-ts.client
  (:require
    [clojure.string :as str]
    [reagent.core :as r]
    [reagent.dom :as dom]
    [promesa.core :as p]
    [clj-ts.handle :as handle]
    [clj-ts.events.navigation :as nav]
    [clj-ts.views.nav-bar :refer [nav-bar]]
    [clj-ts.views.tool-bar :refer [tool-bar]]
    [clj-ts.views.card-list :refer [card-list]]
    [clj-ts.views.transcript :refer [transcript]]
    [clj-ts.views.editor :refer [editor]]
    [clj-ts.views.footer :refer [footer]]))

;; region top-level ratom

(defonce db (r/atom
              {:current-page "HelloWorld"
               :raw          ""
               :transcript   ""
               :cards        []
               :wiki-name    "Wiki Name"
               :site-url     "Site URL"
               :initialized? false
               :mode         :viewing
               :env-port     4545}))

;; endregion

;; region top-level components

(defn header-bar []
  [:div {:class "headerbar"}
   [:div
    [:div [nav-bar db]]]])

(defn page-header []
  [:h2
   (if (= (-> @db :mode) :transcript)
     "Transcript"
     [:span
      (-> @db :current-page)
      [:span {:class "tslink"}
       [:a {:href (str
                    (str/replace (-> @db :site-url) #"/$" "")
                    "/" (-> @db :current-page))} " (public)"]]])])

(defn main-container []
  (let [mode (:mode @db)]
    [:div
     [:div
      (condp = mode

        :editing
        [editor db]

        :viewing
        [:div {:on-double-click (fn [] (handle/set-edit-mode db))}
         [card-list db]]

        :transcript
        [:div
         [transcript db]])]]))

(defn content []
  [:div {:class "main-container"}
   [header-bar]
   [:div {:class "context-box"}
    [page-header]
    [:div [tool-bar db]]
    [main-container]]
   [footer db]])

;; endregion

;; region page load

; request and load the start-page

(def resolved (p/resolved 0))

(defn configure-async! []
  (if (:initialized? @db)
    resolved
    (let [init (first (.-init js/window))
          loaded-p (if (object? init)
                     (p/resolved (nav/load-page db init))
                     (nav/load-start-page-async! db))]
      (p/then loaded-p (fn []
                         (swap! db assoc :mode :viewing)
                         (swap! db assoc :initialized? true)
                         (nav/hook-pop-state db)
                         (nav/replace-state-initial)
                         (js/window.scroll 0 0))))))

(defn render-app []
  (dom/render [content] (.querySelector js/document "#app")))

(-> (configure-async!)
    (p/then (fn [] (render-app))))

;; endregion
