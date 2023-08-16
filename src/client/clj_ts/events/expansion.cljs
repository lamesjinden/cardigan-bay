(ns clj-ts.events.expansion
  (:require [cljs.core.async :as a]))

(def ^:private expansion$ (a/chan))
(def ^:private expansion-mult$ (a/mult expansion$))

(defn notify-expansion [expanded-or-collapsed]
  (a/put! expansion$ expanded-or-collapsed))

(defn create-expansion$
  ([to-chan]
   (a/tap expansion-mult$ to-chan)
   to-chan)
  ([] (create-expansion$ (a/chan))))