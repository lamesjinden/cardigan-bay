(ns clj-ts.export.static-export
  (:require [clj-ts.cards.system :as system]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [markdown.core :as md]
            [clj-ts.common :as common]
            [clj-ts.cards.cards :as cards]
            [clj-ts.card-server :as card-server]
            [cljstache.core :refer [render]]
            [clj-ts.export.page-exporter :as page-exporter])
  (:import (java.io FileOutputStream)
           (java.nio.file Files)
           (java.time LocalDateTime)))

(defn double-bracket-links
  "Turn the internal double-bracket-links into real links in the exported pages"
  [text pe]
  (let [replace-link
        (fn [[_ m]]
          (if (.page-exists? (.page-store pe) m)
            (str "<a class=\"exported-internal-link\" href=\""
                 (.page-name->exported-link pe m) "\">"
                 m "</a>")
            (str "<em>" m "</em>")))]
    (string/replace text
                    #"\[\[(.+?)\]\]"
                    replace-link)))

(defn process-script [s]
  (if (string/includes? s ";;;;PUBLIC")
    (let [broken (string/split s #";;;;PUBLIC")
          private (first broken)
          public (second broken)]
      [private public])
    ["" s]))

(defn exported-workspace [card]
  (let [id (-> (hash card) (string/replace "-" ""))
        fn-name (str "run" id)
        src-name (str "id" id)
        src-name-private (str "idp" id)
        output-name (str "out" id)
        [private public] (process-script (:source_data card))
        final-script (str "(defn " fn-name " []
          (let [public-src (->
                       (.getElementById js/document \"" src-name "\")
                       .-value
                    )
               private-src (->
                       (.getElementById js/document \"" src-name-private "\")
                       .-value
                    )
               src (str private-src " "  public-src )
               result (js/scittle.core.eval_string src)
               out (-> (.getElementById js/document \"" output-name "\")
                       .-innerHTML
                       (set! result) )


]
           (.log js/console result)

            ))
")
        vname (str ".-" fn-name)
        set (str "(set! (" vname " js/window) " fn-name ")")]

    (hiccup/html
      [:div {:class "scittle-workspace"}
       [:input {:type  :hidden
                :id    src-name-private
                :value private}]
       [:textarea {:id src-name :cols 80 :rows 15}
        public]
       [:script {:type "application/x-scittle"}
        "\n"
        final-script
        "\n"
        set
        "\n"]
       [:button {:onclick (str fn-name "()")} "Run"]
       [:div {:id output-name}]])))

(defn card-specific-wrapper [card server-prepared]
  (condp = (:render_type card)
    :manual-copy
    (str "<div class='manual-copy'>" server-prepared "</div>")
    :code
    (str "<code>" server-prepared "</code>")
    :raw
    (str "<pre>" server-prepared "</pre>")
    :workspace (exported-workspace card)
    :transclude "<div class='transcluded'>" server-prepared "</div>"
    server-prepared))

(defn card->html
  "HTML for an exported card"
  [card pe]
  (let [html
        (condp = (:source_type card)
          :patterning
          (:server_prepared_data card)

          :code
          (:server_prepared_data card)

          (-> (get card :server_prepared_data)
              (common/double-comma-table)
              (md/md-to-html-string)
              (common/auto-links)
              (double-bracket-links pe)))]
    (str "<div class=\"card-outer\">
<div class=\"card\">
" (card-specific-wrapper card html)
         "</div></div>
")))

(defn ep [s]
  (if (nil? s)
    "NONE"
    s))

(deftype PageExporter [page-store export-extension export-link-pattern]
  page-exporter/IPageExporter

  (as-map [this]
    {:page-store          page-store
     :export-extension    export-extension
     :export-link-pattern export-link-pattern})

  (report [this]
    (str "Export Extension :\t" (ep export-extension) "
Export Link Pattern :\t" (ep export-link-pattern) "
Example Exported Link :\t" (.page-name->exported-link this "ExamplePage")))

  (export-path [this]
    (-> this .page-store .export-path))

  (page-name->export-file-path [this page-name]
    (-> this .page-store .export-path
        (.resolve (str page-name export-extension))))

  (page-name->exported-link [this page-id]
    (str export-link-pattern page-id export-extension))

  (media-name->exported-link [this media-name]
    (str export-link-pattern "media/" media-name))

  (api-path [this]
    (.resolve (-> this .page-store .export-path) "api"))

  (load-template [this]
    (try
      (let [tpl-path (.resolve (.system-path page-store) "export_resources/index.html")]
        (println "Loading template
" tpl-path)
        (slurp (.toString tpl-path)))
      (catch Exception e
        (do (println "ERROR FINDING TEMPLATE
" e "
USING DEFAULT")
            (hiccup/html
              [:html
               [:head]
               [:body
                [:div "<!-- NOTE :: Cardigan Bay couldn't find custom template, using hardwired default -->"]
                [:div {:class "navbar"}]
                [:div
                 [:h1 "{{page-title}}"]]
                [:div
                 "{{{page-main-content}}}"]]])))))

  (load-main-css [this]
    (try
      (let [css-path (.resolve (.system-path page-store) "export_resources/main.css")]
        (slurp (.toString css-path)))
      (catch Exception e
        (println "ERROR FINDING CSS FILE " e "
USING DEFAULT"))))

  (export-media-dir [this]
    (let [from-stream (.media-files-as-new-directory-stream page-store)
          to (.media-export-path page-store)]
      (try
        (doseq [file from-stream]
          (let [new-file (new FileOutputStream (.toFile (.resolve to (.getFileName file))))]
            (Files/copy file new-file)))
        (catch Exception e (println (str "Something went wrong " e)))))))

(defn make-page-exporter [page-store export-extension export-link-pattern]
  (->PageExporter page-store export-extension export-link-pattern))

(defn export-recentchanges-rss [server-snapshot]
  (let [api-path (-> server-snapshot :page-exporter .api-path)
        rc-rss (.resolve api-path "rc-rss.xml")
        link-fn (fn [p-name]
                  (str (:site-url server-snapshot) p-name))]
    (io/make-parents (.toString rc-rss))
    (spit (.toString rc-rss) (card-server/rss-recent-changes server-snapshot link-fn))))

(defn export-main-css [server-state main-css]
  (let [css-path (.resolve (-> server-state :page-exporter .export-path) "main.css")]
    (try
      (spit (.toString css-path) main-css)
      (catch Exception e (println "Something went wrong ... " e)))))

(defn load->cards-for-export [server-snapshot page-name link-renderer]
  (as-> server-snapshot $
        (.page-store $)
        (.load-page $ page-name)
        (cards/raw->cards server-snapshot $ {:user-authored? true
                                               :for-export?    true
                                               :link-renderer  link-renderer})))

(defn export-page [page-name server-snapshot tpl]
  (let [page-store (:page-store server-snapshot)
        exporter (:page-exporter server-snapshot)
        cards (load->cards-for-export server-snapshot page-name (fn [s] (double-bracket-links s exporter)))
        last-mod (.last-modified page-store page-name)
        file-name (-> (.page-name->export-file-path exporter page-name) .toString)
        rendered (string/join
                   "\n"
                   (map #(card->html % exporter)
                        (filter #(not (common/card-is-blank? %)) cards)))
        insert-page (hiccup/html
                      [:div
                       [:div
                        rendered]
                       [:div {:class "system"}
                        (card->html (system/backlinks server-snapshot page-name) exporter)]])
        page (render tpl
                     {:page-title        page-name
                      :page-main-content insert-page
                      :time              (LocalDateTime/now)
                      :last-modified     last-mod
                      :wiki-name         (.wiki-name server-snapshot)})]
    (println "Exporting " page-name)
    (println "Outfile = " file-name)
    (spit file-name page)))

(defn export-list-of-pages [server-snapshot page-names]
  (let [tpl (-> server-snapshot :page-exporter .load-template)]
    (doseq [page-name page-names]
      (println "Exporting " page-name)
      (try
        (export-page page-name server-snapshot tpl)
        (catch Exception e (println e))))))

(defn export-all-pages [server-state]
  (if (= :not-available (.all-pages server-state))
    :not-exported
    (let [css (-> server-state :page-exporter .load-main-css)
          a2 (filter
               (fn [name]
                 (cond
                   (= "AllPages" name) false
                   (= "AllLinks" name) false
                   (= "BrokenLinks" name) false
                   (= "OrphanPages" name) false
                   :otherwise true))
               (.all-pages server-state))]
      (export-list-of-pages server-state a2)
      (println "Export recentchanges rss")
      (export-recentchanges-rss server-state)
      (println "Export main.css")
      (export-main-css server-state css)
      (println "Exporting media")
      (.export-media-dir (:page-exporter server-state)))))

(defn export-one-page [page-name server-snapshot]
  (let [tpl (-> server-snapshot :page-exporter .load-template)]
    (export-page page-name server-snapshot tpl)
    (export-recentchanges-rss server-snapshot)
    (println "Exporting media")
    (.export-media-dir (:page-exporter server-snapshot))))
