(require
  '[clj-ts.server :refer [-main]])

(let [server (-main)
      port (-> server meta :local-port)
      url (str "http://localhost:" port "/index.html")]
  (println "Started app on" url))