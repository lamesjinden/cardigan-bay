(ns clj-ts.client
  (:require
    [reagent.core :as r]
    [reagent.dom :as dom]
    [promesa.core :as p]
    [clj-ts.mode :as mode]
    [clj-ts.theme :as theme]
    [clj-ts.navigation :as nav]
    [clj-ts.views.app-header :refer [app-header]]
    [clj-ts.views.app-page-controls :refer [app-page-controls]]
    [clj-ts.views.app-main :refer [app-main]]))

;; region top-level ratom

(defonce db (r/atom
              {:current-page             "HelloWorld"
               :raw                      ""
               :transcript               ""
               :cards                    []
               :wiki-name                "Wiki Name"
               :site-url                 "Site URL"
               :initialized?             false
               :mode                     :viewing
               :card-list-expanded-state :expanded
               :theme                    (theme/get-initial-theme :light)
               :env-port                 4545}))

;; endregion

;; region top-level components

(defn app []
  (reagent.core/track! (partial theme/toggle-app-theme db))

  (let [rx-mode (r/cursor db [:mode])]
    [:div.app-container
     [app-header db]
     [app-page-controls db rx-mode]
     [app-main db]]))

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
                         (mode/set-view-mode! db)
                         (swap! db assoc :initialized? true)
                         (nav/hook-pop-state db)
                         (nav/replace-state-initial)
                         (js/window.scroll 0 0))))))

(defn render-app []
  (dom/render [app] (.querySelector js/document "#app")))

(-> (configure-async!)
    (p/then (fn [] (render-app))))

;; endregion
