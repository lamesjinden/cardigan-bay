(require
  '[figwheel.main :as figwheel])

(defn start-figwheel [args]
  (apply figwheel/-main args))

(defn -main [& args]
  (start-figwheel args))

(comment

  (js/alert "Am I connected?")

  (start-figwheel ["--build" "dev/dev-client" "-r"])

  (figwheel.main/status)

  )