(ns clj-ts.events.navigation
  (:require [cljs.core.async :as a]))

(def ^:private navigating$ (a/chan))
(def ^:private navigating-mult$ (a/mult navigating$))

(defn <notify-navigating [page-name]
  (let [out-chan (a/promise-chan)]
    (a/put! navigating$ {:page-name page-name
                         :out-chan  out-chan})
    out-chan))

(defn create-navigating$
  ([to-chan]
   (a/tap navigating-mult$ to-chan)
   to-chan)
  ([] (create-navigating$ (a/chan))))