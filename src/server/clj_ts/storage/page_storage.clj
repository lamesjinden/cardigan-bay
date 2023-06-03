(ns clj-ts.storage.page-storage)

(defprotocol IPageStore
  (as-map [ps])
  (page-name->path [ps page-name])
  (name->system-path [ps name])
  (page-exists? [ps page-name])
  (system-file-exists? [ps name])
  (last-modified [ps page-name])
  ;; note - renamed read-page to load-page to avoid collision with pagestore/read-page
  (load-page [ps page])
  (get-page-as-card-maps [ps page-name])
  (get-card [ps page-name card-hash])
  (get-cards-from-page [ps page-name card-hashes])
  (write-page! [ps page data])
  (read-system-file [ps name])
  (write-system-file! [ps name data])
  (report [ps])
  (similar-page-names [ps p-name])
  (pages-as-new-directory-stream [ps])
  (media-files-as-new-directory-stream [ps])
  (media-export-path [ps])
  (read-recent-changes [ps])
  (recent-changes-as-page-list [ps])
  (write-recent-changes! [ps new-rc])
  (load-media-file [ps file-name])
  (media-list [ps]))