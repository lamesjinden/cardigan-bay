(ns clj-ts.view
  (:require [clojure.string :as str]
            [markdown.core :as md]
            [clj-ts.common :refer [raw-card-text->raw-card-map
                                   double-comma-table
                                   double-bracket-links
                                   auto-links]]))

(defn string->html [s]
  (-> s
      (double-comma-table)
      (md/md->html)
      (auto-links)
      (double-bracket-links)))

(defn card->html [card]
  (-> (get card "server_prepared_data")
      (string->html)))

(defn not-blank? [card]
  (not= "" (str/trim (get card "source_data"))))