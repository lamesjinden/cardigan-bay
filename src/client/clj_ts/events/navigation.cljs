(ns clj-ts.events.navigation
  (:require [clojure.string :as str]
            [promesa.core :as p]
            [clj-ts.http :as http]))

;; region load page

(defn load-page [db load-page-response]
  (let [edn (js->clj load-page-response)
        source-page (get edn "source_page")
        server-prepared-page (get edn "server_prepared_page")
        raw (get source-page "body")
        page-name (get source-page "page_name")
        cards (get server-prepared-page "cards")
        system-cards (get server-prepared-page "system_cards")
        site-url (get server-prepared-page "site_url")
        wiki-name (get server-prepared-page "wiki_name")
        port (get server-prepared-page "port")
        ip (get server-prepared-page "ip")
        start-page-name (get server-prepared-page "start_page_name")]
    (swap! db assoc
           :current-page page-name
           :site-url site-url
           :wiki-name wiki-name
           :port port
           :ip ip
           :start-page-name start-page-name
           :raw raw
           :cards cards
           :system-cards system-cards))
  ;; todo - move set mode to :viewing here - might be in upstream changes
  (js/window.scroll 0 0))

(defn load-page-async! [db page-name]
  (let [query (->> {:page_name page-name}
                   (clj->js)
                   (.stringify js/JSON))
        callback (fn [e] (let [load-page-response (-> e .-target .getResponseJson)]
                           (load-page db load-page-response)))]
    (http/http-post-async
      "/api/page"
      callback
      query
      {:headers {"Content-Type" "application/json"}})))

(defn load-init-async! [db]
  (let [callback (fn [e]
                   (let [load-init-response (-> e .-target .getResponseJson)]
                     (load-page db load-init-response)))]
    (http/http-get-async "/api/init" callback)))

(defn reload-async! [db]
  (load-page-async! db (:current-page @db)))

(defn go-new-async! [db page-name]
  (let [load-page-p (load-page-async! db page-name)]
    (p/then load-page-p
            (fn [] (swap! db assoc :mode :viewing)))))

(defn load-start-page-async! [db]
  (load-init-async! db))

;; endregion

;; region history

(defn- page-name->url [page-name]
  (if (= "/" page-name)
    "/"
    (str "/pages/" page-name)))

(defn- pathname->url []
  (let [pathname (-> js/window .-location .-pathname)
        url (if (= "/" pathname)
              "/"
              (let [split (str/split pathname #"/")]
                (str "/pages/" (last split))))]
    url))

(defn- pathname->page-name []
  (let [pathname (-> js/window .-location .-pathname)
        page-name (if (= "/" pathname)
                    "/"
                    (let [split (str/split pathname #"/")]
                      (last split)))]
    page-name))

(defn- popstate->page-name [db popstate]
  (let [page-name (aget popstate "page-name")
        page-name (if (= "/" page-name)
                    (:start-page-name @db)
                    page-name)]
    page-name))

(defn- replace-state
  ([state-map url]
   (js/history.replaceState (clj->js state-map) "" url))
  ([state-map]
   (replace-state state-map "")))

(defn- push-state
  ([state-map url]
   (js/history.pushState (clj->js state-map) "" url))
  ([state-map]
   (push-state state-map "")))

(defn- pop-state-handler [db state]
  (let [page-name (popstate->page-name db state)]
    (go-new-async! db page-name)))

;; endregion

;; public history api

(defn hook-pop-state [db]
  (js/window.addEventListener "popstate" (fn [e]
                                           (let [state (.-state e)]
                                             (pop-state-handler db state)))))

(defn navigate-to [page-name]
  (let [url (page-name->url page-name)
        state {:page-name page-name}]
    (push-state state url)))

(defn replace-state-initial []
  (let [url (pathname->url)
        page-name (pathname->page-name)
        state {:page-name page-name}]
    (replace-state state url)))

;; endregion