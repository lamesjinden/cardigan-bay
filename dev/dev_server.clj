(ns dev_server
  (:require
    [org.httpkit.server :as http]
    [ring.middleware.reload :as reload]
    [ring.middleware.cors :as cors]
    [clj-ts.server :as cb]))

(set! *warn-on-reflection* true)

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (println "stopping server")

    (@server :timeout 100)
    (reset! server nil)))

(defn create-server [& args]
  (println "creating server")

  (let [settings (cb/gather-settings args)]
    (println "Init-app with settings" settings)

    (let [card-server-ref (cb/init-app settings)]
      (reset! server (http/run-server
                       (-> (cb/create-app card-server-ref)
                           (reload/wrap-reload)
                           (cors/wrap-cors :access-control-allow-origin [#".*"]
                                           :access-control-allow-methods [:get :put :post :delete]))
                       {:port (:port settings)})))))

(defn -main [& args]
  (apply create-server args))

(comment

  (create-server "--directory" "../../Documents/wiki/bedrock/")

  (stop-server)

  )

