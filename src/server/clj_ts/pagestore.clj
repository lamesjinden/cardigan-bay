(ns clj-ts.pagestore
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io]
    [clojure.core.memoize :refer [memo memo-clear!]]
    [clj-ts.common :refer [raw-text->card-maps find-card-by-hash]])
  (:import (java.nio.file Paths)))

;; Diagnostic T
(defn P [x label] (do (println (str label " :: " x)) x))

;; Data structures / types

;; page-path, system-path, export-path are Java nio Paths
;; git-repo? is boolean

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
  (load-custom-file [ps file-name])
  (media-list [ps]))

(deftype PageStore [page-path system-path export-path git-repo?]
  IPageStore

  (as-map [_this]
    {:page-path   page-path
     :system-path system-path
     :export-path export-path
     :git-repo?   git-repo?})

  (page-name->path [_this page-name]
    (.resolve page-path (str page-name ".md")))

  (name->system-path [_this name]
    (.resolve system-path name))

  (page-exists? [this page-name]
    (-> (.page-name->path this page-name) .toFile .exists))

  (system-file-exists? [this name]
    (-> (.name->system-path this name) .toFile .exists))

  (last-modified [this page-name]
    (-> (.page-name->path this page-name) .toFile .lastModified (#(java.util.Date. %))))

  (load-page [this page]
    (if (instance? java.nio.file.Path page)
      (-> page .toFile slurp)
      (-> page (#(.page-name->path this %)) .toFile slurp)))

  (get-page-as-card-maps [this page-name]
    (->> page-name
         (.page-name->path this)
         (.load-page this)
         (raw-text->card-maps)))

  (get-card [this page-name hash]
    (-> (.get-page-as-card-maps this page-name)
        (find-card-by-hash hash)))

  (get-cards-from-page [this page-name card-hashes]
    (remove nil? (map #(.get-card this page-name %) card-hashes)))

  (write-page! [this page data]
    (if (instance? java.nio.file.Path page)
      (spit (.toString page) data)
      (let [x (-> page (#(.page-name->path this %)))]
        (spit (.toString x) data))))

  (read-system-file [this name]
    (if (instance? java.nio.file.Path name)
      (-> name .toFile slurp)
      (-> name (#(.name->system-path this %)) .toFile slurp)))

  (write-system-file! [this name data]
    (if (instance? java.nio.file.Path name)
      (spit (.toString name) data)
      (let [x (-> name (#(.name->system-path this %)))]
        (spit (.toString x) data))))

  (report [_this]
    (str "Page Directory :\t" (str page-path) "\n"
         "Is Git Repo? :\t\t" (str git-repo?) "\n"
         "System Directory :\t" (str system-path) "\n"
         "Export Directory :\t" (str export-path) "\n"))

  (similar-page-names [this page-name]
    (let [all-pages (.pages-as-new-directory-stream this)
          all-names (map #(-> (.getFileName %)
                              .toString
                              (string/split #"\.")
                              butlast
                              last)
                         all-pages)]
      (filter #(= (string/lower-case %) (string/lower-case page-name)) all-names)))

  (pages-as-new-directory-stream [_this]
    (java.nio.file.Files/newDirectoryStream page-path "*.md"))

  (media-files-as-new-directory-stream [_this]
    (let [media-path (.resolve page-path "media")]
      (java.nio.file.Files/newDirectoryStream media-path "*.*")))

  (media-export-path [_this]
    (.resolve export-path "media"))

  (read-recent-changes [this]
    (.read-system-file this "recentchanges"))

  (recent-changes-as-page-list [page-store]
    (->> (clojure.string/split-lines (.read-recent-changes page-store))
         (map (fn [line] (first (re-seq #"\[\[(.+?)\]\]" line))))
         (map second)))

  (write-recent-changes! [this recent-changes]
    (.write-system-file! this "recentchanges" recent-changes))

  (load-media-file [_this file-name]
    (let [media-dir (.toString (.resolve page-path "media"))]
      (io/file media-dir file-name)))

  (load-custom-file [_this file-name]
    (let [dir (.toString (.resolve page-path "system/custom"))]
      (io/file dir file-name)))

  (media-list [this]
    (let [files (.media-files-as-new-directory-stream this)]
      (map #(.getFileName %) files))))

;; Constructing

(defn make-page-store [page-dir-as-string export-dir-as-string]
  (let [page-dir-path (-> (Paths/get page-dir-as-string (make-array String 0))
                          (.toAbsolutePath)
                          (.normalize))
        system-dir-path (-> (Paths/get page-dir-as-string (into-array String ["system"]))
                            (.toAbsolutePath)
                            (.normalize))
        export-dir-path (-> (Paths/get export-dir-as-string (make-array String 0))
                            (.toAbsolutePath)
                            (.normalize))
        ;; note -- only verifies page-dir-path is a git root
        ;; todo -- check if within a git repo
        git-path (.resolve page-dir-path ".git")
        git-repo? (-> git-path .toFile .exists)
        page-store (->PageStore page-dir-path system-dir-path export-dir-path git-repo?)]

    (assert (-> page-dir-path .toFile .exists)
            (str "Given page-store directory " page-dir-as-string " does not exist."))
    (assert (-> page-dir-path .toFile .isDirectory)
            (str "page-store " page-dir-as-string " is not a directory."))
    (assert (-> system-dir-path .toFile .exists)
            (str "There is no system directory. Please make a directory called 'system' under the page directory "
                 page-dir-as-string))
    (assert (-> system-dir-path .toFile .isDirectory)
            (str "There is a file called 'system' under " page-dir-as-string
                 " but it is not a directory. Please remove that file and create a directory with that name"))
    (assert (-> export-dir-path .toFile .exists)
            (str "Given export-dir-path " export-dir-as-string " does not exist."))
    (assert (-> export-dir-path .toFile .isDirectory)
            (str "export-path " export-dir-as-string " is not a directory."))
    page-store))

;; Basic functions

(defn dedouble [s] (string/replace s #"\/\/" "/"))

(defn page-name->url [server-state page-name]
  (dedouble (str (-> server-state :site-url) "/view/" page-name)))

(defn path->pagename [path]
  (-> path .getFileName .toString (string/split #"\.") first))

;; RecentChanges
;; We store recent-changes in a system file called "recentchanges".

(defn update-recent-changes! [page-store page-name]
  (let [rcc (.read-recent-changes page-store)
        filter-step (fn [xs] (filter #(not (string/includes? % (str "[[" page-name "]]"))) xs))
        curlist (-> rcc string/split-lines filter-step)
        newlist (cons
                  (str "* [[" page-name "]] (" (.toString (java.util.Date.)) ")")
                  curlist)]
    (println "Updating recentchanges ... adding " page-name)
    (.write-recent-changes! page-store (string/join "\n" (take 80 newlist)))))

;; API for writing a file

(defn m-read-page [page-store page-name]
  (.load-page page-store page-name))

(def memoized-read-page (memo m-read-page))

(defn read-page [server-state page-name]
  (let [ps (:page-store server-state)]
    (memoized-read-page ps page-name)))

(defn write-page-to-file! [server-state page-name body]
  (let [page-store (.page-store server-state)]
    (.write-page! page-store page-name body)
    (update-recent-changes! page-store page-name)
    (memo-clear! memoized-read-page [page-store page-name])))

;; Search
;; Full Text Search
(defn text-search [server-state page-names pattern]
  (let [contains-pattern? (fn [page-name]
                            (let [text (read-page server-state page-name)]
                              (not (nil? (re-find pattern text)))))
        res (filter contains-pattern? page-names)]
    res))

;; Name Search - finds names containing substring
(defn name-search [page-names pattern]
  (filter #(not (nil? (re-find pattern %))) page-names))

;; Global Search and replace
;; Be careful with this.

(defn search-and-replace! [server-state page-names pattern new-string]
  (let [matched-pages (text-search server-state page-names pattern)]
    (doseq [page-name matched-pages]
      (let [text (read-page server-state page-name)
            new-text (string/replace text pattern new-string)]
        (write-page-to-file! server-state page-name new-text)))))
