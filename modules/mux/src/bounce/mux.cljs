(ns bounce.mux
  (:require [bounce.mux.page :as p]
            [clojure.string :as s]
            [bounce.core :as bc]
            [goog.History :as h]
            [goog.events :as e])

  (:import [goog.history Html5History]))

(defprotocol Router
  (set-location! [router location])
  (replace-location! [router location])
  (link [router location]))

(defprotocol TokenMapper
  (token->location [token-mapper token])
  (location->token [token-mapper location]))

(defn make-router [{:keys [token-mapper listener pages default-location]}]
  (let [history (doto (Html5History.)
                  (.setUseFragment false)
                  (.setPathPrefix ""))

        !current-location (atom nil)
        !current-page (atom nil)]

    (letfn [(on-change [new-location]
              (let [[old-location new-location] (loop []
                                                  (let [old-location @!current-location]
                                                    (if (compare-and-set! !current-location old-location new-location)
                                                      [old-location new-location]
                                                      (recur))))]
                (when (not= old-location new-location)
                  (if-let [new-page-fn (get pages (:handler new-location))]
                    (let [same-handler? (= (:handler old-location) (:handler new-location))]
                      (when-let [old-page @!current-page]
                        (p/-unmount! old-page (merge new-location
                                                     {:same-handler? same-handler?})))

                      (let [new-page (new-page-fn (merge old-location
                                                         {:same-handler? same-handler?}))]
                        (reset! !current-page new-page)
                        (listener (p/-value new-page))))

                    (js/console.warn "Can't find new handler:" (pr-str new-location))))))]

      (e/listen history h/EventType.NAVIGATE
                (fn [e]
                  (let [token (.-token e)
                        location (if-not (s/blank? token)
                                   token
                                   (or (location->token token-mapper default-location) "/"))]
                    (on-change (token->location token-mapper location)))))

      (.setEnabled history true)

      (bc/->component (reify Router
                        (set-location! [_ location]
                          (.setToken history (location->token token-mapper location)))

                        (replace-location! [_ location]
                          (.replaceToken history (location->token token-mapper location)))

                        (link [router location]
                          {:href (location->token token-mapper location)
                           :on-click (fn [e]
                                       (when (and (not (.-platformModifierKey e))
                                                  (not (.-shiftKey e))
                                                  (not (.-ctrlKey e))
                                                  (not (.-altKey e)))
                                         (.preventDefault e)
                                         (set-location! router location)))}))

                      (fn []
                        (.setEnabled history false)
                        (.removeAllListeners history))))))

(defn ->page
  ([value]
   (->page value (fn [])))

  ([value unmount-fn]
   (p/->Page value unmount-fn)))
