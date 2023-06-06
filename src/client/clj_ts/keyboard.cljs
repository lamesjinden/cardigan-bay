(ns clj-ts.keyboard
  (:require [clj-ts.navigation :as nav]
            [clj-ts.page :as page]))

(def key-escape-code 27)

(defn editor-on-escape-press [db]
  (nav/reload-async! db))

(def key-s-code 83)

(defn editor-on-key-s-press [db e]
  (.preventDefault e)
  (page/save-page-async! db identity))

(defn editor-on-ctrl-shift-s-press [db e]
  (.preventDefault e)
  (page/save-page-async! db))

(defn editor-on-key-press [db e]
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