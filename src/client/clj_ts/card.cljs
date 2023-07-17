(ns clj-ts.card
  (:require [promesa.core :as p]
            [clj-ts.navigation :as nav]
            [clj-ts.page :as page]))

(defn has-link-target? [e]
  (let [tag (.-target e)
        class (.getAttribute tag "class")]
    (= class "wikilink")))

(defn wikilink-data [e]
  (when (has-link-target? e)
    (let [tag (.-target e)
          data (.getAttribute tag "data")]
      data)))

(defn navigate-via-link-async! [db e]
  (let [tag (-> e .-target)
        data (.getAttribute tag "data")]
    (nav/navigate-async! db data)))

(defn- replace-card [snapshot replaced-hash new-card raw]
  (let [matching-index (->> (:cards snapshot)
                            (map-indexed (fn [i x] [i x]))
                            (filter (fn [[_i x]]
                                      (= (get x "hash") replaced-hash)))
                            (ffirst))]
    (-> snapshot
        (assoc :raw raw)
        (update-in [:cards matching-index] merge new-card))))

(defn replace-card-async! [db current-hash new-card-body]
  (let [page-name (:current-page @db)]
    (-> (page/save-card-async!
          page-name
          current-hash
          new-card-body)
        (p/then (fn [json]
                  (let [edn (js->clj json)
                        replaced-hash (get edn "replaced-hash")
                        new-card (get edn "new-card")
                        raw (get-in edn ["source_page" "body"])]
                    (swap! db replace-card replaced-hash new-card raw)))))))