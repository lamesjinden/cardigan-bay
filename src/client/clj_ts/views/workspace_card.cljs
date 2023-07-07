(ns clj-ts.views.workspace-card
  (:require [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [reagent.core :as r]
            [sci.core :as sci]
            [cljfmt.core :as format]
            [clj-ts.ace :as ace]
            [clj-ts.card :as cards]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.theme :as theme]
            [clj-ts.view :refer [->display]]))

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

(defn resize-editor! [db state]
  (let [editor (:editor @state)
        next-size (->> (:code-editor-size @state)
                       (get ->next-size))
        editor-max-lines (get size->editor-max-lines next-size)
        editor-options (assoc ace/default-ace-options :maxLines editor-max-lines)
        theme (if (theme/light-theme? db) ace/ace-theme ace/ace-theme-dark)]
    (swap! state assoc :code-editor-size next-size)
    (ace/configure-ace-instance! editor ace/ace-mode-clojure theme editor-options)))

(defn toggle-calc! [state]
  (swap! state #(conj % {:calc-toggle (-> @state :calc-toggle not)})))

(defn toggle-result! [state]
  (swap! state #(conj % {:result-toggle (-> @state :result-toggle not)})))

(defn format-workspace [state]
  (let [editor (:editor @state)
        code (.getValue editor)
        formatted (format/reformat-string code)]
    (.setValue editor formatted)))

(defn- on-save-clicked [db state]
  (let [current-hash (-> @state :hash)
        new-body (-> @state :editor (.getValue))]
    (cards/replace-card-async! db current-hash new-body)))

(defn workspace [db db-cards card]
  (let [match (->> @db-cards
                   (filter #(= (:hash card) (:hash %)))
                   (first))
        state (r/atom {:code-toggle      true
                       :calc-toggle      false
                       :result-toggle    true
                       :calc             []
                       :result           ""
                       :editor           nil
                       :code-editor-size :small
                       :code             (get match "server_prepared_data")
                       :hash             (get match "hash")
                       :source_type      (get match "source_type")})
        tracking (reagent.core/track! (fn []
                                        (if (theme/light-theme? db)
                                          (ace/set-theme! (:editor @state) ace/ace-theme)
                                          (ace/set-theme! (:editor @state) ace/ace-theme-dark))))]
    (reagent.core/create-class
      {:component-did-mount    (fn [] (let [editor-element (first (array-seq (.getElementsByClassName js/document "workspace-editor")))
                                            ace-instance (.edit js/ace editor-element)
                                            max-lines (->> (:code-editor-size @state)
                                                           (get size->editor-max-lines))
                                            editor-options (assoc ace/default-ace-options :maxLines max-lines)
                                            theme (if (theme/light-theme? db) ace/ace-theme ace/ace-theme-dark)]
                                        (ace/configure-ace-instance! ace-instance ace/ace-mode-clojure theme editor-options)
                                        (swap! state assoc :editor ace-instance)))
       :component-will-unmount (fn []
                                 (let [editor (:editor @state)]
                                   (when editor
                                     (.destroy editor)))
                                 (r/dispose! tracking))
       :reagent-render         (fn []
                                 [:div.workspace
                                  [:div.workspace-header-container
                                   [:h3.workspace-header "Workspace"]
                                   [:div.visibility-buttons
                                    [:button.big-btn.big-btn-left {:class    (when (-> @state :code-toggle) "pressed")
                                                                   :on-click (fn [] (toggle-code! state))}
                                     "SOURCE"]
                                    [:button.big-btn.big-btn-middle {:class    (when (-> @state :result-toggle) "pressed")
                                                                     :on-click (fn [] (toggle-result! state))}
                                     "RESULT"]
                                    [:button.big-btn.big-btn-right {:class    (when (-> @state :calc-toggle) "pressed")
                                                                    :on-click (fn [] (toggle-calc! state))}
                                     "CALCULATED"]]
                                   [:div]]

                                  [:div.code.workspace-padding
                                   [:div.code-section {:style {:display (->display (-> @state :code-toggle))}}
                                    [:div.code-section-header-container
                                     [:h4 "Source"]
                                     [:div.workspace-buttons
                                      [:button.big-btn.big-btn-left.lambda-button {:on-click (fn [] (execute-code state))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "λ"]]
                                      [:button.big-btn.big-btn-middle {:on-click (fn [] (on-save-clicked db state))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "save"]]
                                      [:button.big-btn.big-btn-right {:on-click (fn [] (format-workspace state))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "format_align_justify"]]
                                      [:button.big-btn {:on-click (fn [] (resize-editor! db state))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "expand"]]]]
                                    [:div.workspace-editor {:class       [:workspace-editor]
                                                            :on-key-down (fn [e] (keyboard/workspace-editor-on-key-down db state e))}
                                     (str/trim (-> @state :code))]]]

                                  [:div.calculated-section {:style {:display (->display (-> @state :calc-toggle))}}
                                   [:h4 "Calculated"]
                                   [:pre {:style {:white-space "pre-wrap"}}
                                    (with-out-str (pprint (str (-> @state :calc))))]]

                                  [:div.result-section {:style {:display (->display (-> @state :result-toggle))}}
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
                                        (str result)))]]])})))
