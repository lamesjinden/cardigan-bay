(ns clj-ts.cards.system
  (:require [clj-ts.common :as common]
            [clj-ts.search :as search]
            [clojure.edn :as edn]))

(defn- ldb-query->mdlist-card [i source_data title result _qname f render-context]
  (let [items (apply str (map f result))
        body (str "*" title "* " "*(" (count result) " items)*\n\n" items)]
    (common/package-card i :system :markdown source_data body render-context)))

(defn- item1 [s] (str "* [[" s "]]\n"))

(defn system-card
  [server-snapshot i data render-context]
  (let [info (edn/read-string data)
        cmd (:command info)
        db (-> server-snapshot :facts-db)
        ps (-> server-snapshot :page-store)]

    (condp = cmd
      :allpages
      (ldb-query->mdlist-card i data "All Pages" (.all-pages db) :allpages item1 render-context)

      :alllinks
      (ldb-query->mdlist-card
        i data "All Links" (.all-links db) :alllinks
        (fn [[a b]] (str "[[" a "]],, &#8594;,, [[" b "]]\n"))
        render-context)

      :brokenlinks
      (ldb-query->mdlist-card
        i data "Broken Internal Links" (.broken-links db) :brokenlinks
        (fn [[a b]] (str "[[" a "]],, &#8603;,, [[" b "]]\n"))
        render-context)

      :orphanpages
      (ldb-query->mdlist-card
        i data "Orphan Pages" (.orphan-pages db) :orphanpages item1
        render-context)

      :recentchanges
      (let [src (.read-recent-changes ps)]
        (common/package-card
          "recentchanges" :system :markdown src src render-context))

      :search
      ;; note/todo - if this path is used, then the first arg needs to be made case-insensitive (see resolve-text-search below)
      (let [res (search/search server-snapshot (:query info) (:query info))]
        (common/package-card
          "search" :system :markdown
          data res render-context))

      :about
      (let [sr (str "### System Information

**Wiki Name**,, " (:wiki-name server-snapshot) "
**PageStore Directory** (relative to code) ,, " (.page-path ps) "
**Is Git Repo?**  ,, " (.git-repo? ps) "
**Site Url Root** ,, " (:site-url server-snapshot) "
**Export Dir** ,, " (.export-path ps) "
**Number of Pages** ,, " (count (.all-pages db))
                    )]
        (common/package-card i :system :markdown data sr render-context))

      :filelist
      (let [file-names (-> (.page-store server-snapshot)
                           .media-list)
            file-list (str "<ul>\n"
                           (apply
                             str
                             (map #(str "<li> <a href='/media/" % "'>" % "</a></li>\n")
                                  file-names))
                           "</ul>")]
        (common/package-card i :system :html data file-list render-context))

      ;; not recognised
      (let [d (str "Not recognised system command in " data " -- cmd " cmd)]
        (common/package-card i :system :raw data d render-context)))))

(defn backlinks
  [server-snapshot page-name]
  (let [bl (.links-to server-snapshot page-name)]
    (cond
      (= bl :not-available)
      (common/package-card
        :backlinks :system :markdown
        "Backlinks Not Available"
        "Backlinks Not Available"
        false)

      (= bl '())
      (common/package-card
        :backlinks :system :markdown
        "No Backlinks"
        "No Backlinks"
        false)

      :otherwise
      (ldb-query->mdlist-card
        "backlinks" "backlinks" "Backlinks" bl
        :calculated
        (fn [[a b]] (str "* [[" a "]] \n"))
        false))))
