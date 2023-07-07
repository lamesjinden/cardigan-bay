(ns clj-ts.views.editor
  (:require [reagent.core :as r]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.views.paste-bar :refer [paste-bar]]
            [clj-ts.ace :as ace]
            [clj-ts.theme :as theme]))

(defn setup-editor [db]
  (let [editor-element (first (array-seq (.getElementsByClassName js/document "edit-box")))
        ace-instance (.edit js/ace editor-element)
        ace-options (assoc ace/default-ace-options :maxLines "Infinity")
        theme (if (theme/light-theme? db) ace/ace-theme ace/ace-theme-dark)]
    (ace/configure-ace-instance! ace-instance ace/ace-mode-markdown theme ace-options)
    (.focus ace-instance)
    (swap! db assoc :ace-instance ace-instance)))

(defn destroy-editor [db]
  (let [editor (:ace-instance @db)]
    (when editor
      (.destroy editor))))

(defn editor [db db-raw]
  (let [tracking (reagent.core/track! (fn []
                                        (if (theme/light-theme? db)
                                          (ace/set-theme! (:ace-instance @db) ace/ace-theme)
                                          (ace/set-theme! (:ace-instance @db) ace/ace-theme-dark))))]
    (reagent.core/create-class
      {:component-did-mount    (fn [] (setup-editor db))
       :component-will-unmount (fn []
                                 (destroy-editor db)
                                 (r/dispose! tracking))
       :reagent-render         (fn [] [:div.edit-box-container
                                       [paste-bar db]
                                       [:div.edit-box
                                        {:on-key-down (fn [e] (keyboard/editor-on-key-down db e))
                                         :on-key-up   (fn [e] (keyboard/editor-on-key-up db e))}
                                        @db-raw]])})))