(ns clj-ts.navigation
  (:require [clojure.string :as str]
            [promesa.core :as p]
            [clj-ts.http :as http]))

;; region load page

(defn load-page [db body]
  (let [edn (js->clj body)
        source-page (get edn "source_page")
        server-prepared-page (get edn "server_prepared_page")
        raw (get source-page "body")
        page-name (get source-page "page_name")
        cards (get server-prepared-page "cards")
        system-cards (get server-prepared-page "system_cards")
        site-url (get server-prepared-page "site_url")
        wiki-name (get server-prepared-page "wiki_name")
        start-page-name (get server-prepared-page "start_page_name")
        nav-links (get server-prepared-page "nav-links")]
    (swap! db assoc
           :current-page page-name
           :site-url site-url
           :wiki-name wiki-name
           :start-page-name start-page-name
           :raw raw
           :cards cards
           :system-cards system-cards
           :nav-links nav-links
           :mode :viewing
           :card-list-expanded-state :expanded)))

(defn- load-page-async! [db page-name]
  (let [query (->> {:page_name page-name}
                   (clj->js)
                   (.stringify js/JSON))]
    (-> (http/http-post-async "/api/page" query {:headers {"Content-Type" "application/json"}})
        (p/then (fn [{body-text :body}]
                  (let [body (.parse js/JSON body-text)]
                    (load-page db body))))
        (p/then (fn [_] (js/window.scroll 0 0))))))

(defn load-init-async! [db]
  (-> (http/http-get-async "/api/init")
      (p/then (fn [{body-text :body}]
                (let [body (.parse js/JSON body-text)]
                  (load-page db body))))))

(defn reload-async! [db]
  (load-page-async! db (:current-page @db)))

(defn- go-new-async! [db page-name]
  (-> (load-page-async! db page-name)
      (p/then (fn [] (swap! db assoc :mode :viewing)))))

(defn load-start-page-async! [db]
  (load-init-async! db))

;; endregion

;; region history

(defn page-name->url [page-name]
  (if (= "/" page-name)
    "/"
    (str "/pages/" page-name)))

(defn- get-pathname [] (-> js/window .-location .-pathname))

(defn- pathname->url
  ([pathname]
   (let [url (if (= "/" pathname)
               "/"
               (let [split (str/split pathname #"/")]
                 (str "/pages/" (last split))))]
     url))
  ([] (pathname->url (get-pathname))))

(defn- pathname->page-name
  ([pathname]
   (let [page-name (if (= "/" pathname)
                     "/"
                     (let [split (str/split pathname #"/")]
                       (last split)))]
     page-name))
  ([] (pathname->page-name (get-pathname))))

(defn- popstate->page-name [db popstate]
  (let [page-name (aget popstate "page-name")
        page-name (if (or (= "/" page-name) (= "index.html" page-name))
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

;; region public history api

(defn hook-pop-state [db]
  (js/window.addEventListener "popstate" (fn [e]
                                           (let [state (.-state e)]
                                             (pop-state-handler db state)))))

(defn replace-state-initial []
  (let [pathname (get-pathname)]
    (if (= "/index.html" pathname)
      (replace-state {:page-name "index.html"} pathname)
      (let [url (pathname->url pathname)
            page-name (pathname->page-name pathname)
            state {:page-name page-name}]
        (replace-state state url)))))

;; endregion

;; region links

(defn- navigate-to [page-name]
  (let [url (page-name->url page-name)
        state {:page-name page-name}]
    (push-state state url)))

(defn navigate-async! [db page-name]
  (-> (go-new-async! db page-name)
      (p/then (fn [] (navigate-to page-name)))))

(defn on-link-clicked [db e target aux-clicked?]
  (.preventDefault e)
  (cond
    (or (.-ctrlKey e) aux-clicked?)
    (js/window.open (page-name->url target))

    :else
    (navigate-async! db target)))

;; endregion
