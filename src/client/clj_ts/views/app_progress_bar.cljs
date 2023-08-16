(ns clj-ts.views.app-progress-bar
  (:require [cljs.core.async :as a]))

(defn app-progress-bar []
  [:span.progress-bar-outer
   [:span.progress-bar-inner]])
