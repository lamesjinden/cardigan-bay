(ns clj-ts.page
  (:require [clj-ts.http :as http]
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
  ([db http-callback]
   (let [page-name (-> @db :current-page)
         ace-instance (:ace-instance @db)
         new-data (.getValue ace-instance)]
     (http/http-post-async
       "/api/save"
       http-callback
       (pr-str {:page page-name
                :data new-data}))))
  ([db]
   (let [http-callback (fn [e]
                         (let [load-page-response (-> e .-target .getResponseJson)]
                           (if (nil? load-page-response)
                             (nav/reload-async! db)
                             (nav/load-page db load-page-response))))]
     (save-page-async! db http-callback))))

(defn save-card-async!
  ([page-name hash new-val http-callback]
   (let [body (pr-str {:page page-name
                       :data new-val
                       :hash hash})]
     (http/http-post-async "/api/replacecard" http-callback body)))
  ([page-name hash new-val]
   (let [http-callback (fn [e]
                         (let [replace-card-response (-> e .-target .getResponseJson)]
                           replace-card-response))]
     (save-card-async! page-name hash new-val http-callback))))
