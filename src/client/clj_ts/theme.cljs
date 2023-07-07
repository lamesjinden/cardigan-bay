(ns clj-ts.theme)

(defn viewing? [db-or-mode]
  (let [value @db-or-mode]
    (or (= :viewing value)
        (= :viewing (:mode value)))))

(defn light-theme? [db-or-theme]
  (or (= :light db-or-theme)
      (when (satisfies? IDeref db-or-theme)
        (or
          (= :light @db-or-theme)
          (= :light (:theme @db-or-theme))))))

(defn set-light-theme! [db]
  (swap! db assoc :theme :light))

(defn set-dark-theme! [db]
  (swap! db assoc :theme :dark))

