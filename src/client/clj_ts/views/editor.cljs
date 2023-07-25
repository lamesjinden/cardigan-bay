(ns clj-ts.views.editor
  (:require [clojure.core.async :as a]
            [reagent.core :as r]
            [clj-ts.ace :as ace]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.navigation :as nav]
            [clj-ts.page :as page]
            [clj-ts.theme :as theme]
            [clj-ts.views.paste-bar :refer [paste-bar]]))

(def ^:private editor-id "global")

(defn- editor-on-key-s-press [db e]
  (.preventDefault e)
  (page/<save-page! db identity))

(defn- editor-on-ctrl-shift-s-press [db e]
  (.preventDefault e)
  (page/<save-page! db))

(defn editor-on-key-down [db e]
  (when (= (-> @db :mode) :editing)
    (let [key-code (.-keyCode e)
          control? (.-ctrlKey e)
          shift? (.-shiftKey e)]
      (cond
        (and (= key-code keyboard/key-s-code)
             control?
             shift?)
        (editor-on-ctrl-shift-s-press db e)

        (and (= key-code keyboard/key-s-code)
             control?)
        (editor-on-key-s-press db e)))))

(defn- editor-on-escape-press [db]
  (a/go
    (when-let [response (a/<! (nav/notify-editing-ending editor-id))]
      (when (= response :ok)
        (nav/<reload-page! db)))))

(defn editor-on-key-up [db e]
  ;; note - escape doesn't fire for key-press, only key-up
  (when (= (-> @db :mode) :editing)
    (let [key-code (.-keyCode e)]
      (cond
        (= key-code keyboard/key-escape-code)
        (editor-on-escape-press db)))))

(defn setup-editor [db !editor-element]
  (let [editor-element @!editor-element
        ace-instance (.edit js/ace editor-element)
        ace-options (assoc ace/default-ace-options :maxLines "Infinity")
        theme (if (theme/light-theme? db) ace/ace-theme ace/ace-theme-dark)]
    (ace/configure-ace-instance! ace-instance ace/ace-mode-markdown theme ace-options)
    (.focus ace-instance)
    (swap! db assoc :editor ace-instance)
    (let [!notify (atom false)]
      (.on ace-instance "change" (fn [_delta]
                                   (when-not @!notify
                                     (reset! !notify true)
                                     (nav/notify-editing-begin editor-id)))))))

(defn destroy-editor [db]
  (let [editor (:editor @db)]
    (when editor
      (.destroy editor))))

(defn editor [db db-raw]
  (let [!editor-element (clojure.core/atom nil)
        tracking (reagent.core/track! (fn []
                                        (if (theme/light-theme? db)
                                          (ace/set-theme! (:editor @db) ace/ace-theme)
                                          (ace/set-theme! (:editor @db) ace/ace-theme-dark))))]
    (reagent.core/create-class
      {:component-did-mount    (fn []
                                 (setup-editor db !editor-element))
       :component-will-unmount (fn []
                                 (destroy-editor db)
                                 (r/dispose! tracking)
                                 (nav/notify-editing-end editor-id))
       :reagent-render         (fn [] [:div.edit-box-container
                                       [paste-bar db]
                                       [:div.edit-box
                                        {:ref         (fn [element] (reset! !editor-element element))
                                         :on-key-down (fn [e] (editor-on-key-down db e))
                                         :on-key-up   (fn [e] (editor-on-key-up db e))}
                                        @db-raw]])})))