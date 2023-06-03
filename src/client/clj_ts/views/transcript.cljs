(ns clj-ts.views.transcript
  (:require [clj-ts.events.actions :refer [has-link-target? navigate-via-link-async!]]))

(defn transcript [db]
  [:div {:class                   "transcript"
         :dangerouslySetInnerHTML {:__html (-> @db :transcript)}
         :on-click                (fn [e] (when (has-link-target? e)
                                            (navigate-via-link-async! db e)))}])
