(ns clj-ts.views.editor-single
  (:require [clojure.core.async :as a]
            [reagent.core :as r]
            [clj-ts.ace :as ace]
            [clj-ts.card :as cards]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.navigation :as nav]
            [clj-ts.theme :as theme]
            [clj-ts.views.paste-bar :refer [paste-bar]]))

(defn- single-editor-on-key-s-press [db local-db e]
  (.preventDefault e)
  (let [current-hash (:hash @local-db)
        new-body (->> @local-db :editor (.getValue))]
    (cards/replace-card-async! db current-hash new-body)))

(defn- single-editor-on-key-down [db local-db e]
  (let [key-code (.-keyCode e)
        control? (.-ctrlKey e)]
    (when (and (= key-code keyboard/key-s-code)
               control?)
      (single-editor-on-key-s-press db local-db e))))

(defn- single-editor-on-escape-press [local-db]
  (let [id (:hash @local-db)]
    (a/go
      (when-let [response (a/<! (nav/notify-editing-ending id))]
        (when (= response :ok)
          (swap! local-db assoc :mode :viewing))))))

(defn- single-editor-on-key-up [local-db e]
  ;; note - escape doesn't fire for key-press, only key-up
  (let [key-code (.-keyCode e)]
    (cond
      (= key-code keyboard/key-escape-code)
      (single-editor-on-escape-press local-db))))

(defn- setup-editor [db local-db !editor-element]
  (let [editor-element @!editor-element
        ace-instance (.edit js/ace editor-element)
        ace-options (assoc ace/default-ace-options :maxLines "Infinity")
        theme (if (theme/light-theme? db) ace/ace-theme ace/ace-theme-dark)]
    (ace/configure-ace-instance! ace-instance ace/ace-mode-markdown theme ace-options)
    (.focus ace-instance)
    (swap! local-db assoc :editor ace-instance)
    (let [!notify (atom false)
          id (:hash @local-db)]
      (.on ace-instance "change" (fn [_delta]
                                   (when-not @!notify
                                     (reset! !notify true)
                                     (nav/notify-editing-begin id)))))
    ;; note - tracking over 2 ratoms causes the subscribe function to be invoked continuously;
    ;;        to compensate, tracking declaration closes over the editor instance instead of deref'ing it.
    (let [tracking (r/track! (fn []
                               (if (theme/light-theme? db)
                                 (ace/set-theme! ace-instance ace/ace-theme)
                                 (ace/set-theme! ace-instance ace/ace-theme-dark))))]
      (swap! local-db assoc :tracking tracking))))

(defn- destroy-editor [local-db]
  (let [editor (:editor @local-db)]
    (when editor
      (.destroy editor))))

(defn single-editor [db local-db !editor-element]
  (r/create-class
    {:component-did-mount    (fn []
                               (setup-editor db local-db !editor-element))
     :component-will-unmount (fn []
                               (destroy-editor local-db)
                               (when-let [tracking (:tracking @local-db)]
                                 (r/dispose! tracking))
                               (nav/notify-editing-end (:hash @local-db)))
     :reagent-render         (fn []
                               [:<>
                                [paste-bar db local-db]
                                [:div.edit-box-single {:ref         (fn [element] (reset! !editor-element element))
                                                       :on-key-down (fn [e] (single-editor-on-key-down db local-db e))
                                                       :on-key-up   (fn [e] (single-editor-on-key-up local-db e))}
                                 (get (:card @local-db) "source_data")]])}))
