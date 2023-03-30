(ns clj-ts.highlight
  (:require [cljsjs.highlight]
            [cljsjs.highlight.langs.clojure]
            [cljsjs.highlight.langs.bash]))

(defn highlight-all []
  (.highlightAll js/hljs))
