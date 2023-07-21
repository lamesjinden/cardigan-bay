(ns clj-ts.views.transcript
  (:require [clj-ts.card :refer [has-link-target?]]
            [clj-ts.navigation :as nav]))

(defn navigate-via-link-async! [db e]
  (let [tag (-> e .-target)
        data (.getAttribute tag "data")]
    (nav/<navigate! db data)))

(defn transcript [db db-transcript]
  [:div {:class                   "transcript"
         :dangerouslySetInnerHTML {:__html @db-transcript}
         :on-click                (fn [e]
                                    (.preventDefault e)
                                    (when (has-link-target? e)
                                      (navigate-via-link-async! db e)))}])
