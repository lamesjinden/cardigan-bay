(ns clj-ts.views.app-progress-bar
  (:require [cljs.core.async :as a]
            [reagent.core :as r]
            [clj-ts.events.progression :as e-progress]))

(defn- ->% [x]
  (str x "%"))

(defn- <timeout [ms]
  (let [c (a/chan)]
    (js/setTimeout (fn [] (a/close! c)) ms)
    c))

(defn schedule-update [id completed]
  (a/go
    (a/<! (<timeout 1000))
    (e-progress/notify-progress-update id completed)))

(defn app-progress-bar [db progress$]
  (let [local-db (r/atom {:width   0
                          :opacity 0})]
    (a/go-loop [tasks {}]
               (when-some [message (a/<! progress$)]
                 (let [{:keys [id action]} message]
                   (recur
                     (condp = action
                       :start (let [starting-completed 10]
                                (swap! local-db assoc :opacity (->% 100) :width (->% starting-completed))
                                (swap! local-db dissoc :completed? :failed?)
                                (schedule-update id (inc starting-completed))
                                {id starting-completed})
                       :update (if-let [_current-completed (get tasks id)]
                                 (let [{completed :completed} message
                                       completed' (min completed 100)]
                                   (swap! local-db assoc :width (->% completed'))
                                   (schedule-update id (inc completed'))
                                   {id completed'})
                                 tasks)
                       :end (do
                              (swap! local-db assoc :completed? true)
                              {})
                       :fail (do
                               (swap! local-db assoc :width 0 :failed? true)
                               {})
                       tasks)))))

    (fn [db progress$]
      (let [completed? (:completed? @local-db)
            failed? (:failed @local-db)]

        [:span.progress-bar-outer {:class (cond
                                            completed? "completed"
                                            failed? "failed"
                                            :else "")}
         [:span.progress-bar-inner {:style (when-not (or completed? failed?)
                                             {:width (:width @local-db)})
                                    :class (cond
                                             completed? "completed"
                                             failed? "failed"
                                             :else "")}]]))))
