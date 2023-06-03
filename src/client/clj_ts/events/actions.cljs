(ns clj-ts.events.actions
  (:require
    [promesa.core :as p]
    [clj-ts.http :as http]
    [clj-ts.events.navigation :as nav]))

(defn cancel-async! [db]
  (nav/reload-async! db))

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
   (let [http-callback (fn [e] (let [load-page-response (-> e .-target .getResponseJson)]
                                 (if (nil? load-page-response)
                                   (nav/reload-async! db)
                                   (nav/load-page db load-page-response))))]
     (save-page-async! db http-callback))))

(defn save-card-async! [page-name hash new-val]
  (let [body (pr-str {:page page-name
                      :data new-val
                      :hash hash})]
    (http/http-post-async "/api/replacecard" identity body)))

(defn has-link-target? [e]
  (let [tag (-> e .-target)
        class (.getAttribute tag "class")]
    (= class "wikilink")))

(defn navigate-via-link-async! [db e]
  (let [tag (-> e .-target)
        data (.getAttribute tag "data")]
    (-> (nav/go-new-async! db data)
        (p/then (fn [] (clj-ts.events.navigation/navigate-to data))))))

(defn set-edit-mode! [db]
  (swap! db assoc :mode :editing))

(defn set-view-mode! [db]
  (swap! db assoc :mode :viewing))
