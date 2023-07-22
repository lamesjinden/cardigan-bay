(ns clj-ts.client
  (:require
    [cljs.core.async :as a]
    [reagent.core :as r]
    [reagent.dom :as dom]
    [clj-ts.mode :as mode]
    [clj-ts.theme :as theme]
    [clj-ts.navigation :as nav]
    [clj-ts.views.confirmation-dialog :refer [confirmation-dialog]]
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
     [confirmation-dialog nav/confirmation-request$ nav/confirmation-response$]
     [app-header db]
     [app-page-controls db rx-mode]
     [app-main db]]))

;; endregion

;; region page load

; request and load the start-page

(defn render-app []
  (dom/render [app] (.querySelector js/document "#app")))

(let [render$ (cond
                (:init-dispatch @db)
                (doto (a/promise-chan) (a/put! 0))

                :else (let [init-config (first (.-init js/window))
                            init-body$ (if (object? init-config)
                                         (doto (a/promise-chan) (a/put! init-config))
                                         (nav/<get-init))]
                        (a/go
                          (let [init (a/<! init-body$)]
                            (nav/load-page! db init)
                            (mode/set-view-mode! db)
                            (swap! db assoc :initialized? true)
                            (nav/hook-pop-state db)
                            (nav/replace-state-initial)
                            (js/window.scroll 0 0)))))]
  (a/go
    (let [_ (a/<! render$)]
      (render-app))))

;; endregion