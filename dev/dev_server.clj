(ns dev_server
  (:require
    [org.httpkit.server :as http]
    [ring.middleware.reload :as reload]
    [clj-ts.server :as cb]))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (println "stopping server")

    (@server :timeout 100)
    (reset! server nil)))

(defn create-server [& args]
  (println "creating server")

  (let [opts (cb/args->opts args)]
    (cb/init-app opts)

    (reset! server (http/run-server
                     (-> (cb/create-app)
                         (reload/wrap-reload))
                     {:port (:port opts)}))

    #_(let [app (cb/create-app)
            app (reload/wrap-reload app)
            shutdown-server (http/run-server app {:port (:port opts)})]
        (reset! server shutdown-server))
    ))

(defn -main [& args]
  (apply create-server args))

(comment

  (create-server "--directory" "../../Documents/wiki/bedrock/")

  (stop-server)

  )

