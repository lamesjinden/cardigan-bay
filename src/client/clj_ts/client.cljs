(ns clj-ts.client
  (:require
    [cljs.core]
    [cljs.pprint :refer [pprint]]
    [clojure.string :as str]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as r]
    [reagent.dom :as dom]
    [sci.core :as sci]
    [markdown.core :as md]
    [cljsjs.highlight]
    [cljsjs.highlight.langs.clojure]
    [cljsjs.highlight.langs.bash]
    [cljsjs.ace]
    [clj-ts.common :refer [raw-card-text->raw-card-map
                           double-comma-table
                           double-bracket-links
                           auto-links]])
  (:import [goog.net XhrIo]))

(goog-define env "production")

;; State
(defonce db (r/atom
              {:current-page "HelloWorld"
               :raw          ""
               :transcript   ""
               :cards        []
               :past         ["HelloWorld"]
               :future       []
               :wiki-name    "Wiki Name"
               :site-url     "Site URL"
               :mode         :viewing
               :env-port     4545}))

(defn http-send [{:keys [url callback method body headers timeout with-credentials?]
                  :or   {body              nil
                         headers           nil
                         timeout           0
                         with-credentials? false}}]
  (when (not url)
    (throw (js/error "url was not defined")))
  (when (not callback)
    (throw (js/error "callback was not defined")))
  (when (not method)
    (throw (js/error "method was not defined")))

  (let [url (if (= env "dev")
              (gstring/format "//localhost:%s%s" (:env-port @db) url)
              url)]
    (.send XhrIo
           url
           callback
           method
           body
           headers
           timeout
           with-credentials?)))

(defn http-get [url callback & {:keys [headers timeout with-credentials?]}]
  (http-send {:url               url
              :callback          callback
              :method            "GET"
              :headers           headers
              :timeout           timeout
              :with-credentials? with-credentials?}))

(defn http-post [url callback body & {:keys [headers timeout with-credentials?]}]
  (http-send {:url               url
              :callback          callback
              :method            "POST"
              :body              body
              :headers           headers
              :timeout           timeout
              :with-credentials? with-credentials?}))

;; PageStore

(defn ->load-page-query [page-name]
  (let [lcpn page-name]
    (str "{\"query\" : \"query GetPage {
  source_page(page_name: \\\"" lcpn "\\\" ) {
    page_name
    body
  }
  server_prepared_page(page_name:  \\\"" lcpn "\\\") {
    page_name
    wiki_name
    site_url
    port
    ip
    start_page_name
    cards {
      id
      hash
      source_type
      source_data
      render_type
      server_prepared_data
    }
    system_cards {
      id
      hash
      source_type
      source_data
      render_type
      server_prepared_data
    }
  }
} \",\"variables\":null, \"operationName\":\"GetPage\"}")))

