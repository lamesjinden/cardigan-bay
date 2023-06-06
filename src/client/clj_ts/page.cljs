(ns clj-ts.page
  (:require [clj-ts.http :as http]
            [clj-ts.navigation :as nav]))

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
