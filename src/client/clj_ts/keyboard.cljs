(ns clj-ts.keyboard
  (:require [clojure.string :as str]
            [clj-ts.card :as cards]
            [clj-ts.navigation :as nav]
            [clj-ts.page :as page]))

(def key-enter-code 13)
(def key-escape-code 27)
(def key-s-code 83)

;; region global editor

(defn editor-on-escape-press [db]
  (nav/<reload-page! db))

(defn editor-on-key-s-press [db e]
  (.preventDefault e)
  (page/<save-page! db identity))

(defn editor-on-ctrl-shift-s-press [db e]
  (.preventDefault e)
  (page/<save-page! db))

(defn editor-on-key-down [db e]
  (when (= (-> @db :mode) :editing)
    (let [key-code (.-keyCode e)
          control? (.-ctrlKey e)
          shift? (.-shiftKey e)]
      (cond
        (and (= key-code key-s-code)
             control?
             shift?)
        (editor-on-ctrl-shift-s-press db e)

        (and (= key-code key-s-code)
             control?)
        (editor-on-key-s-press db e)))))

(defn editor-on-key-up [db e]
  ;; note - escape doesn't fire for key-press, only key-up
  (when (= (-> @db :mode) :editing)
    (let [key-code (.-keyCode e)]
      (cond
        (= key-code key-escape-code)
        (editor-on-escape-press db)))))

;; endregion

;; region nav input

(defn nav-input-on-key-enter [db e]
  (let [key-code (.-keyCode e)
        input-value (-> e .-target .-value str/trim)
        page-name input-value]
    (when (and (= key-code key-enter-code)
               (seq input-value))
      (nav/<navigate! db page-name))))

;; endregion

;; region single-editor

(defn single-editor-on-key-s-press [db local-db e]
  (.preventDefault e)
  (let [current-hash (-> @local-db :card (get "hash"))
        new-body (->> @local-db :editor (.getValue))]
    (cards/replace-card-async! db current-hash new-body)))

(defn single-editor-on-key-down [db local-db e]
  (let [key-code (.-keyCode e)
        control? (.-ctrlKey e)]
    (when (and (= key-code key-s-code)
               control?)
      (single-editor-on-key-s-press db local-db e))))

(defn single-editor-on-escape-press [local-db]
  (swap! local-db assoc :mode :viewing))

(defn single-editor-on-key-up [local-db e]
  ;; note - escape doesn't fire for key-press, only key-up
  (let [key-code (.-keyCode e)]
    (cond
      (= key-code key-escape-code)
      (single-editor-on-escape-press local-db))))

;; endregion

;; region workspace editor

(defn workspace-editor-on-key-s-press [db local-db e]
  (.preventDefault e)
  (let [current-hash (-> @local-db :hash)
        new-body (->> @local-db :editor (.getValue))]
    (cards/replace-card-async! db current-hash new-body)))

(defn workspace-editor-on-key-down [db local-db e]
  (let [key-code (.-keyCode e)
        control? (.-ctrlKey e)]
    (when (and (= key-code key-s-code)
               control?)
      (workspace-editor-on-key-s-press db local-db e))))

;; endregion