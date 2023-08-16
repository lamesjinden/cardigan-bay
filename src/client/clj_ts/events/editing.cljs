(ns clj-ts.events.editing
  (:require [cljs.core.async :as a]))

"
Contains editing event channels as well as functions for publishing editing events.
"

(def ^:private editing$ (a/chan))
(def ^:private editing-mult$ (a/mult editing$))

(defn notify-editing-begin [id]
  (a/put! editing$ {:id     id
                    :action :start}))

(defn <notify-editing-ending [id]
  (let [out-chan (a/promise-chan)]
    (a/put! editing$ {:id       id
                      :action   :ending
                      :out-chan out-chan})
    out-chan))

(defn notify-editing-end [id]
  (a/put! editing$ {:id     id
                    :action :end}))

(defn create-editing$
  ([to-chan]
   (a/tap editing-mult$ to-chan)
   to-chan)
  ([] (create-editing$ (a/chan))))