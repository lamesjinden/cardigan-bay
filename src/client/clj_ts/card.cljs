(ns clj-ts.card
  (:require [clj-ts.navigation :as nav]
            [promesa.core :as p]))

(defn has-link-target? [e]
  (let [tag (-> e .-target)
        class (.getAttribute tag "class")]
    (= class "wikilink")))

(defn navigate-via-link-async! [db e]
  (let [tag (-> e .-target)
        data (.getAttribute tag "data")]
    (-> (nav/go-new-async! db data)
        (p/then (fn [] (nav/navigate-to data))))))