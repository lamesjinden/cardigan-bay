(ns clj-ts.http
  (:require
    [goog.string :as gstring]
    [goog.string.format])
  (:import [goog.net XhrIo]))

(goog-define env "production")
(goog-define env-port "")

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
              (gstring/format "//localhost:%s%s" env-port url)
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
