(ns clj-ts.card-server
  [:require
    [clojure.string :as string]
    [clj-ts.query.logic :as ldb]
    [clj-ts.storage.page_store :as pagestore]
    [clj-ts.query.facts-db :as facts]
    [clj-ts.query.card-server-record :as server-record]
    [clj-ts.common :as common]
    [clj-ts.embed :as embed]
    [clj-ts.exporting.page-exporter]
    [clj-ts.network :as network]
    [clj-ts.patterning :as patterning]
    [clj-rss.core :as rss]
    [clj-ts.util :as util]
    [markdown.core :as md]
    [sci.core :as sci]]
  (:import (clojure.lang Atom)
           (java.util.regex Pattern)))

;; Card Server state is just a defrecord.
;; But two components : the page-store and page-exporter are
;; deftypes in their own right.
;; page-store has all the file-system information that the wiki reads and writes.
;; page-exporter the other info for exporting flat files

(defn create-card-server ^Atom [wiki-name site-url port-no start-page logic-db page-store page-exporter]
  (atom (server-record/->CardServerRecord
          wiki-name
          site-url
          port-no
          start-page
          logic-db
          page-store
          page-exporter)))

(defn- set-state!
  "The official API call to update any of the key-value pairs in the card-server state"
  [^Atom card-server key val]
  (swap! card-server assoc key val))

(defn set-start-page!
  [^Atom card-server page-name]
  (set-state! card-server :start-page page-name))

(defn- set-facts-db!
  [^Atom card-server facts-db]
  {:pre [(satisfies? facts/IFactsDb facts-db)]}
  (set-state! card-server :facts-db facts-db))

;; PageStore delegation

(defn regenerate-db!
  [^Atom card-server]
  (let [f (ldb/regenerate-db @card-server)]
    (set-facts-db! card-server f)))

(defn write-page-to-file!
  [^Atom card-server page-name body]
  (pagestore/write-page-to-file! @card-server page-name body)
  (regenerate-db! card-server))

(defn page-exists?
  [^Atom card-server page-name]
  (let [server-snapshot @card-server]
    (-> (.page-store server-snapshot)
        (.page-exists? page-name))))

;; Other functions

