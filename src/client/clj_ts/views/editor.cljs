(ns clj-ts.views.editor
  (:require [reagent.core]
            [clj-ts.handle :as handle]
            [clj-ts.views.paste-bar :refer [paste-bar]]))

(defn editor [db]
  (reagent.core/create-class
    {:component-did-mount    (fn [] (handle/setup-editor db))
     :component-will-unmount (fn [] (handle/destroy-editor db))
     :reagent-render         (fn [] [:div
                                     [paste-bar db]
                                     [:div
                                      {:class       ["edit-box"]
                                       :on-key-down (fn [e] (handle/editor-on-key-press db e))
                                       :on-key-up   (fn [e] (handle/editor-on-key-up db e))}
                                      (:raw @db)]])}))