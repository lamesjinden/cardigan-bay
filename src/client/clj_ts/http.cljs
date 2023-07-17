(ns clj-ts.http
  (:require [promesa.core :as p])
  (:import [goog.net XhrIo]))

(goog-define env "production")
(goog-define env-port "")

;; region promise-based implementations

(defn http-send-async [{:keys [url method body headers timeout with-credentials?]
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
        deferred (p/deferred)
        callback (fn [e]
                   (let [response {:status     (.getStatus (.-target e))
                                   :statusText (.getStatusText (.-target e))
                                   :headers    (-> e (.-target) (.getResponseHeaders))
                                   :body       (-> e (.-target) (.getResponseText))}]
                     (if (.isSuccess (.-target e))
                       (p/resolve! deferred response)
                       (p/reject! deferred response))))]
    (.send XhrIo
           url
           callback
           method
           body
           (clj->js headers)
           timeout
           with-credentials?)
    deferred))

(defn http-get-async [url & {:keys [headers timeout with-credentials?]}]
  (http-send-async {:url               url
                    :method            "GET"
                    :headers           headers
                    :timeout           timeout
                    :with-credentials? with-credentials?}))

(defn http-post-async [url body & {:keys [headers timeout with-credentials?]}]
  (http-send-async {:url               url
                    :method            "POST"
                    :body              body
                    :headers           headers
                    :timeout           timeout
                    :with-credentials? with-credentials?}))

;; endregion


