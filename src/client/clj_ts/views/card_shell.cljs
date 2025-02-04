(ns clj-ts.views.card-shell
  (:require [clojure.core.async :as a]
            [reagent.core :as r]
            [clj-ts.card :as cards]
            [clj-ts.events.expansion :as e-expansion]
            [clj-ts.navigation :as nav]
            [clj-ts.view :refer [->display]]
            [clj-ts.views.card-bar :refer [card-bar]]
            [clj-ts.views.editor-single :refer [single-editor]]))

(defn expanded? [local-db]
  (= :expanded (:expanded-state @local-db)))

(defn collapsed? [local-db]
  (not (expanded? local-db)))

(defn editing? [local-db]
  (= :editing (:mode @local-db)))

(defn viewing? [local-db]
  (= :viewing (:mode @local-db)))

(defn toggle-local-expanded-state! [local-db e]
  (swap! local-db update :expanded-state {:expanded  :collapsed
                                          :collapsed :expanded})
  (.preventDefault e))

(defn enter-edit-mode! [local-db]
  (when (not= :editing (:mode @local-db))
    (swap! local-db assoc :mode :editing)))

(defn- on-link-clicked [db e aux-clicked?]
  (when-let [target (cards/wikilink-data e)]
    (nav/<on-link-clicked db e target aux-clicked?)))

(defn- on-card-double-clicked [local-db]
  (cond
    (collapsed? local-db)
    (swap! local-db assoc :expanded-state :expanded)

    (true? (:editable? @local-db))
    (enter-edit-mode! local-db)))

(defn card-shell [db card component]
  (let [local-db (r/atom {:expanded-state :expanded
                          :mode           :viewing
                          :card           card
                          :hash           (get card "hash")
                          :editor         nil
                          :editable?      (get card "user_authored?")})
        !editor-element (clojure.core/atom nil)
        rx-theme (r/cursor db [:theme])
        expanded$ (e-expansion/create-expansion$)]

    (a/go-loop []
               (when-some [expanded-state (a/<! expanded$)]
                 (swap! local-db assoc :expanded-state expanded-state))
               (recur))

    (fn [db card component]
      [:div.card-shell
       (if (viewing? local-db)
         [:article.card-outer {:on-double-click (fn [] (on-card-double-clicked local-db))}
          [:div.card-meta-parent
           [:div.card-meta
            [:span.toggle-container {:on-click (fn [e] (toggle-local-expanded-state! local-db e))}
             (if (collapsed? local-db)
               [:span {:class [:material-symbols-sharp :clickable]} "unfold_more"]
               [:span {:class [:material-symbols-sharp :clickable]} "unfold_less"])]]]
          [:div.card
           {:on-click     (fn [e] (on-link-clicked db e false))
            :on-aux-click (fn [e] (on-link-clicked db e true))}
           [:div.card-parent {:class (when (collapsed? local-db) :collapsed)}
            [:div.card-child.container
             component]
            [:div.card-child.overlay {:style {:display (->display (collapsed? local-db))}}]]]
          [card-bar db card]]
         [:div.editor-container
          [single-editor db rx-theme local-db !editor-element]])])))
