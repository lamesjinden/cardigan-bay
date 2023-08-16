(ns clj-ts.views.app
  (:require
    [reagent.core :as r]
    [clj-ts.theme :as theme]
    [clj-ts.views.confirmation-dialog :refer [confirmation-dialog]]
    [clj-ts.views.app-header :refer [app-header]]
    [clj-ts.views.app-page-controls :refer [app-page-controls]]
    [clj-ts.views.app-main :refer [app-main]]))

(defn app [db confirmation-request$]
  (reagent.core/track! (partial theme/toggle-app-theme db))

  (let [rx-mode (r/cursor db [:mode])]
    [:<>
     [confirmation-dialog confirmation-request$]
     [app-header db]
     [app-page-controls db rx-mode]
     [app-main db]]))
