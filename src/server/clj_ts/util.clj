(ns clj-ts.util
  (:require [ring.util.response :as resp]
            [sci.core :as sci])
  (:import (java.io PrintWriter StringWriter)))

;; Helpful for print debugging ... diffs two strings
(defn replace-whitespace [char]
  (if (Character/isWhitespace ^Character char)
    "_"
    (str char)))

(defn diff-strings [str1 str2]
  (let [len1 (count str1)
        len2 (count str2)
        min-len (min len1 len2)]
    (apply str
           (map (fn [ch1 ch2]
                  (if (= ch1 ch2)
                    (replace-whitespace ch1)
                    (str "[" (replace-whitespace ch1) (replace-whitespace ch2) "]")))
                (take min-len str1)
                (take min-len str2)))))

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

(defn server-eval
  "Evaluate Clojure code embedded in a card. Evaluated with SCI
   but on the server. I hope there's no risk for this ...
   BUT ..."
  [data]
  (let [code data
        evaluated (try
                    (#(apply str (sci/eval-string code)))
                    (catch Exception _ exception-stack))]
    evaluated))