(ns clj-ts.views.paste-bar
  (:require [clj-ts.common :as common]
            [clj-ts.handle :as handle]))

(def system-search-template "
----
:system

{:command :search
 :query \"\"
}

----")

(def workspace-template "
----
:workspace

;; Write some code
[:div
(str \"Hello Teenage America\")
]

----")

(def evalmd-template "
----
:evalmd

;; Write some code.
;; Note that if the result of your executed code is a number
;; You must convert it to a string.

(str \"### \" (+ 1 2 3))

")

(defn paste-bar [db]
  [:div {:class "pastebar"}
   [:div
    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :markdown)))}
     "New Card"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db system-search-template))}
     "Search Card"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db workspace-template))}
     "Code Workspace"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db evalmd-template))}
     "Code on Server"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :youtube)))}
     "YouTube Card"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :vimeo)))}
     "Vimeo Card"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :img)))}
     "Image Card"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :soundcloud)))}
     "SoundCloud Card"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :bandcamp)))}
     "BandCamp Card"]


    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :twitter)))}
     "Twitter Card"]

    [:button
     {:class    "big-btn"
      :on-click (fn [] (handle/insert-text-at-cursor! db (common/embed-boilerplate :rss)))}
     "RSS Feed"]]])
