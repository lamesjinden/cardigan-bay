(ns clj-ts.client
  (:require
    [cljs.core]
    [clojure.string :as str]
    [reagent.core :as r]
    [reagent.dom :as dom]
    [clj-ts.handle :as handle]
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
               :past         []
               :future       []
               :wiki-name    "Wiki Name"
               :site-url     "Site URL"
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
(when (empty? (:past @db))
  (handle/load-start-page! db))

; request page render
(dom/render
  [content]
  (.querySelector js/document "#app"))

;; endregion