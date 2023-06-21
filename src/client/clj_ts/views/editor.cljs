(ns clj-ts.views.editor
  (:require [reagent.core]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.views.paste-bar :refer [paste-bar]]
            [clj-ts.ace :as ace]))

(defn setup-editor [db]
  (let [editor-element (first (array-seq (.getElementsByClassName js/document "edit-box")))
        ace-instance (.edit js/ace editor-element)
        ace-options (assoc ace/default-ace-options :maxLines "Infinity")]
    (ace/configure-ace-instance! ace-instance ace/ace-mode-markdown ace-options)
    (.focus ace-instance)
    (swap! db assoc :ace-instance ace-instance)))

(defn destroy-editor [db]
  (let [editor (:editor @db)]
    (when editor
      (.destroy editor))))

(defn editor [db]
  (reagent.core/create-class
    {:component-did-mount    (fn [] (setup-editor db))
     :component-will-unmount (fn [] (destroy-editor db))
     :reagent-render         (fn [] [:div
                                     [paste-bar db]
                                     [:div.edit-box
                                      {:on-key-down (fn [e] (keyboard/editor-on-key-down db e))
                                       :on-key-up   (fn [e] (keyboard/editor-on-key-up db e))}
                                      (:raw @db)]])}))