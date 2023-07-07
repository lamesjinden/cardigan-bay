(ns clj-ts.ace
  (:require [cljsjs.ace]))

(def default-ace-options {:fontSize                 "1.2rem"
                          :minLines                 5
                          :autoScrollEditorIntoView true})

(def ace-theme "ace/theme/cloud9_day")
(def ace-theme-dark "ace/theme/cloud9_night")
(def ace-mode-clojure "ace/mode/clojure")
(def ace-mode-markdown "ace/mode/markdown")

(defn configure-ace-instance!
  ([ace-instance mode]
   (configure-ace-instance! ace-instance mode ace-theme default-ace-options))
  ([ace-instance mode theme options]
   (let [ace-session (.getSession ace-instance)]
     (.setTheme ace-instance theme)
     (.setOptions ace-instance (clj->js options))
     (.setShowInvisibles ace-instance false)
     (.setMode ace-session mode))))

(defn set-theme! [ace-instance theme]
  (when ace-instance
    (.setTheme ace-instance theme)))