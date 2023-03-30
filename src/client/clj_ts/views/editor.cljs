(ns clj-ts.views.editor
  (:require [reagent.core]
            [clj-ts.handle :as handle]))

(defn editor [db]
  (reagent.core/create-class
    {:component-did-mount    (fn [] (handle/setup-editor db))
     :component-will-unmount (fn [] (handle/destroy-editor db))
     :reagent-render         (fn [] [:div
                                     {:class     ["edit-box"]
                                      :on-key-up (fn [e] (handle/exit-edit-mode-on-escape-press e db))}
                                     (:raw @db)])}))