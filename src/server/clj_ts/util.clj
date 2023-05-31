(ns clj-ts.util
  (:require [ring.util.response :as resp])
  (:import (java.io PrintWriter StringWriter)))

(defn exception-stack [e]
  (let [sw (new StringWriter)
        pw (new PrintWriter sw)]
    (.printStackTrace e pw)
    (str "Exception :: " (.getMessage e) (-> sw .toString))))

(defn create-not-found [uri-or-page-name]
  (-> (resp/not-found (str "Not found " uri-or-page-name))
      (resp/content-type "text")))

(defn create-ok []
  (-> "thank you"
      (resp/response)
      (resp/content-type "text/html")))

(defn ->html-response [html]
  (-> html
      (resp/response)
      (resp/content-type "text/html")))

(defn ->json-response [json]
  (-> json
      (resp/response)
      (resp/content-type "application/json")))
