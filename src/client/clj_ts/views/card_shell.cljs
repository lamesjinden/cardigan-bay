(ns clj-ts.views.card-shell
  (:require [reagent.core :as r]
            [clj-ts.ace :as ace]
            [clj-ts.keyboard :as keyboard]
            [clj-ts.theme :as theme]
            [clj-ts.card :refer [has-link-target? navigate-via-link-async!]]
            [clj-ts.view :refer [->display]]
            [clj-ts.views.card-bar :refer [card-bar]]
            [clj-ts.views.paste-bar :refer [paste-bar]]))

(defn expanded? [local-db]
  (:toggle @local-db))

(defn collapsed? [local-db]
  (not (expanded? local-db)))

(defn editing? [local-db]
  (= :editing (:mode @local-db)))

(defn viewing? [local-db]
  (= :viewing (:mode @local-db)))

(defn toggle-local-expanded-state! [local-db e]
  (swap! local-db update :toggle not)
  (.preventDefault e))

(defn enter-edit-mode! [local-db]
  (when (not= :editing (:mode @local-db))
    (swap! local-db assoc :mode :editing)))

;; region single-card editor

(defn- setup-editor [db local-db]
  (let [editor-element (:editor-element @local-db)
        ace-instance (.edit js/ace editor-element)
        ace-options (assoc ace/default-ace-options :maxLines "Infinity")
        theme (if (theme/light-theme? db) ace/ace-theme ace/ace-theme-dark)]
    (ace/configure-ace-instance! ace-instance ace/ace-mode-markdown theme ace-options)
    (.focus ace-instance)
    (swap! local-db assoc :editor ace-instance)

    ;; note - tracking over 2 ratoms causes the subscribe function to be invoked continuously;
    ;;        to compensate, tracking declaration closes over the editor instance instead of deref'ing it.
    (let [tracking (r/track! (fn []
                               (if (theme/light-theme? db)
                                 (ace/set-theme! ace-instance ace/ace-theme)
                                 (ace/set-theme! ace-instance ace/ace-theme-dark))))]
      (swap! local-db assoc :tracking tracking))))

(defn- destroy-editor [local-db]
  (let [editor (:editor @local-db)]
    (when editor
      (.destroy editor))))

(defn- single-editor [db local-db]
  (r/create-class
    {:component-did-mount    (fn [] (setup-editor db local-db))
     :component-will-unmount (fn []
                               (destroy-editor local-db)
                               (when-let [tracking (:tracking @local-db)]
                                 (r/dispose! tracking)))
     :reagent-render         (fn []
                               [:<>
                                [paste-bar db local-db]
                                [:div.edit-box-single {:ref         (fn [element] (swap! local-db assoc :editor-element element))
                                                       :on-key-down (fn [e] (keyboard/single-editor-on-key-down db local-db e))
                                                       :on-key-up   (fn [e] (keyboard/single-editor-on-key-up local-db e))}
                                 (get (:card @local-db) "source_data")]])}))

;; endregion

(defn card-shell [db card component]
  (let [local-db (r/atom {:toggle         true
                          :mode           :viewing
                          :current-page   (:current-page @db)
                          :card           card
                          :editor-element nil
                          :editor         nil})
        editable? (get card "user_authored?")]

    ; listen for global expanded state changes and set local-db accordingly
    ; note: local state can still be updated via toggle-local-expanded-state!
    ; todo - dispose of track! return value]
    (reagent.core/track! (fn [] (swap! local-db assoc :toggle (= :expanded (:card-list-expanded-state @db)))))
    (fn [db card component]
      [:div.card-shell
       (if (viewing? local-db)
         [:article.card-outer {:on-double-click (fn [] (when editable? (enter-edit-mode! local-db)))}
          [:div.card-meta-parent
           [:div.card-meta
            [:span.toggle-container {:on-click (fn [e] (toggle-local-expanded-state! local-db e))}
             (if (collapsed? local-db)
               [:span {:class [:material-symbols-sharp :clickable]} "unfold_more"]
               [:span {:class [:material-symbols-sharp :clickable]} "unfold_less"])]]]
          [:div.card
           {:on-click (fn [e] (when (has-link-target? e)
                                (navigate-via-link-async! db e)))}
           [:div.card-parent {:class (when (collapsed? local-db) :collapsed)}
            [:div.card-child.container
             [component]]
            [:div.card-child.overlay {:style {:display (->display (collapsed? local-db))}}]]]
          [card-bar db card]]
         [:div.editor-container
          [single-editor db local-db]])])))