(defn load-page! [page-name new-past new-future]
  (let [query (->load-page-query page-name)
        callback (fn [e]
                   (let [edn (-> e .-target .getResponseText .toString
                                 (#(.parse js/JSON %)) js->clj)
                         data (-> edn (get "data"))
                         raw (-> data (get "source_page") (get "body"))
                         cards (-> data (get "server_prepared_page") (get "cards"))
                         system-cards (-> data (get "server_prepared_page") (get "system_cards"))
                         site-url (-> data (get "server_prepared_page") (get "site_url"))
                         wiki-name (-> data (get "server_prepared_page") (get "wiki_name"))
                         port (-> data (get "server_prepared_page") (get "port"))
                         ip (-> data (get "server_prepared_page") (get "ip"))
                         start-page-name (-> data (get "server_prepared_page") (get "start_page_name"))]
                     (swap! db assoc
                            :current-page page-name
                            :site-url site-url
                            :wiki-name wiki-name
                            :port port
                            :ip ip
                            :start-page-name start-page-name
                            :raw raw
                            :cards cards
                            :system-cards system-cards
                            :past new-past
                            :future new-future))
                   (js/window.scroll 0 0))]
    (http-post "/clj_ts/graphql" callback query)))

(defn generate-form-data [params]
  (let [form-data (js/FormData.)]
    (doseq [[k v] params]
      (.append form-data (name k) v))
    form-data))

(defn reload! []
  (load-page! (:current-page @db) (-> @db :past) (-> @db :future)))

(defn save-page! []
  (let [page-name (-> @db :current-page)
        ace-instance (:ace-instance @db)
        new-data (.getValue ace-instance)]
    (http-post
      "/clj_ts/save"
      (fn [_] (reload!))
      (pr-str {:page page-name
               :data new-data}))))

(defn card-reorder! [page-name hash direction]
  (http-post
    "/api/reordercard"
    (fn [_] (reload!))
    (pr-str {:page      page-name
             :hash      hash
             :direction direction})))

(defn ->text-search-query [cleaned-query]
  (str "{\"query\" : \"query TextSearch  {
text_search(query_string:\\\"" cleaned-query "\\\"){     result_text }
}\",  \"variables\":null, \"operationName\":\"TextSearch\"   }"))

;; Nav and History

(defn go-new! [p-name]
  (load-page! p-name (conj (-> @db :past) (-> @db :current-page)) [])
  (swap! db assoc :mode :viewing))

(defn forward! [p-name]
  (load-page! p-name (conj (-> @db :past) (-> @db :current-page)) (pop (-> @db :future))))

(defn back! []
  (load-page! (-> @db :past last) (pop (-> @db :past)) (conj (-> @db :future) (-> @db :current-page))))

;; Process page

(defn stamp! [stamp]
  (swap! db assoc
         :mode :editing
         :raw (str (-> @db :raw) "\n----\n:stamp\n" {:type stamp})))

; todo - broken. probably because edit-field was replaced with ace-editor
; todo - once fixed, can this be done without going directly to the dom?
(defn insert-text-at-cursor! [s]
  (let [ta (-> js/document
               (.getElementById "edit-field"))
        text (-> ta .-value)
        selectionStart (-> ta .-selectionStart)
        new (str
              (subs text 0 selectionStart)
              s
              (subs text selectionStart))]
    (swap! db assoc :raw new)
    (-> ta (.-value) (set! new))))

(defn prepend-transcript! [code result]
  (swap! db assoc :transcript
         (str "<p> > " code "
<br/>
" result "
</p>
" (-> @db :transcript)))
  (swap! db assoc :mode :transcript))

;; RUN

(let [url "/startpage"
      callback (fn [e] (-> e .-target .getResponseText .toString go-new!))]
  (http-get url callback))

;; Rendering Views

(defn nav-input [value]
  [:input {:type      "text"
           :id        "navinputbox"
           :value     @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn string->html [s]
  (-> s
      (double-comma-table)
      (md/md->html)
      (auto-links)
      (double-bracket-links)))

(defn search-text! [query-text]
  (let [cleaned-query (-> query-text
                          (#(str/replace % "\"" ""))
                          (#(str/replace % "'" "")))
        query (->text-search-query cleaned-query)
        callback (fn [e]
                   (let [edn (-> e .-target .getResponseText .toString (#(.parse js/JSON %)) js->clj)
                         data (-> edn (get "data"))
                         result (-> data (get "text_search") (get "result_text"))]
                     (prepend-transcript! (str "Searching for " cleaned-query) (string->html result))))]
    (http-post
      "/clj_ts/graphql"
      callback
      query)))

(defn nav-bar []
  (let [current (r/atom (-> @db :future last))]
    (fn []
      (let [start-page-name (-> @db :start-page-name)]
        [:div {:class "navbar"}
         [:div {:class "breadcrumbs"}
          [:span (-> @db :wiki-name)]]
         [:div {:id "nav1"}
          [:span {:on-click (fn [] (go-new! start-page-name))} start-page-name]
          " || "
          [:span {:on-click (fn [] (go-new! "ToDo"))} "Todo"]
          " || "
          [:span {:on-click (fn [] (go-new! "Work"))} "Work"]
          " || "
          [:span {:on-click (fn [] (go-new! "Projects"))} "Projects"]
          " || "
          [:span {:on-click (fn [] (go-new! "SandBox"))} "SandBox"]
          " || "
          [:a {:href "/api/exportallpages"} "Export All Pages"]]
         [:div {:id "nav2"}
          [:button
           {:class    "big-btn"
            :on-click (fn [] (back!))}
           [:img {:src "/icons/skip-back.png"}] " Back"]
          [:button
           {:class    "big-btn"
            :on-click (fn [] (forward! (-> @db :future last)))} ""
           [:img {:src "/icons/skip-forward.png"}] " Forward"]
          [:button {:class "big-btn"}
           [:a {:href "/api/rss/recentchanges"} [:img {:src "/icons/rss.png"}]]]]
         [:div {:id "nav3"}
          [nav-input current]
          [:button
           {:class    "big-btn"
            :on-click (fn [] (go-new! @current))}
           ;[:img {:src "/icons/arrow-right.png"}]
           " Go!"]
          [:button
           {:class "big-btn"
            :on-click
            (fn []
              (let [code (-> @current str)
                    result (sci/eval-string code)]
                (prepend-transcript! code result)))}
           "Execute"]
          [:button
           {:class "big-btn"
            :on-click
            (fn []
              (search-text! (-> @current str)))}
           "Search"]]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This function embed-boilerplate is a copy of the function in common.cljc

;; But for some reason, when we try to include it from common here
;; it fails on Chrome on my Android tablet.
;; It works fine on Chromium on desktop, and on Firefox on the same Android tablet
;; But Chrome on tablet won't work for any of these boilerplate strings.

;; However, the same code works fine on Chrome on Android
;; when we use a local copy of embed-boilerplate here in this file (client.cljs)

;; VERY MYSTERIOUS
;;

(defn embed-boilerplate [type]
  (condp = type
    :markdown
    "
----

"
    :youtube
    "
----
:embed

{:type :youtube
:url \"URL GOES HERE\"
:title \"\"
:caption \"\"
}

"
    :vimeo
    "
----
:embed

{:type :vimeo
:url \"URL GOES HERE\"
:title \"\"
:caption \"\"
}

"
    :media-img
    "
----
:embed

{:type :media-img
:url \"URL GOES HERE\"
:title \"\"
:caption \"\"
}
"
    :img
    "
----
:embed

{:type :img
:url \"URL GOES HERE\"
:title \"\"
:caption \"\"
}
"
    :soundcloud
    "
----
:embed

{:type :soundcloud
:url \"URL GOES HERE\"
:title \"\"
:caption \"\"

}


"
    :bandcamp
    "
----
:embed

{:type :bandcamp
:id IDHERE
:url \"URL GOES HERE\"
:description \"DESCRIPTION GOES HERE\"
:title \"\"
:caption \"\"

}

"
    :twitter
    "
----
:embed

{:type :twitter
:url \"URL GOES HERE\"
:title \"\"
:caption \"\"
}

"
    :codepen
    "
----
:embed

{:type :codepen
:url \"URL GOES HERE\"
:title \"\"
:caption \"\"
}

"
    :rss
    "
----
:embed

{:type :rss
:url \"URL GOES HERE\"
:caption \"\"
:title \"\"}
"
    :oembed
    "
----
:embed

{:type :oembed
:url \"URL GOES HERE\"
:api \"API ENDPOINT
:title \"\"
:caption \"\"}
"
    (str "
----

NO BOILERPLATE FOR EMBED TYPE " type
         "
         ----
         ")))

(defn pastebar []
  [:div {:class "pastebar"}
   [:div
    "Quick Paste Bar"]
   [:div

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :markdown)))}
     "New Card"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! "
----
:system

{:command :search
 :query \"\"
}

----"))}
     "Search Card"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! "
----
:workspace

;; Write some code
[:div
(str \"Hello Teenage America\")
]

----"))}
     "Code Workspace"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! "
----
:evalmd

;; Write some code.
;; Note that if the result of your executed code is a number
;; You must convert it to a string.

(str \"### \" (+ 1 2 3))

"))}
     "Code on Server"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :youtube)))}
     "YouTube Card"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :vimeo)))}
     "Vimeo Card"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :img)))}
     "Image Card"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :soundcloud)))}
     "SoundCloud Card"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :bandcamp)))}
     "BandCamp Card"]


    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :twitter)))}
     "Twitter Card"]

    [:button {:class "big-btn"
              :on-click
              (fn [e]
                (insert-text-at-cursor! (embed-boilerplate :rss)))}
     "RSS Feed"]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tool-bar []
  (fn []
    (let [mode (-> @db :mode)]
      [:div
       (condp = mode

         :editing
         [:div
          [:div
           [:span
            [:button {:class "big-btn"
                      :on-click
                      (fn []
                        (do
                          (swap! db assoc :mode :viewing)
                          (reload!)))}
             [:img {:src "/icons/x.png"}] " Cancel"]
            [:button {:class "big-btn"
                      :on-click
                      (fn []
                        (do
                          (swap! db assoc :mode :viewing)
                          (save-page!)))}
             [:img {:src "/icons/save.png"}] " Save"]]]
          (pastebar)]

         :viewing
         [:span
          [:button {:class "big-btn"
                    :on-click
                    #(swap! db assoc :mode :editing)}
           [:img {:src "/icons/edit.png"}] " Edit"]
          [:button {:class "big-btn"}
           [:a {:href (str "/api/exportpage?page=" (-> @db :current-page))}
            [:img {:src "/icons/package.png"}]
            " Export"]]]
         :transcript
         [:span
          [:button {:class "big-btn"
                    :on-click
                    #(swap! db assoc :mode :viewing)}
           [:img {:src "/icons/x.png"}] " Return"]])])))

(defn not-blank? [card]
  (not= "" (str/trim (get card "source_data"))))

(defn card->html [card]
  (-> (get card "server_prepared_data")
      (string->html)))

(defn send-to-input-box [value]
  [:input {:type      "text"
           :id        "sendto-inputbox"
           :value     @value
           :on-change #(reset! value (-> % .-target .-value))}])

(defn card-send-to-page! [page-name hash new-page-name]
  (http-post
    "/api/movecard"
    (fn [_] (go-new! new-page-name))
    (pr-str {:from page-name
             :to   new-page-name
             :hash hash})))

(defn card-bar [card]
  (let [meta-id (str "cardmeta" (get card "hash"))
        state (r/atom {:toggle "none"})
        sendval (r/atom "")
        toggle! (fn [e]
                  (do
                    (if (= (-> @state :toggle) "none")
                      (swap! state #(conj % {:toggle "block"}))
                      (swap! state #(conj % {:toggle "none"})))))]
    (fn [card]
      [:div {:class :card-meta}
       [:div
        [:span {:on-click (fn [e] (card-reorder!
                                    (-> @db :current-page)
                                    (get card "hash")
                                    "up"))}
         [:img {:src "/icons/chevrons-up.png"}]]
        [:span {:on-click (fn [e] (card-reorder!
                                    (-> @db :current-page)
                                    (get card "hash")
                                    "down"))}
         [:img {:src "/icons/chevrons-down.png"}]]
        [:span {:on-click toggle! :style {:size "smaller" :float "right"}}
         (if (= (-> @state :toggle) "none")
           [:img {:src "/icons/eye.png"}]
           [:img {:src "/icons/eye-off.png"}])]]
       [:div {:id meta-id :style {:spacing-top "5px" :display (-> @state :toggle)}}
        [:div [:h4 "Card Bar"]]
        [:div
         [:span "ID: " (get card "id")] " | Hash: "
         [:span (get card "hash")] " | Source type: "
         [:span (get card "source_type")] " | Render type: "
         [:span (get card "render_type")]]
        [:div
         [:span
          "Send to Another Page : "
          [send-to-input-box sendval]
          [:input {:name "hash" :id "sendhash" :type "hidden" :value (get card "hash")}]
          [:input {:name "from" :id "sendcurrent" :type "hidden" :value (-> @db :current-page)}]
          [:img {:src "/icons/send.png"}]
          [:button {:on-click
                    (fn [e]
                      (card-send-to-page!
                        (-> @db :current-page)
                        (get card "hash")
                        @sendval))} "Send"]]]]])))

(def default-ace-options {:fontSize "1.6rem"})
(def ace-theme "ace/theme/cloud9_day")
(def ace-mode-clojure "ace/mode/clojure")
(def ace-mode-markdown "ace/mode/markdown")

(defn configure-ace-instance!
  ([ace-instance mode]
   (configure-ace-instance! ace-instance mode default-ace-options))
  ([ace-instance mode options]
   (let [ace-session (.getSession ace-instance)]
     (.setTheme ace-instance ace-theme)
     (.setOptions ace-instance (clj->js options))
     (.setMode ace-session mode))))

(defn workspace [card]
  (let [state (r/atom {:code-toggle   true
                       :calc-toggle   false
                       :result-toggle true
                       :code          (get card "server_prepared_data")
                       :calc          []
                       :result        ""
                       :editor        (atom nil)})
        toggle-code! (fn [_e] (swap! state #(conj % {:code-toggle (-> @state :code-toggle not)})))
        toggle-calc! (fn [_e] (swap! state #(conj % {:calc-toggle (-> @state :calc-toggle not)})))
        toggle-result! (fn [_e] (swap! state #(conj % {:result-toggle (-> @state :result-toggle not)})))
        display (fn [d] (if d "block" "none"))
        execute-code (fn [_e] (let [code (.getValue (:editor @state))
                                    result
                                    (sci/eval-string
                                      code
                                      {:bindings {'replace replace}
                                       :classes  {'js    goog/global
                                                  :allow :all}})]
                                (swap! state #(conj % {:calc result :result result}))))]
    (reagent.core/create-class
      {:component-did-mount    (fn [_this] (let [editor-element (first (array-seq (.getElementsByClassName js/document "workspace-editor")))
                                                 ace-instance (.edit js/ace editor-element)]
                                             (configure-ace-instance! ace-instance ace-mode-clojure)
                                             (swap! state assoc :editor ace-instance)))
       :component-will-unmount (fn [_this]
                                 (let [editor (:editor @state)]
                                   (when editor
                                     (.destroy editor))))
       :reagent-render         (fn [_card]
                                 (let []
                                   [:div {:class :workspace}
                                    [:h3 "Workspace"]
                                    [:p {:class :workspace-note} [:i "Note : this is a ClojureScript workspace based on "
                                                                  [:a {:href "https://github.com/borkdude/sci"} "SCI"]
                                                                  ". Be aware that it does not save any changes you make in the textbox.

                                                             You'll need to  edit the page fully to make permanent changes to the code. "]]
                                    [:div {:class :workspace-buttons}
                                     [:button {:class :workspace-button :on-click execute-code} "Run"]
                                     [:button {:class :workspace-button :on-click toggle-code!} "Code"]
                                     [:button {:class :workspace-button :on-click toggle-calc!} "Calculated"]
                                     [:button {:class :workspace-button :on-click toggle-result!} "Output"]]
                                    [:div {:class :code :style {:padding "3px"
                                                                :display (display (-> @state :code-toggle))}}
                                     [:h4 "Source"]
                                     [:div {:class ["workspace-editor"]} (str/trim (-> @state :code))]]
                                    [:div {:class :calculated-out :style {:padding "3px"
                                                                          :display (display (-> @state :calc-toggle))}}
                                     [:h4 "Calculated"]
                                     [:pre
                                      (with-out-str (pprint (str (-> @state :calc))))]]
                                    [:div {:class :results :style {:padding "3px"
                                                                   :display (display (-> @state :result-toggle))}}
                                     [:h4 "Result"]
                                     [:div
                                      (let [result (-> @state :result)]
                                        (cond

                                          (number? result)
                                          (str result)

                                          (string? result)
                                          (if (= (first result) \<)
                                            [:div {:dangerouslySetInnerHTML {:__html result}}]
                                            result)

                                          (= (first result) :div)
                                          result

                                          :else
                                          (str result)))]]]))})))

(defn on-click-for-links [e]
  (let [tag (-> e .-target)
        classname (.getAttribute tag "class")
        data (.getAttribute tag "data")
        x (-> @db :dirty)]
    (if (= classname "wikilink")
      (go-new! data))))

(defn one-card []
  (let [inner-html (fn [s] [:div {:dangerouslySetInnerHTML {:__html s}}])
        state2 (r/atom {:toggle "block"})
        toggle! (fn [_]
                  (if (= (-> @state2 :toggle) "none")
                    (swap! state2 #(conj % {:toggle "block"}))
                    (swap! state2 #(conj % {:toggle "none"}))))]
    (fn [card]
      (let [rtype (get card "render_type")
            data (get card "server_prepared_data")
            inner (condp = rtype

                    ":code"
                    {:reagent-render (fn [_] (inner-html (str "<code>" data "</code>")))}

                    ":raw"
                    {:reagent-render (fn [_] (inner-html (str "<pre>" data "</pre>")))}

                    ":markdown" {:reagent-render      (fn [_] (inner-html (card->html card)))
                                 :component-did-mount (fn [_] (.highlightAll js/hljs))}

                    ":manual-copy"
                    {:reagent-render (fn [_] (inner-html
                                               (str "<div class='manual-copy'>"
                                                    (card->html card)
                                                    "</div>")))}

                    ":html"
                    {:reagent-render (fn [_] (inner-html (str data)))}

                    ":stamp"
                    {:reagent-render (fn [_] (inner-html (str data)))}

                    ":hiccup"
                    {:reagent-render (fn [_] "THIS SHOULD BE HICCUP RENDERED")}

                    ":workspace"
                    {:reagent-render (fn [_] [workspace card])}

                    (str "UNKNOWN TYPE ( " rtype " ) " data))
            component (reagent.core/create-class inner)]
        [:div {:class :card-outer}
         [:div {:class :card-meta}
          [:span {:on-click toggle! :style {:size "smaller" :float "right"}}
           (if (= (-> @state2 :toggle) "none")
             [:img {:src "/icons/maximize-2.svg"}]
             [:img {:src "/icons/minimize-2.svg"}])]]
         [:div
          {:style {:spacing-top "5px"
                   :display     (-> @state2 :toggle)}}
          [:div
           {:class    "card"
            :on-click on-click-for-links}
           [component]]]
         [card-bar card]]))))

(defn card-list []
  (reagent.core/create-class
    {:component-did-mount
     (fn [_this] (let [set-key (fn [card] (assoc card :key (random-uuid)))
                       cards (->> (:cards @db) (mapv set-key))
                       system-cards (->> (:system-cards @db) (mapv set-key))]
                   (swap! db assoc :cards cards)
                   (swap! db assoc :system-cards system-cards)))

     :reagent-render
     (fn [_this]
       (let [key-fn (fn [card] (or (get card "hash") (:key card)))]
         [:div
          [:div
           (try
             (let [cards (-> @db :cards)]
               (for [card (filter not-blank? cards)]
                 (try
                   [:div {:key (key-fn card)} [one-card card]]
                   (catch :default e
                     [:div {:class :card-outer}
                      [:div {:class "card"}
                       [:h4 "Error"]
                       (str e)]]))))
             (catch :default e
               (do
                 (js/console.log "ERROR")
                 (js/console.log (str e))
                 (js/alert e))))]
          [:div
           (try
             (let [cards (-> @db :system-cards)]
               (for [card cards]
                 [:div {:key (key-fn card)} [one-card card]]))
             (catch :default e
               (js/alert e)))]]))}))

(defn transcript []
  [:div {:class                   "transcript"
         :dangerouslySetInnerHTML {:__html (-> @db :transcript)}
         :on-click                on-click-for-links}])

(defn editor-component []
  (reagent.core/create-class
    {:component-did-mount    (fn [_this] (let [editor-element (first (array-seq (.getElementsByClassName js/document "edit-box")))
                                               ace-instance (.edit js/ace editor-element)]
                                           (configure-ace-instance! ace-instance ace-mode-markdown {:fontSize "1.2rem"})
                                           (swap! db assoc :ace-instance ace-instance)))
     :component-will-unmount (fn [_this]
                               (let [editor (:editor @db)]
                                 (when editor
                                   (.destroy editor))))
     :reagent-render         (fn [_] [:div {:class ["edit-box"]
                                            :on-key-up
                                            (fn [e]
                                              (let [kc (.-keyCode e)
                                                    escape-code 27]
                                                (when (and (= (-> @db :mode) :editing)
                                                           (= kc escape-code))
                                                  (swap! db assoc :mode :viewing))

                                                (when ())
                                                ))} (:raw @db)])}))

(defn main-container []
  [:div
   [:div
    (condp = (-> @db :mode)

      :editing
      [editor-component]

      :viewing
      [:div {:on-double-click (fn [] (swap! db assoc :mode :editing))}
       [card-list]]

      :transcript
      [:div
       [transcript]])]])

(defn bookmarklet-footer-link []
  (let [port (:port @db)
        document-url js/document.URL
        url (str "http://localhost:" port "/api/bookmarklet?url=" document-url)]
    [:a {:href url} "Bookmark to this Wiki"]))

;; Main Page

; Main page
(defn content []
  [:div {:class "main-container"}
   [:div {:class "headerbar"}
    [:div
     [:div [nav-bar]]]]
   [:div {:class "context-box"}
    [:h2
     (if (= (-> @db :mode) :transcript)
       "Transcript"
       [:span
        (-> @db :current-page)
        [:span {:class "tslink"}
         [:a {:href (str
                      (str/replace (-> @db :site-url) #"/$" "")
                      "/" (-> @db :current-page))} " (public)"]]])]
    [:div [tool-bar]]
    [main-container]]
   [:div {:class "footer"}
    [:span
     [:span "This " (-> @db :wiki-name) " wiki!"]
     [:span " || Home : " [:a {:href (-> @db :site-url)} (-> @db :site-url)] " || "]
     [:span [:a {:href "/api/system/db"} "DB"] " || "]
     [:a {:href "https://github.com/interstar/cardigan-bay"} "Cardigan Bay "]
     "(c) Phil Jones 2020-2022  || "
     [:span "IP: " (str (-> @db :ip)) " || "]
     [bookmarklet-footer-link]]]])

;; tells reagent to begin rendering
(dom/render [content] (.querySelector js/document "#app"))
