(ns clj-ts.confirmation.edit-process
  (:require [cljs.core.async :as a]
            [clj-ts.events.confirmation :as e-confirm]))

(defn <create-editor-process [editing$]
  (a/go-loop [editing #{}]
             (let [value (a/<! editing$)
                   {:keys [id action]} value]
               (condp = action
                 :start (recur (conj editing id))
                 :ending (let [out-chan (:out-chan value)]
                           (if (contains? editing id)
                             (let [confirm$ (e-confirm/<notify-confirm)
                                   response (a/<! confirm$)]
                               (a/>! out-chan response)
                               (if (= response :ok)
                                 (recur (disj editing id))
                                 (recur editing)))
                             (do
                               (a/>! out-chan :ok)
                               (recur editing))))
                 :end (recur (disj editing id))
                 :default (throw (ex-info "unknown action" value))))))