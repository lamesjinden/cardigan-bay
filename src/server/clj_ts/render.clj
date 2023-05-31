(ns clj-ts.render
  (:require [clojure.pprint :as pp]))

(defn raw-db [card-server-state]
  (str "<pre>" (with-out-str (pp/pprint (.raw-db card-server-state))) "</pre>"))