(ns clj-ts.page
  (:require [cljs.core.async :as a]
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
  ([db callback]
   (let [page-name (-> @db :current-page)
         editor (:editor @db)
         new-data (.getValue editor)
         body (pr-str {:page page-name
                       :data new-data})]
     (a/go
       (when-let [result (a/<! (http/<http-post "/api/save" body))]
         (callback result)))))
  ([db]
   (let [callback (fn [{body-text :body}]
                    (if (nil? body-text)
                      (nav/<reload-page db)
                      (let [body (js/JSON.parse body-text)]
                        (nav/load-page db body))))]
     (save-page-async! db callback))))

(defn save-card-async!
  [page-name hash new-val]
  (let [body (pr-str {:page page-name
                      :data new-val
                      :hash hash})
        callback (fn [{body-text :body}]
                   (js/JSON.parse body-text))]
    (a/go
      (when-let [result (a/<! (http/<http-post "/api/replacecard" body))]
        (let [{body-text :body} result]
          (js/JSON.parse body-text))))))