(defn- search
  [^Atom card-server pattern term]
  (let [state-snapshot @card-server
        db (-> state-snapshot :facts-db)
        all-pages (.all-pages db)
        name-res (pagestore/name-search all-pages (re-pattern pattern))
        count-names (count name-res)
        res (pagestore/text-search state-snapshot all-pages
                                   (re-pattern pattern))
        count-res (count res)
        name-list (apply str (map #(str "* [[" % "]]\n") name-res))
        res-list (apply str (map #(str "* [[" % "]]\n") res))
        out (str "

*" count-names " PageNames containing \"" term "\"*\n
" name-list "

*" count-res " Pages containing \" " term "\"*\n "
                 res-list)]
    out))

;; Card Processing

;; We're going to use a map to store flags and other gubbins needed
;; in the rendering pipeline. Particularly to track whether we're
;; doing something in a normal rendering context or an export context
;; And whether a card is system generated or human generated.

;; We'll call it render-context
;; {:for-export false :user-authored? true}

(defn- server-eval
  "Evaluate Clojure code embedded in a card. Evaluated with SCI
   but on the server. I hope there's no risk for this ...
   BUT ..."
  [data]
  (let [code data
        evaluated (try
                    (#(apply str (sci/eval-string code)))
                    (catch Exception e util/exception-stack))]
    evaluated))

(defn- ldb-query->mdlist-card [i source_data title result qname f render-context]
  (let [items (apply str (map f result))
        body (str "*" title "* " "*(" (count result) " items)*\n\n" items)]
    (common/package-card i :system :markdown source_data body render-context)))

(defn- item1 [s] (str "* [[" s "]]\n"))

(defn- file-link [data]
  (let [{:keys [file-name label]} (-> data read-string)]
    (str "<a href='" "/media/" file-name "'>"
         (if label label file-name)
         "</a>")))

(defn- system-card
  [^Atom card-server i data render-context]
  (let [info (read-string data)
        cmd (:command info)
        state-snapshot @card-server
        db (-> state-snapshot :facts-db)
        ps (-> state-snapshot :page-store)]

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
      (let [res (search card-server (:query info) (:query info))]
        (common/package-card
          "search" :system :markdown
          data res render-context))

      :about
      (let [sr (str "### System Information

**Wiki Name**,, " (:wiki-name state-snapshot) "
**PageStore Directory** (relative to code) ,, " (.page-path ps) "
**Is Git Repo?**  ,, " (.git-repo? ps) "
**Site Url Root** ,, " (:site-url state-snapshot) "
**Export Dir** ,, " (.export-path ps) "
**Number of Pages** ,, " (count (.all-pages db))
                    )]
        (common/package-card i :system :markdown data sr render-context))

      :filelist
      (let [file-names (-> (.page-store state-snapshot)
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

(defn- bookmark-card [data]
  (let [{:keys [url timestamp]} (read-string data)]
    (str "
Bookmarked " timestamp ": <" url ">

")))

(defn- md->html [s]
  (-> s
      (common/double-comma-table)
      (md/md-to-html-string)
      (common/auto-links)
      (common/double-bracket-links)))

(defn- process-card-map
  [^Atom card-server i {:keys [source_type source_data]} render-context]
  (let [server-snapshot @card-server]
    (try
      [(condp = source_type

         :markdown
         (common/package-card i source_type :markdown source_data source_data render-context)

         :manual-copy
         (common/package-card i source_type :manual-copy source_data source_data render-context)

         :raw
         (common/package-card i source_type :raw source_data source_data render-context)

         :code
         (common/package-card i :code :code source_data source_data render-context)

         :evalraw
         (common/package-card i :evalraw :raw source_data (server-eval source_data) render-context)

         :evalmd
         (common/package-card i :evalmd :markdown source_data (server-eval source_data) render-context)

         :workspace
         (common/package-card i source_type :workspace source_data source_data render-context)

         :system
         (system-card card-server i source_data render-context)

         :embed
         (common/package-card i source_type :html source_data
                              (embed/process source_data
                                             render-context
                                             (if (:for-export? render-context)
                                               (:link-renderer render-context)
                                               (fn [s] (md->html s)))
                                             server-snapshot)
                              render-context)

         :bookmark
         (common/package-card i :bookmark :markdown source_data (bookmark-card source_data) render-context)

         :network
         (network/network-card i source_data render-context)

         :patterning
         (common/package-card i :patterning :html source_data (patterning/one-pattern source_data) render-context)

         :filelink
         (common/package-card i :filelink :html source_data (file-link source_data) render-context)

         ;; not recognised
         (common/package-card i source_type source_type source_data source_data render-context))]
      (catch
        Exception e
        [(common/package-card
           i :raw :raw source_data
           (str "Error \n\nType was\n" source_type
                "\nSource was\n" source_data
                "\n\nStack trace\n"
                (util/exception-stack e))
           render-context)]))))

(defn- transclude
  [^Atom card-server i source-data render-context]
  (let [{:keys [from _process ids]} (read-string source-data)
        server-snapshot @card-server
        ps (.page-store server-snapshot)
        matched-cards (.get-cards-from-page ps from ids)
        card-maps->processed (fn [id-start card-maps render-context]
                               (mapcat process-card-map (iterate inc id-start) card-maps (repeat render-context)))
        ;; todo - may have broken transclusion here while attempting to avoid forward declaring card-maps->processed
        cards (card-maps->processed (* 100 i) matched-cards render-context)
        body (str "### Transcluded from [[" from "]]")]
    (concat [(common/package-card i :transclude :markdown body body render-context)] cards)))

(defn- process-card [^Atom card-server i {:keys [source_type source_data] :as card-maps} render-context]
  (if (= source_type :transclude)
    (transclude card-server i source_data render-context)
    (process-card-map card-server i card-maps render-context)))

(defn- raw->cards [^Atom card-server raw render-context]
  (let [card-maps (common/raw-text->card-maps raw)]
    (mapcat (fn [i card-maps render-context] (process-card card-server i card-maps render-context))
            (iterate inc 0)
            card-maps
            (repeat render-context))))

(defn backlinks
  [^Atom card-server page-name]
  (let [server-snapshot @card-server
        bl (.links-to server-snapshot page-name)]
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

(defn- load->cards
  [^Atom card-server page-name]
  (let [server-snapshot @card-server]
    (as-> server-snapshot $
          (.page-store $)
          (.load-page $ page-name)
          (raw->cards card-server $ {:user-authored? true :for-export? false}))))

(defn load->cards-for-export
  [^Atom card-server page-name link-renderer]
  (let [server-snapshot @card-server]
    (as-> server-snapshot $
          (.page-store $)
          (.load-page $ page-name)
          (raw->cards card-server $ {:user-authored? true
                                     :for-export?    true
                                     :link-renderer  link-renderer}))))

(defn- generate-system-cards [^Atom card-server page-name]
  [(backlinks card-server page-name)])

;; GraphQL resolvers

(defn resolve-text-search [^Atom card-server _context arguments _value]
  (let [{:keys [query_string]} arguments
        query-pattern-str (str "(?i)" (Pattern/quote query_string))
        out (search card-server query-pattern-str query_string)]
    {:result_text out}))

(defn resolve-source-page
  [^Atom card-server _context arguments _value]
  (let [{:keys [page_name]} arguments
        server-snapshot @card-server
        ps (.page-store server-snapshot)]
    (if (.page-exists? ps page_name)
      {:page_name page_name
       :body      (pagestore/read-page server-snapshot page_name)}
      {:page_name page_name
       :body
       (str "A PAGE CALLED " page_name " DOES NOT EXIST
Check if the name you typed, or in the link you followed is correct.
If you would *like* to create a page with this name, simply click the [Edit] button to edit this text. When you save, you will create the page")})))

(defn resolve-page
  [^Atom card-server _context arguments _value]
  (let [{:keys [page_name]} arguments
        server-snapshot @card-server
        ps (:page-store server-snapshot)
        wiki-name (:wiki-name server-snapshot)
        site-url (:site-url server-snapshot)
        start-page-name (:start-page server-snapshot)]
    (if (.page-exists? ps page_name)
      {:page_name       page_name
       :wiki_name       wiki-name
       :site_url        site-url
       :public_root     (str site-url "/view/")
       :start_page_name start-page-name
       :cards           (load->cards card-server page_name)
       :system_cards    (generate-system-cards card-server page_name)}
      {:page_name       page_name
       :wiki_name       wiki-name
       :site_url        site-url
       :start_page_name start-page-name
       :public_root     (str site-url "/view/")
       :cards           (raw->cards card-server (str "<div style='color:#990000'>A PAGE CALLED " page_name " DOES NOT EXIST


Check if the name you typed, or in the link you followed is correct.

If you would *like* to create a page with this name, simply click the [Edit] button to edit this text. When you save, you will create the page
</div>")
                                    {:user-authored? false :for-export? false})
       :system_cards
       (let [sim-names (map
                         #(str "\n- [[" % "]]")
                         (.similar-page-names
                           ps page_name))]
         (if (empty? sim-names) []
                                [(common/package-card
                                   :similarly_name_pages :system :markdown ""
                                   (str "Here are some similarly named pages :"
                                        (apply str sim-names)) false)]))})))

;; RecentChanges as RSS

(defn rss-recent-changes
  [^Atom card-server link-fn]
  (let [server-snapshot @card-server
        ps (:page-store server-snapshot)
        make-link (fn [s]
                    (let [m (re-matches #"\* \[\[(\S+)\]\] (\(.+\))" s)
                          [pname date] [(second m) (nth m 2)]]
                      {:title (str pname " changed on " date)
                       :link  (link-fn pname)}))
        rc (-> (.read-recent-changes ps)
               string/split-lines
               (#(map make-link %)))]
    (rss/channel-xml {:title       "RecentChanges"
                      :link        (-> server-snapshot :site-url)
                      :description "Recent Changes in CardiganBay Wiki"}
                     rc)))

;; transforms on pages

(defn- append-card-to-page!
  [^Atom card-server page-name type body]
  (let [server-snapshot @card-server
        page-body (try
                    (pagestore/read-page server-snapshot page-name)
                    (catch Exception e (str "Automatically created a new page : " page-name "\n\n")))
        new-body (str page-body "----
" type "
" body)]
    (write-page-to-file! card-server page-name new-body)))

(defn move-card!
  [^Atom card-server page-name hash destination-name]
  (if (= page-name destination-name)
    nil                                                     ;; don't try to move to self
    (let [server-snapshot @card-server
          ps (.page-store server-snapshot)
          from-cards (.get-page-as-card-maps ps page-name)
          card (common/find-card-by-hash from-cards hash)
          stripped (into [] (common/remove-card-by-hash from-cards hash))
          stripped-raw (common/cards->raw stripped)]
      (when (not (nil? card))
        (append-card-to-page! card-server destination-name (:source_type card) (:source_data card))
        (write-page-to-file! card-server page-name stripped-raw)))))

(defn reorder-card!
  [^Atom card-server page-name hash direction]
  (let [server-snapshot @card-server
        ps (.page-store server-snapshot)
        cards (.get-page-as-card-maps ps page-name)
        new-cards (if (= "up" direction)
                    (common/move-card-up cards hash)
                    (common/move-card-down cards hash))]
    (write-page-to-file! card-server page-name (common/cards->raw new-cards))))

(defn replace-card!
  [^Atom card-server page-name hash new-body]
  (let [server-snapshot @card-server
        ps (.page-store server-snapshot)
        cards (.get-page-as-card-maps ps page-name)
        ;; todo - this is where things get weird
        #_new-card #_(common/raw-card-text->card-map (str source-type "\n" new-body))
        new-card (common/raw-card-text->card-map new-body)
        new-cards (common/replace-card
                    cards
                    #(common/match-hash % hash)
                    new-card)]
    (write-page-to-file! card-server page-name (common/cards->raw new-cards))))
