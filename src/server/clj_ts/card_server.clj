(ns clj-ts.card-server
  [:require
    [clojure.string :as string]
    [clj-ts.logic :as ldb]
    [clj-ts.pagestore :as pagestore]
    [clj-ts.common :as common]
    [clj-ts.embed :as embed]
    [clj-ts.network :as network]
    [clj-ts.patterning :as patterning]
    [clj-rss.core :as rss]
    [markdown.core :as md]
    [sci.core :as sci]]
  (:import (java.util.regex Pattern)))

;; Card Server State is ALL the global state for the application.
;; NOTHING mutable should be stored anywhere else but in the card-server-state atom.

;; Card Server state is just a defrecord.
;; But two components : the page-store and page-exporter are
;; deftypes in their own right.
;; page-store has all the file-system information that the wiki reads and writes.
;; page-exporter the other info for exporting flat files

(defmacro dnn [cs m & args]
  `(let [db# (:facts-db ~cs)]
     (if (nil? db#) :not-available
                    (. db# ~m ~@args))))

(defrecord CardServerRecord
  [wiki-name site-url port-no start-page facts-db page-store page-exporter]

  ldb/IFactsDb
  (raw-db [cs] (dnn cs raw-db))
  (all-pages [cs] (dnn cs all-pages))
  (all-links [cs] (dnn cs all-links))
  (broken-links [cs] (dnn cs broken-links))
  (orphan-pages [cs] (dnn cs orphan-pages))
  (links-to [cs p-name] (dnn cs links-to p-name)))

;; State Management is done at the card-server level

;; todo - def doesn't play well with wrap-reload
(def the-server-state (atom :dummy))

(defn initialize-state! [wiki-name site-url port-no start-page logic-db page-store page-exporter]
  (reset! the-server-state
          (->CardServerRecord
            wiki-name
            site-url
            port-no
            start-page
            logic-db
            page-store
            page-exporter)))

(defn server-state
  "Other modules should always get the card-server data through calling this function.
  Rather than relying on knowing the name of the atom"
  [] @the-server-state)

(defn set-state!
  "The official API call to update any of the key-value pairs in the card-server state"
  [key val]
  (swap! the-server-state assoc key val))

;; convenience functions for updating state
(defn set-wiki-name! [wname]
  (set-state! :wiki-name wname))

(defn set-site-url! [url]
  (set-state! :site-url url))

(defn set-start-page! [pagename]
  (set-state! :start-page pagename))

(defn set-port! [port]
  (set-state! :port-no port))

(defn set-facts-db! [facts]
  {:pre [(satisfies? ldb/IFactsDb facts)]}
  (set-state! :facts-db facts))

(defn set-page-store! [page-store]
  {:pre [(satisfies? pagestore/IPageStore page-store)]}
  (set-state! :page-store page-store))

;; PageStore delegation

(defn regenerate-db! []
  (future
    (println "Starting to rebuild logic db")
    (let [f (ldb/regenerate-db (server-state))]
      (set-facts-db! f)
      (println "Finished building logic db"))))

(defn write-page-to-file! [page-name body]
  (pagestore/write-page-to-file! (server-state) page-name body)
  (regenerate-db!))

(defn update-pagedir! [new-pd new-ed]
  (let [new-ps
        (pagestore/make-page-store
          new-pd
          new-ed)]
    (set-page-store! new-ps)
    (regenerate-db!)))

(defn page-exists? [page-name]
  (-> (.page-store (server-state))
      (.page-exists? page-name)))

(defn read-page [page-name]
  (-> (.page-store (server-state))
      (.load-page page-name)))

;; Useful for errors

(defn exception-stack [e]
  (let [sw (new java.io.StringWriter)
        pw (new java.io.PrintWriter sw)]
    (.printStackTrace e pw)
    (str "Exception :: " (.getMessage e) (-> sw .toString))))

;; Other functions

(defn search [pattern term]
  (let [db (-> (server-state) :facts-db)
        all-pages (.all-pages db)
        name-res (pagestore/name-search all-pages (re-pattern pattern))
        count-names (count name-res)
        res (pagestore/text-search (server-state) all-pages
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

(defn server-eval
  "Evaluate Clojure code embedded in a card. Evaluated with SCI
   but on the server. I hope there's no risk for this ...
   BUT ..."
  [data]
  (let [code data
        evaluated
        (try
          (#(apply str (sci/eval-string code)))
          (catch Exception e exception-stack))]
    evaluated))

(defn server-custom-script
  "Evaluate a script from system/custom/ with arguments"
  [data]
  (println "In server-custom-script")
  (str "This will (eventually) run a custom script: " data))

(defn ldb-query->mdlist-card [i source_data title result qname f render-context]
  (let [items (apply str (map f result))
        body (str "*" title "* " "*(" (count result) " items)*\n\n" items)]
    (common/package-card i :system :markdown source_data body render-context)))

(defn item1 [s] (str "* [[" s "]]\n"))

(defn file-link [data]
  (let [{:keys [file-name label]} (-> data read-string)]
    (str "<a href='" "/media/" file-name "'>"
         (if label label file-name)
         "</a>")))

(defn system-card [i data render-context]
  (let [
        info (read-string data)
        cmd (:command info)
        db (-> (server-state) :facts-db)
        ps (-> (server-state) :page-store)]

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
      (let [res (search (:query info) (:query info))]
        (common/package-card
          "search" :system :markdown
          data res render-context))

      :about
      (let [sr (str "### System Information

**Wiki Name**,, " (:wiki-name (server-state)) "
**PageStore Directory** (relative to code) ,, " (.page-path ps) "
**Is Git Repo?**  ,, " (.git-repo? ps) "
**Site Url Root** ,, " (:site-url (server-state)) "
**Export Dir** ,, " (.export-path ps) "
**Number of Pages** ,, " (count (.all-pages db))
                    )]
        (common/package-card i :system :markdown data sr render-context))

      :customscript
      (let [return-type (or (:return-type data) :markdown)
            sr (server-custom-script data)]
        (common/package-card i :customscript return-type data sr render-context))

      :filelist
      (let [file-names (-> (.page-store (server-state))
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

(defn bookmark-card [data]
  (let [{:keys [url timestamp]} (read-string data)]
    (str "
Bookmarked " timestamp ": <" url ">

")))

(defn afind [n ns]
  (cond (empty? ns) nil
        (= n (-> ns first first))
        (-> ns first rest)
        :otherwise (afind n (rest ns))))

(defn md->html [s]
  (-> s
      (common/double-comma-table)
      (md/md-to-html-string)
      (common/auto-links)
      (common/double-bracket-links)))

(defn process-card-map
  [i {:keys [source_type source_data]} render-context]
  (try
    [(condp = source_type
       :markdown (common/package-card i source_type :markdown source_data source_data render-context)
       :manual-copy (common/package-card i source_type :manual-copy source_data source_data render-context)
       :raw (common/package-card i source_type :raw source_data source_data render-context)

       :code
       (do
         (println "Exporting :code card ")
         (common/package-card i :code :code source_data source_data render-context))

       :evalraw
       (common/package-card i :evalraw :raw source_data (server-eval source_data) render-context)

       :evalmd
       (common/package-card i :evalmd :markdown source_data (server-eval source_data) render-context)

       :workspace
       (common/package-card i source_type :workspace source_data source_data render-context)

       :system
       (system-card i source_data render-context)

       :embed
       (common/package-card i source_type :html source_data
                            (embed/process source_data
                                           render-context
                                           (if (:for-export? render-context)
                                             (:link-renderer render-context)
                                             (fn [s] (md->html s)))
                                           (server-state))
                            render-context)

       :bookmark
       (common/package-card i :bookmark :markdown source_data (bookmark-card source_data) render-context)


       :network
       (network/network-card i source_data render-context)

       :patterning
       (common/package-card i :patterning :html source_data
                            (patterning/one-pattern source_data) render-context)

       :filelink
       (common/package-card i :filelink :html source_data
                            (file-link source_data) render-context)

       ;; not recognised
       (common/package-card i source_type source_type source_data source_data render-context))]
    (catch
      Exception e
      [(common/package-card
         i :raw :raw source_data
         (str "Error \n\nType was\n" source_type
              "\nSource was\n" source_data
              "\n\nStack trace\n"
              (exception-stack e))
         render-context)])))

(defn transclude [i source-data render-context]
  (let [{:keys [from _process ids]} (read-string source-data)
        ps (.page-store (server-state))
        matched-cards (.get-cards-from-page ps from ids)
        card-maps->processed (fn [id-start card-maps render-context]
                               (mapcat process-card-map (iterate inc id-start) card-maps (repeat render-context)))
        ;; todo - may have broken transclusion here while attempting to avoid forward declaring card-maps->processed
        cards (card-maps->processed (* 100 i) matched-cards render-context)
        body (str "### Transcluded from [[" from "]]")]
    (concat [(common/package-card i :transclude :markdown body body render-context)] cards)))

(defn process-card [i {:keys [source_type source_data] :as card-maps} render-context]
  (if (= source_type :transclude)
    (transclude i source_data render-context)
    (process-card-map i card-maps render-context)))

(defn raw->cards [raw render-context]
  (let [card-maps (common/raw-text->card-maps raw)]
    (mapcat process-card (iterate inc 0) card-maps (repeat render-context))))

(defn backlinks [page-name]
  (let [bl (.links-to (server-state) page-name)]
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

(defn load->cards [page-name]
  (-> (server-state) .page-store
      (.load-page page-name)
      (raw->cards {:user-authored? true :for-export? false})))

(defn load->cards-for-export [page-name link-renderer]
  (-> (server-state) .page-store
      (.load-page page-name)
      (raw->cards {:user-authored? true
                   :for-export?    true
                   :link-renderer  link-renderer})))

(defn generate-system-cards [page-name]
  [(backlinks page-name)])

(defn load-one-card [page-name hash]
  (let [cards (load->cards page-name)]
    (common/find-card-by-hash cards hash)))

;; GraphQL resolvers

(defn resolve-text-search [_context arguments _value]
  (let [{:keys [query_string]} arguments
        query-pattern-str (str "(?i)" (Pattern/quote query_string))
        out (search query-pattern-str query_string)]
    {:result_text out}))

(defn resolve-card
  "Not yet tested"
  [context arguments value render-context]
  (let [{:keys [page_name hash]} arguments
        ps (.page-store (server-state))]
    (if (.page-exists? ps page_name)
      (-> (load->cards page_name)
          (common/find-card-by-hash hash))
      (common/package-card 0 :markdown :markdown
                           (str "Card " hash " doesn't exist in " page_name)
                           (str "Card " hash " doesn't exist in " page_name)
                           render-context))))

(defn resolve-source-page [_context arguments _value]
  (let [{:keys [page_name]} arguments
        ps (.page-store (server-state))]
    (if (.page-exists? ps page_name)
      {:page_name page_name
       :body      (pagestore/read-page (server-state) page_name)}
      {:page_name page_name
       :body
       (str "A PAGE CALLED " page_name " DOES NOT EXIST
Check if the name you typed, or in the link you followed is correct.
If you would *like* to create a page with this name, simply click the [Edit] button to edit this text. When you save, you will create the page")})))

(defn resolve-page [_context arguments _value]
  (let [{:keys [page_name]} arguments
        ps (:page-store (server-state))
        wiki-name (:wiki-name (server-state))
        site-url (:site-url (server-state))
        start-page-name (:start-page (server-state))]
    (if (.page-exists? ps page_name)
      {:page_name       page_name
       :wiki_name       wiki-name
       :site_url        site-url
       :public_root     (str site-url "/view/")
       :start_page_name start-page-name
       :cards           (load->cards page_name)
       :system_cards    (generate-system-cards page_name)}
      {:page_name       page_name
       :wiki_name       wiki-name
       :site_url        site-url
       :start_page_name start-page-name
       :public_root     (str site-url "/view/")
       :cards           (raw->cards
                          (str "<div style='color:#990000'>A PAGE CALLED " page_name " DOES NOT EXIST


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

(defn rss-recent-changes [link-fn]
  (let [ps (:page-store (server-state))
        make-link (fn [s]
                    (let [m (re-matches #"\* \[\[(\S+)\]\] (\(.+\))" s)
                          [pname date] [(second m) (nth m 2)]]
                      {:title (str pname " changed on " date)
                       :link  (link-fn pname)}))
        rc (-> (.read-recent-changes ps)
               string/split-lines
               (#(map make-link %)))]
    (rss/channel-xml {:title       "RecentChanges"
                      :link        (-> (server-state) :site-url)
                      :description "Recent Changes in CardiganBay Wiki"}
                     rc)))

;; transforms on pages

(defn append-card-to-page! [page-name type body]
  (let [page-body (try
                    (pagestore/read-page (server-state) page-name)
                    (catch Exception e (str "Automatically created a new page : " page-name "\n\n")))
        new-body (str page-body "----
" type "
" body)]
    (write-page-to-file! page-name new-body)))

(defn prepend-card-to-page! [page-name type body]
  (let [page-body (try
                    (pagestore/read-page (server-state) page-name)
                    (catch Exception e (str "Automatically created a new page : " page-name "\n\n")))
        new-body (str
                   "----
" type "
" body "

----
"
                   page-body)]
    (write-page-to-file! page-name new-body)))

(defn move-card [page-name hash destination-name]
  (if (= page-name destination-name)
    nil                                                     ;; don't try to move to self
    (let [ps (.page-store (server-state))
          from-cards (.get-page-as-card-maps ps page-name)
          card (common/find-card-by-hash from-cards hash)
          stripped (into [] (common/remove-card-by-hash from-cards hash))
          stripped_raw (common/cards->raw stripped)]
      (when (not (nil? card))
        (append-card-to-page! destination-name (:source_type card) (:source_data card))
        (write-page-to-file! page-name stripped_raw)))))

(defn reorder-card [page-name hash direction]
  (let [ps (.page-store (server-state))
        cards (.get-page-as-card-maps ps page-name)
        new-cards (if (= "up" direction)
                    (common/move-card-up cards hash)
                    (common/move-card-down cards hash))]
    (write-page-to-file! page-name (common/cards->raw new-cards))))

(defn replace-card [page-name hash new-body]
  (let [ps (.page-store (server-state))
        cards (.get-page-as-card-maps ps page-name)
        ;; todo - this is where things get weird
        #_new-card #_(common/raw-card-text->card-map (str source-type "\n" new-body))
        new-card (common/raw-card-text->card-map new-body)
        new-cards (common/replace-card
                    cards
                    #(common/match-hash % hash)
                    new-card)]
    (write-page-to-file! page-name (common/cards->raw new-cards))))

;;;; Media and Custom files

(defn load-media-file [file-name]
  (-> (server-state) :page-store (.load-media-file file-name)))

(defn load-custom-file [file-name]
  (-> (server-state) :page-store (.load-custom-file file-name)))
