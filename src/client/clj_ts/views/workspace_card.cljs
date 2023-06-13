(ns clj-ts.views.workspace-card
  (:require [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [reagent.core :as r]
            [sci.core :as sci]
            [clj-ts.ace :as ace]
            [cljfmt.core :as format]
            [clj-ts.page :as page]
            [clj-ts.navigation :as nav]
            [clj-ts.view :refer [->display]]
            [promesa.core :as p]))

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

(def size->editor-max-lines {:small  25
                             :medium 50
                             :large  "Infinity"})

(def ->next-size {:small  :medium
                  :medium :large
                  :large  :small})

(defn resize-editor! [state]
  (let [editor (:editor @state)
        next-size (->> (:code-editor-size @state)
                       (get ->next-size))
        editor-options (->> next-size
                            (get size->editor-max-lines)
                            (assoc {} :maxLines))]
    (swap! state assoc :code-editor-size next-size)
    (ace/configure-ace-instance! editor ace/ace-mode-clojure editor-options)))

(defn toggle-calc! [state]
  (swap! state #(conj % {:calc-toggle (-> @state :calc-toggle not)})))

(defn toggle-result! [state]
  (swap! state #(conj % {:result-toggle (-> @state :result-toggle not)})))

(defn format-workspace [state]
  (let [editor (:editor @state)
        code (.getValue editor)
        formatted (format/reformat-string code)]
    (.setValue editor formatted)))

(defn save-code-async! [db state]
  (let [editor-instance (:editor @state)
        editor-value (.getValue editor-instance)]
    (-> (page/save-card-async! (-> @db :current-page)
                               (-> @state :hash)
                               editor-value)
        (p/then (fn [_] (nav/reload-async! db))))))

(defn workspace [db card]
  (let [state (r/atom {:code-toggle      true
                       :code-editor-size :small
                       :calc-toggle      false
                       :result-toggle    true
                       :code             (get card "server_prepared_data")
                       :calc             []
                       :result           ""
                       :hash             (get card "hash")
                       :source_type      (get card "source_type")
                       :editor           (atom nil)})]
    (reagent.core/create-class
      {:component-did-mount    (fn [] (let [editor-element (first (array-seq (.getElementsByClassName js/document "workspace-editor")))
                                            ace-instance (.edit js/ace editor-element)
                                            editor-options (->> (:code-editor-size @state)
                                                                (get size->editor-max-lines)
                                                                (assoc {} :maxLines))]
                                        (ace/configure-ace-instance! ace-instance ace/ace-mode-clojure editor-options)
                                        (swap! state assoc :editor ace-instance)))
       :component-will-unmount (fn []
                                 (let [editor (:editor @state)]
                                   (when editor
                                     (.destroy editor))))
       :reagent-render         (fn []
                                 (let []
                                   [:div.workspace
                                    [:div.workspace-header-container
                                     [:h3.workspace-header "Workspace"]]
                                    [:div.workspace-note
                                     [:i "Note : this is a ClojureScript workspace based on "
                                      [:a {:href "https://github.com/borkdude/sci"} "SCI"]
                                      ". Be aware that it does not save any changes you make in the textbox. You'll need to  edit the page fully to make permanent changes to the code. "]]
                                    [:div.code.workspace-padding

                                     [:div.workspace-section
                                      [:h4 "Source"]
                                      [:span {:on-click (fn [] (toggle-code! state))
                                              :class    [:material-symbols-sharp :clickable]
                                              :style    {:display (->display (-> @state :code-toggle))}}
                                       "visibility_off"]
                                      [:span {:on-click (fn [] (toggle-code! state))
                                              :class    [:material-symbols-sharp :clickable]
                                              :style    {:display (->display (-> @state :code-toggle not))}}
                                       "visibility"]]
                                     [:div.code-section {:style {:display (->display (-> @state :code-toggle))}}
                                      [:div.workspace-buttons
                                       [:button.big-btn.big-btn-left.lambda-button {:on-click (fn [] (execute-code state))}
                                        [:span {:class [:material-symbols-sharp :clickable]} "λ"]]
                                       [:button.big-btn.big-btn-middle {:on-click (fn [] (save-code-async! db state))}
                                        [:span {:class [:material-symbols-sharp :clickable]} "save"]]
                                       [:button.big-btn.big-btn-right {:on-click (fn [] (format-workspace state))}
                                        [:span {:class [:material-symbols-sharp :clickable]} "format_align_justify"]]
                                       [:button.big-btn {:on-click (fn [] (resize-editor! state))}
                                        [:span {:class [:material-symbols-sharp :clickable]} "expand"]]]
                                      [:div.workspace-editor {:class [:workspace-editor]} (str/trim (-> @state :code))]]]

                                    [:div.workspace-section
                                     [:h4 "Calculated"]
                                     [:span {:on-click (fn [] (toggle-calc! state))
                                             :class    [:material-symbols-sharp :clickable]
                                             :style    {:display (->display (-> @state :calc-toggle))}} "visibility_off"]
                                     [:span {:on-click (fn [] (toggle-calc! state))
                                             :class    [:material-symbols-sharp :clickable]
                                             :style    {:display (->display (-> @state :calc-toggle not))}} "visibility"]]
                                    [:div.calculated-section {
                                                              :style {:display (->display (-> @state :calc-toggle))}}
                                     [:pre {:style {:white-space "pre-wrap"}}
                                      (with-out-str (pprint (str (-> @state :calc))))]]

                                    [:div.workspace-section
                                     [:h4 "Result"]
                                     [:span {:on-click (fn [] (toggle-result! state))
                                             :class    [:material-symbols-sharp :clickable]
                                             :style    {:display (->display (-> @state :result-toggle))}} "visibility_off"]
                                     [:span {:on-click (fn [] (toggle-result! state))
                                             :class    [:material-symbols-sharp :clickable]
                                             :style    {:display (->display (-> @state :result-toggle not))}} "visibility"]]
                                    [:div.result-section {:style {:display (->display (-> @state :result-toggle))}}
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
