(ns clj-ts.page
  (:require [promesa.core :as p]
            [clj-ts.http :as http]
            [clj-ts.navigation :as nav]))

(defn enter-view-mode! [db]
  (swap! db assoc :mode :viewing))

(defn enter-edit-mode! [db]
  (swap! db assoc :mode :editing))

(defn enter-transcript-mode! [db]
  (swap! db assoc :mode :transcript))

(defn cancel-async! [db]
  (enter-view-mode! db))

(defn save-page-async!
  [db]
  (let [page-name (-> @db :current-page)
        ace-instance (:ace-instance @db)
        new-data (.getValue ace-instance)
        body (pr-str {:page page-name
                      :data new-data})
        callback (fn [{body-text :body}]
                   (if (nil? body)
                     (nav/reload-async! db)
                     (let [body (js/JSON.parse body-text)]
                       (nav/load-page db body))))]
    (-> (http/http-post-async "/api/save" body)
        (p/then callback))))

(defn save-card-async!
  [page-name hash new-val]
  (let [body (pr-str {:page page-name
                      :data new-val
                      :hash hash})
        callback (fn [{body-text :body}]
                   (js/JSON.parse body-text))]
    (-> (http/http-post-async "/api/replacecard" body)
        (p/then callback))))
