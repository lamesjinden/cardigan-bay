(ns clj-ts.views.confirmation-dialog
  (:require [cljs.core.async :as a]))

(defn confirmation-dialog [request-chan response-chan]
  (let [!dialog (clojure.core/atom nil)
        request-process (a/go-loop [] (when-some [_ (a/<! request-chan)]
                                        (.showModal @!dialog)
                                        (recur)))]
    (fn [request-chan response-chan]
      [:dialog#confirmation-dialog {:ref      (fn [element] (reset! !dialog element))
                                    :on-close (fn []
                                                (let [return-value (if (= "default" (.-returnValue @!dialog))
                                                                     :ok
                                                                     :cancel)]
                                                  (a/put! response-chan return-value)
                                                  (set! (.-returnValue @!dialog) nil)))}
       [:form
        [:p "You have unsaved changes. Do you want to discard them?"]
        [:div
         [:button {:value       "cancel"
                   :form-method "dialog"
                   :on-click    (fn [e] (.close @!dialog))}
          "Cancel"]
         [:button {:value    "default"
                   :on-click (fn [e]
                               (.preventDefault e)
                               (.close @!dialog "default"))}
          "OK"]]]])))