(ns clj-ts.http
  (:require
    [promesa.core :as p]
    [goog.string :as gstring]
    [goog.string.format])
  (:import [goog.net XhrIo]))

(goog-define env "production")
(goog-define env-port "")

(defn http-send-async [{:keys [url callback method body headers timeout with-credentials?]
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

  (let [hostname (.-hostname js/location)
        url (if (= env "dev")
              (gstring/format "//%s:%s%s" hostname env-port url)
              url)
        ; replace callback with a function that resolves a promise
        ; return the resolved promise
        deferred (p/deferred)
        adapted-callback (fn [e]
                           (if (.isSuccess (.-target e))
                             (let [callback-result (callback e)]
                               (p/resolve! deferred callback-result))
                             (let [status {:status     (.getStatus (.-target e))
                                           :statusText (.getStatusText (.-target e))}]
                               (p/reject! deferred status))))]
    (.send XhrIo
           url
           adapted-callback
           method
           body
           (clj->js headers)
           timeout
           with-credentials?)
    deferred))

(defn http-get-async [url callback & {:keys [headers timeout with-credentials?]}]
  (http-send-async {:url               url
                    :callback          callback
                    :method            "GET"
                    :headers           headers
                    :timeout           timeout
                    :with-credentials? with-credentials?}))

(defn http-post-async [url callback body & {:keys [headers timeout with-credentials?]}]
  (http-send-async {:url               url
                    :callback          callback
                    :method            "POST"
                    :body              body
                    :headers           headers
                    :timeout           timeout
                    :with-credentials? with-credentials?}))
