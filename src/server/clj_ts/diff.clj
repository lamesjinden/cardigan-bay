(ns clj-ts.diff)

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
