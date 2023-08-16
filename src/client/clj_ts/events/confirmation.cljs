(ns clj-ts.events.confirmation
  (:require [cljs.core.async :as a]))

(def ^:private confirmation-request$ (a/chan))

(defn <notify-confirm []
  (let [out-chan (a/promise-chan)]
    (a/put! confirmation-request$ {:action   :confirmation-requested
                                  :out-chan out-chan})
    out-chan))

(defn create-confirmation-request$ []
  confirmation-request$)
