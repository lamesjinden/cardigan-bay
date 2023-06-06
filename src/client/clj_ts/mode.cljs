(ns clj-ts.mode)

(defn set-edit-mode! [db]
  (swap! db assoc :mode :editing))

(defn set-view-mode! [db]
  (swap! db assoc :mode :viewing))