(ns clj-ts.views.transcript
  (:require [clj-ts.card :refer [has-link-target? navigate-via-link-async!]]))

(defn transcript [db db-transcript]
  [:div {:class                   "transcript"
         :dangerouslySetInnerHTML {:__html @db-transcript}
         :on-click                (fn [e] (when (has-link-target? e)
                                            (navigate-via-link-async! db e)))}])
