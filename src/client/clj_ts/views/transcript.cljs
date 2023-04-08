(ns clj-ts.views.transcript
  (:require [clj-ts.handle :as handle]))

(defn transcript [db]
  [:div {:class                   "transcript"
         :dangerouslySetInnerHTML {:__html (-> @db :transcript)}
         :on-click                (fn [e] (handle/on-click-for-links-async! db e))}])