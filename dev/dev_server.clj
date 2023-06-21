(ns dev-server
  (:require
    [clojure.pprint]
    [org.httpkit.server :as http]
    [ring.middleware.reload :as reload]
    [ring.middleware.cors :as cors]
    [clj-ts.server :as server]))

(set! *warn-on-reflection* true)

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (println "stopping server")

    (@server :timeout 100)
    (reset! server nil)))

(defn create-server [& args]
  (println "\ncreating dev server:")
  (clojure.pprint/pprint args)

  (let [settings (server/gather-settings args)]
    (println "\ninitialize dev server app:")
    (clojure.pprint/pprint settings)

    (let [card-server-ref (server/initialize-state settings)]
      (reset! server (http/run-server
                       (-> (server/create-app card-server-ref)
                           (reload/wrap-reload)
                           (cors/wrap-cors :access-control-allow-origin [#".*"]
                                           :access-control-allow-methods [:get :put :post :delete]))
                       {:port (:port settings)})))))

(defn -main [& args]
  (apply create-server args))

(comment

  (create-server
    "--directory" "../../Documents/wiki/bedrock/"
    "--export-dir" "../../Documents/wiki/bedrock/exported/")

  (stop-server)

  )

