(ns clj-ts.views.app-main
  (:require [clj-ts.views.editor :refer [editor]]
            [clj-ts.views.card-list :refer [card-list]]
            [clj-ts.views.transcript :refer [transcript]]
            [clj-ts.networks :refer [network-canvas]]))

(defn app-main [db]
  (let [mode (:mode @db)]
    [:main
     [:div
      (condp = mode

        :editing
        [editor db]

        :viewing
        [card-list db]

        :transcript
        [transcript db]

        :network-editor
        [network-canvas])]]))