(ns clj-ts.views.bookmarklet-footer)

(defn bookmarklet-footer-link [db]
  (let [port (:port @db)
        document-url js/document.URL
        url (str "http://localhost:" port "/api/bookmarklet?url=" document-url)]
    [:a {:href url} "Bookmark to this Wiki"]))
