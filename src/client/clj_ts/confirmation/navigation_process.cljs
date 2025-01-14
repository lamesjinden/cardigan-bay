(ns clj-ts.confirmation.navigation-process
  (:require [cljs.core.async :as a]
            [clj-ts.events.confirmation :as e-confirm]
            [clj-ts.http :as http]))

(defn- update-edit-sessions [editing {:keys [id action]}]
  (if (= action :start)
    (conj editing id)
    (disj editing id)))

(defn <create-nav-process [navigating$ editing$]
  (let [do-post (fn [page-name]
                  (http/<http-post "/api/page"
                                   (->> {:page_name page-name}
                                        (clj->js)
                                        (.stringify js/JSON))
                                   {:headers {"Content-Type" "application/json"}}))]
    (a/go-loop [editing #{}]
               (let [[value channel] (a/alts! [navigating$ editing$])]
                 (condp = channel
                   navigating$ (let [{:keys [page-name out-chan]} value]
                                 (if (empty? editing)
                                   (let [{:keys [isSuccess] :as result} (a/<! (do-post page-name))]
                                     (if isSuccess
                                       (a/put! out-chan result)
                                       (a/close! out-chan))
                                     (recur #{}))
                                   (let [confirm$ (e-confirm/<notify-confirm)
                                         response (a/<! confirm$)]
                                     (if (= response :ok)
                                       (let [{:keys [isSuccess] :as result} (a/<! (do-post page-name))]
                                         (if isSuccess
                                           (a/put! out-chan result)
                                           (a/close! out-chan))
                                         (recur #{}))
                                       (recur editing)))))
                   editing$ (recur (update-edit-sessions editing value)))))))
