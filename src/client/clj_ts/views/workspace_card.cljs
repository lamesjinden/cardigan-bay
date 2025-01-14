(ns clj-ts.views.workspace-card
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [cljs.pprint :refer [pprint]]
            [reagent.core :as r]
            [sci.core :as sci]
            [cljfmt.core :as format]
            [clj-ts.ace :as ace]
            [clj-ts.card :as cards]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.theme :as theme]
            [clj-ts.view :refer [->display]]))

(defn eval-string [s]
  (try
    (let [opts {:classes    {'js js/globalThis :allow :all}
                :namespaces {'sci.core {'eval-string sci/eval-string}
                             'cb       {'get-element-by-id (fn [id] (js/document.getElementById id))}}}]
      (sci/eval-string s opts))
    (catch :default e
      (js/console.error e)
      (pr-str s))))

(defn eval-from-editor [state]
  (let [code (.getValue (:editor @state))
        result (eval-string code)]
    (swap! state #(conj % {:calc result :result result}))))

(defn eval-on-load [state]
  (let [code (:code @state)
        result (eval-string code)]
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

(defn- workspace-editor-on-key-s-press [db local-db e]
  (.preventDefault e)
  (let [current-hash (-> @local-db :hash)
        new-body (->> @local-db :editor (.getValue))]
    (cards/replace-card-async! db current-hash new-body)))

(defn- workspace-editor-on-key-down [db local-db e]
  (let [key-code (.-keyCode e)
        control? (.-ctrlKey e)]
    (when (and (= key-code keyboard/key-s-code)
               control?)
      (workspace-editor-on-key-s-press db local-db e))))

(defn- theme-tracker [db local-db]
  (ace/set-theme! (:editor @local-db)
                  (if (theme/light-theme? db)
                    ace/ace-theme
                    ace/ace-theme-dark)))

(defn- setup-editor [db local-db !editor-element]
  (let [editor-element @!editor-element
        ace-instance (.edit js/ace editor-element)
        max-lines (->> (:code-editor-size @local-db)
                       (get size->editor-max-lines))
        editor-options (assoc ace/default-ace-options :maxLines max-lines)
        theme (if (theme/light-theme? db) ace/ace-theme ace/ace-theme-dark)]
    (ace/configure-ace-instance! ace-instance ace/ace-mode-clojure theme editor-options)
    (swap! local-db assoc :editor ace-instance)))

(defn- destroy-editor [local-db]
  (when-let [editor (:editor @local-db)]
    (.destroy editor)))

(defn ->card-configuration
  "the card configuration is a map literal read from server_prepared_data.
   if the first form is not a map, returns nil."
  [server-prepared-data]
  (try
    (let [edn (-> server-prepared-data
                  (str/trim)
                  (edn/read-string))]
      (when (map? edn)
        edn))
    (catch :default _e
      nil)))

(defn workspace [db card]
  (let [server-prepared-data (get card "server_prepared_data")
        card-configuration (or (->card-configuration server-prepared-data) {})
        local-db (r/atom {:calc             []
                          :calc-toggle      (get card-configuration :calc-visibility false)
                          :code             server-prepared-data
                          :code-editor-size :small
                          :code-toggle      (get card-configuration :code-visibility true)
                          :editor           nil
                          :hash             (get card "hash")
                          :result           ""
                          :result-toggle    (get card-configuration :result-visibility false)
                          :source_type      (get card "source_type")})
        !editor-element (clojure.core/atom nil)
        track-theme (r/track! (partial theme-tracker db local-db))]

    (when (get card-configuration :eval-on-load)
      (eval-on-load local-db))

    (reagent.core/create-class

      {:component-did-mount    (fn []
                                 (setup-editor db local-db !editor-element))
       :component-will-unmount (fn []
                                 (destroy-editor local-db)
                                 (r/dispose! track-theme))
       :reagent-render         (fn []
                                 [:div.workspace
                                  [:div.workspace-header-container
                                   [:div.visibility-buttons
                                    [:button.big-btn.big-btn-left {:class    (when (-> @local-db :code-toggle) "pressed")
                                                                   :on-click (fn [] (toggle-code! local-db))}
                                     "SOURCE"]
                                    [:button.big-btn.big-btn-middle {:class    (when (-> @local-db :result-toggle) "pressed")
                                                                     :on-click (fn [] (toggle-result! local-db))}
                                     "RESULT"]
                                    [:button.big-btn.big-btn-right {:class    (when (-> @local-db :calc-toggle) "pressed")
                                                                    :on-click (fn [] (toggle-calc! local-db))}
                                     "CALCULATED"]]
                                   [:div]]

                                  [:div.code.workspace-padding
                                   ;; visibility controlled by style.display instead of 'when because the editor control needs to be initialized when (re)created
                                   [:div.code-section {:style {:display (->display (-> @local-db :code-toggle))}}
                                    [:div.code-section-header-container
                                     [:h4 "Source"]
                                     [:div.workspace-buttons
                                      [:button.big-btn.big-btn-left.lambda-button {:on-click (fn [] (eval-from-editor local-db))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "λ"]]
                                      [:button.big-btn.big-btn-middle {:on-click (fn [] (on-save-clicked db local-db))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "save"]]
                                      [:button.big-btn.big-btn-right {:on-click (fn [] (format-workspace local-db))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "format_align_justify"]]
                                      [:button.big-btn {:on-click (fn [] (resize-editor! db local-db))}
                                       [:span {:class [:material-symbols-sharp :clickable]} "expand"]]]]
                                    [:div.workspace-editor {:ref             (fn [element] (reset! !editor-element element))
                                                            :on-key-down     (fn [e] (workspace-editor-on-key-down db local-db e))
                                                            :on-double-click (fn [e] (.stopPropagation e))}
                                     (str/trim (-> @local-db :code))]]]

                                  (when (:calc-toggle @local-db)
                                    [:div.calculated-section
                                     [:h4 "Calculated"]
                                     [:pre {:style {:white-space "pre-wrap"}}
                                      (with-out-str (pprint (str (-> @local-db :calc))))]])

                                  (when (:result-toggle @local-db)
                                    [:div.result-section {:on-double-click (fn [e] (.stopPropagation e))}
                                     [:h4 "Result"]
                                     [:output
                                      (let [result (-> @local-db :result)]
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
                                          (str result)))]])])})))
