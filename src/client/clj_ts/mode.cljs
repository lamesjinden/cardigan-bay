(ns clj-ts.mode)

(defn set-edit-mode! [db]
  (swap! db assoc :mode :editing))

(defn set-view-mode! [db]
  (swap! db assoc :mode :viewing))

(defn set-transcript-mode! [db]
  (swap! db assoc :mode :transcript))

(defn editing? [db]
  (= :editing (:mode @db)))

(defn viewing? [db]
  (= :viewing (:mode @db)))