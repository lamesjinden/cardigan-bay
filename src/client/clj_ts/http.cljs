(ns clj-ts.http
  (:require [cljs.core.async :as a]
            [clj-ts.events.progression :as e-progress])
  (:import [goog.net XhrIo]))

(goog-define env "production")
(goog-define env-port "")

(defn- ->progress-id [url method]
  (str url method))

(defn- <http-send [{:keys [url method body headers timeout with-credentials?]
                    :or   {body              nil
                           headers           nil
                           timeout           0
                           with-credentials? false}}]
  (when (not url)
    (throw (js/error "url was not defined")))
  (when (not method)
    (throw (js/error "method was not defined")))

  (let [hostname (.-hostname js/location)
        url (if (= env "dev")
              (str "//" hostname ":" env-port url)
              url)
        chan (a/promise-chan)
        callback (fn [e]
                   (if (.isSuccess (.-target e))
                     (do
                       (e-progress/notify-progress-end (->progress-id url method))
                       (a/put! chan {:isSuccess  true
                                     :status     (-> e (.-target) (.getStatus))
                                     :statusText (-> e (.-target) (.getStatusText))
                                     :headers    (-> e (.-target) (.getResponseHeaders))
                                     :body       (-> e (.-target) (.getResponseText))}))
                     (do
                       (e-progress/notify-progress-fail (->progress-id url method))
                       (a/put! chan {:isSuccess  false
                                     :status     (-> e (.-target) (.getStatus))
                                     :statusText (-> e (.-target) (.getStatusText))}))))]
    (e-progress/notify-progress-begin (->progress-id url method))
    (.send XhrIo
           url
           callback
           method
           body
           (clj->js headers)
           timeout
           with-credentials?)
    chan))

(defn <http-get [url & {:keys [headers timeout with-credentials?]}]
  (<http-send {:url               url
               :method            "GET"
               :headers           headers
               :timeout           timeout
               :with-credentials? with-credentials?}))

(defn <http-post [url body & {:keys [headers timeout with-credentials?]}]
  (<http-send {:url               url
               :method            "POST"
               :body              body
               :headers           headers
               :timeout           timeout
               :with-credentials? with-credentials?}))