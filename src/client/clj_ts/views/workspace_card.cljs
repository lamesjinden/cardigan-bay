(ns clj-ts.views.workspace-card
  (:require [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [reagent.core :as r]
            [sci.core :as sci]
            [clj-ts.ace :as ace]
            [cljfmt.core :as format]
            [clj-ts.handle :as handle]))

(defn execute-code [state]
  (let [code (.getValue (:editor @state))
        result (sci/eval-string
                 code
                 {:bindings {'replace replace}
                  :classes  {'js    goog/global
                             :allow :all}})]
    (swap! state #(conj % {:calc result :result result}))))

(defn toggle-code! [state]
  (swap! state #(conj % {:code-toggle (-> @state :code-toggle not)})))

(defn toggle-calc! [state]
  (swap! state #(conj % {:calc-toggle (-> @state :calc-toggle not)})))

(defn toggle-result! [state]
  (swap! state #(conj % {:result-toggle (-> @state :result-toggle not)})))

(defn ->display [d]
  (if d
    "block"
    "none"))

(defn format-workspace [state]
  (let [editor (:editor @state)
        code (.getValue editor)
        formatted (format/reformat-string code)]
    (.setValue editor formatted)))

(defn save-code-async! [db state]
  (handle/save-card-async! (-> @db :current-page)
                            (-> @state :hash)
                            (-> @state :source_type)
                            (-> @state :code)))

(defn workspace [db card]
  (let [state (r/atom {:code-toggle   true
                       :calc-toggle   false
                       :result-toggle true
                       :code          (get card "server_prepared_data")
                       :calc          []
                       :result        ""
                       :hash          (get card "hash")
                       :source_type   (get card "source_type")
                       :editor        (atom nil)})]
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
                                     [:button {:class :workspace-button :on-click (fn [] (execute-code state))} "Run"]
                                     [:button {:class :workspace-button :on-click (fn [] (save-code-async! db state))} "Save Changes"]]
                                    [:div
                                     [:button {:class :workspace-button :on-click (fn [] (toggle-code! state))} "Code"]
                                     [:button {:class :workspace-button :on-click (fn [] (toggle-calc! state))} "Calculated"]
                                     [:button {:class :workspace-button :on-click (fn [] (toggle-result! state))} "Output"]]
                                    [:div {:class :code
                                           :style {:padding "3px"
                                                   :display (->display (-> @state :code-toggle))}}
                                     [:h4 "Source"
                                      [:span
                                       [:button
                                        {:class    :workspace-button
                                         :on-click (fn [] (format-workspace state))}
                                        "Format"]]]
                                     [:div {:class ["workspace-editor"]} (str/trim (-> @state :code))]]
                                    [:div {:class :calculated-out :style {:padding "3px"
                                                                          :display (->display (-> @state :calc-toggle))}}
                                     [:hr]
                                     [:h4 "Calculated"]
                                     [:pre
                                      (with-out-str (pprint (str (-> @state :calc))))]]
                                    [:div {:class :results :style {:padding "3px"
                                                                   :display (->display (-> @state :result-toggle))}}
                                     [:hr]
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
