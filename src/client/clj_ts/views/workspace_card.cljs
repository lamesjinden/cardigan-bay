(ns clj-ts.views.workspace-card
  (:require [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [reagent.core :as r]
            [sci.core :as sci]
            [clj-ts.ace :as ace]))

(defn workspace [card]
  (let [state (r/atom {:code-toggle   true
                       :calc-toggle   false
                       :result-toggle true
                       :code          (get card "server_prepared_data")
                       :calc          []
                       :result        ""
                       :editor        (atom nil)})
        toggle-code! (fn [_e] (swap! state #(conj % {:code-toggle (-> @state :code-toggle not)})))
        toggle-calc! (fn [_e] (swap! state #(conj % {:calc-toggle (-> @state :calc-toggle not)})))
        toggle-result! (fn [_e] (swap! state #(conj % {:result-toggle (-> @state :result-toggle not)})))
        display (fn [d] (if d "block" "none"))
        execute-code (fn [_e] (let [code (.getValue (:editor @state))
                                    result
                                    (sci/eval-string
                                      code
                                      {:bindings {'replace replace}
                                       :classes  {'js    goog/global
                                                  :allow :all}})]
                                (swap! state #(conj % {:calc result :result result}))))]
    (reagent.core/create-class
      {:component-did-mount    (fn [] (let [editor-element (first (array-seq (.getElementsByClassName js/document "workspace-editor")))
                                            ace-instance (.edit js/ace editor-element)]
                                        (ace/configure-ace-instance! ace-instance ace/ace-mode-clojure)
                                        (swap! state assoc :editor ace-instance)))
       :component-will-unmount (fn []
                                 (let [editor (:editor @state)]
                                   (when editor
                                     (.destroy editor))))
       :reagent-render         (fn []
                                 (let []
                                   [:div {:class :workspace}
                                    [:h3 "Workspace"]
                                    [:p {:class :workspace-note} [:i "Note : this is a ClojureScript workspace based on "
                                                                  [:a {:href "https://github.com/borkdude/sci"} "SCI"]
                                                                  ". Be aware that it does not save any changes you make in the textbox.

                                                             You'll need to  edit the page fully to make permanent changes to the code. "]]
                                    [:div {:class :workspace-buttons}
                                     [:button {:class :workspace-button :on-click execute-code} "Run"]
                                     [:button {:class :workspace-button :on-click toggle-code!} "Code"]
                                     [:button {:class :workspace-button :on-click toggle-calc!} "Calculated"]
                                     [:button {:class :workspace-button :on-click toggle-result!} "Output"]]
                                    [:div {:class :code :style {:padding "3px"
                                                                :display (display (-> @state :code-toggle))}}
                                     [:h4 "Source"]
                                     [:div {:class ["workspace-editor"]} (str/trim (-> @state :code))]]
                                    [:div {:class :calculated-out :style {:padding "3px"
                                                                          :display (display (-> @state :calc-toggle))}}
                                     [:h4 "Calculated"]
                                     [:pre
                                      (with-out-str (pprint (str (-> @state :calc))))]]
                                    [:div {:class :results :style {:padding "3px"
                                                                   :display (display (-> @state :result-toggle))}}
                                     [:h4 "Result"]
                                     [:div
                                      (let [result (-> @state :result)]
                                        (cond

                                          (number? result)
                                          (str result)

                                          (string? result)
                                          (if (= (first result) \<)
                                            [:div {:dangerouslySetInnerHTML {:__html result}}]
                                            result)

                                          (= (first result) :div)
                                          result

                                          :else
                                          (str result)))]]]))})))
