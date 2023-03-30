(ns clj-ts.views.inner-html-card)

(defn inner-html [s]
  [:div {:dangerouslySetInnerHTML {:__html s}}])