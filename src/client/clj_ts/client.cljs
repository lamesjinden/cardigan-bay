(ns clj-ts.client
  (:require
    [reagent.core :as r]
    [reagent.dom :as dom]
    [promesa.core :as p]
    [clj-ts.events.actions :as actions]
    [clj-ts.events.navigation :as nav]
    [clj-ts.views.app_header :refer [app-header]]
    [clj-ts.views.app-main :refer [app-main]]))

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

(defn app []
  [:div {:class :app-container}
   [app-header db]
   [app-main db]])

;; endregion

;; region page load

; request and load the start-page

(def resolved (p/resolved 0))

(defn configure-async! []
  (if (:initialized? @db)
    resolved
    (let [init (first (.-init js/window))
          loaded-p (if (object? init)
                     ;; todo - delete window["init"]
                     (p/resolved (nav/load-page db init))
                     (nav/load-start-page-async! db))]
      (p/then loaded-p (fn []
                         (actions/set-view-mode! db)
                         (swap! db assoc :initialized? true)
                         (nav/hook-pop-state db)
                         (nav/replace-state-initial)
                         (js/window.scroll 0 0))))))

(defn render-app []
  #_(.AutoInit (.-M js/window))
  (dom/render [app] (.querySelector js/document "#app")))

(-> (configure-async!)
    (p/then (fn [] (render-app))))

;; endregion
