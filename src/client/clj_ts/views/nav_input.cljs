(ns clj-ts.views.nav-input)

(defn nav-input [value]
  [:input {:type      "text"
           :id        "navinputbox"
           :value     @value
           :on-change #(reset! value (-> % .-target .-value))}])
