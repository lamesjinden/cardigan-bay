(ns dev_client
  (:require [clojure.tools.nrepl.server :as nrepl-server]
            [clojure.java.io :as io]))

(def nrepl-port 7888)
(defonce nrepl-server (atom nil))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn start-nrepl-server! []
  (reset!
    nrepl-server
    (nrepl-server/start-server :port nrepl-port
                               :handler (nrepl-handler)))
  (println "Cider nREPL server started on port" nrepl-port)
  (spit ".nrepl-port" nrepl-port))

(defn stop-nrepl-server! []
  (when (not (nil? @nrepl-server))
    (nrepl-server/stop-server @nrepl-server)
    (println "Cider nREPL server on port" nrepl-port "stopped")
    (reset! nrepl-server nil)
    (io/delete-file ".nrepl-port" true)))

(comment
  ;; for nrepl-based development, require this file from a rebel repl
  ;; $> (require '[dev_client :refer :all])
  ;; $> (start-nrepl-server!)
  ;;
  ;; connect to the nrepl session via cursive
  ;; next, start figwheel by eval'ing the following
  (require 'figwheel.main.api)
  (figwheel.main.api/start {:mode :serve} "dev/dev_client")
  ;; enter cljs mode
  (figwheel.main.api/cljs-repl "dev/dev_client")
  ;; load the webapp on figwheel's port (9500)
  ;; begin eval'ing cljs code

  (+ 1 1)
  (println "hi")
  (js/alert "hi")

  )