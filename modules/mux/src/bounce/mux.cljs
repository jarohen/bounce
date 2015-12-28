(ns bounce.mux
  (:require [bounce.mux.protocols :as p]
            [clojure.string :as s]
            [bounce.core :as bc]
            [goog.History :as h]
            [goog.events :as e])

  (:import [goog.history Html5History]))

(defn make-router
  "Makes a Router Bounce component - to be passed to the other
  functions in this namespace.

  When the browser history changes, the following happens:

  1. The token mapper is asked to translate the token to a new
  location (i.e. handler, route-params and query-params)

  2. The current page's unmount-fn is called (most of these check
  'same-handler?' to see whether they _actually_ need to unmount)

  3. We get the new page fn out of the pages map, and call
  it. (Likewise, it checks 'same-handler?' to see what it needs to
  mount.) It returns a Page (pair of 'page value' and unmount-fn) by
  calling mux/->page.

  4. 'listener' is called with the new location and the page value.

  Parameters:

  token-mapper :: bounce.mux.protocols/TokenMapper (see bounce.mux.bidi for an example implementation)
  listener :: ({:keys [location page]} -> ())
  default-location :: location
  pages :: {handler-key -> ({:keys [old-location new-location same-handler?]} -> Page)}

  Usage:

  (require '[bounce.core :as bc]
           '[bounce.mux :as mux]
           '[bounce.mux.bidi :as mux.bidi]
           '[reagent.core :as r])

  (defn make-system-map []
    {:!app (fn []
             (bc/->component (r/atom {})))

     :router (-> (fn []
                   (mux/make-router {:token-mapper (mux.bidi/token-mapper [\"\" {\"/\" ::home-page
                                                                               ...}])
                                     :listener (fn [{:keys [location page]}]
                                                 (reset! (r/cursor (bc/ask :!app) [:location]) location)
                                                 (reset! (r/cursor (bc/ask :!app) [::root-component]) page))

                                     :default-location {:handler ::home-page}

                                     :pages {::home-page (fn [{:keys [old-location new-location same-handler?]}]
                                                           (when-not same-handler?
                                                             ;; mount!
                                                             )

                                                           (mux/->page (fn []
                                                                         [home-page-view])

                                                                       (fn [{:keys [old-location new-location same-handler?]}]
                                                                         (when-not same-handler?
                                                                           ;; unmount!
                                                                           ))))

                                             ...}}))
                 (bc/using #{:!app}))

     ...})

  "
  [{:keys [token-mapper listener pages default-location]}]

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
                        (listener {:location new-location
                                   :page (p/-value new-page)})))

                    (js/console.warn "Can't find new handler:" (pr-str new-location))))))]

      (let [history-listener (fn [e]
                               (let [token (or (let [token (.-token e)]
                                                 (when-not (s/blank? token)
                                                   token))

                                               (p/-location->token token-mapper default-location)

                                               "/")]

                                 (if-let [location (p/-token->location token-mapper token)]
                                   (on-change location)
                                   (js/console.warn "Invalid location: " (pr-str {:token token})))))]

        (e/listen history h/EventType.NAVIGATE history-listener)

        (.setEnabled history true)

        (bc/->component (reify p/Router
                          (-set-location! [_ location]
                            (.setToken history (p/-location->token token-mapper location)))

                          (-replace-location! [_ location]
                            (.replaceToken history (p/-location->token token-mapper location)))

                          (-link [router location]
                            {:href (p/-location->token token-mapper location)
                             :on-click (fn [e]
                                         (when (and (not (.-platformModifierKey e))
                                                    (not (.-shiftKey e))
                                                    (not (.-ctrlKey e))
                                                    (not (.-altKey e)))
                                           (.preventDefault e)
                                           (p/-set-location! router location)))}))

                        (fn []
                          (.setEnabled history false)
                          (.unlisten history h/EventType.NAVIGATE history-listener)))))))

(defn ->page
  "Given a value, and (optionally) an unmount-fn, returns a 'Page'
  pair, to be returned from the :pages map for 'make-router'

  value :: a
  unmount-fn :: ({:keys [old-location new-location same-handler?]} -> ())

  same-handler? is a convenience value - it's true iff the handler
  hasn't changed from old-location to new-location. Equivalent
  to (= (:handler old-location) (:handler new-location))."

  ([value]
   (->page value (fn [{:keys [old-location new-location same-handler?]}])))

  ([value unmount-fn]
   (p/->Page value unmount-fn)))

(defn link
  "Generates an <a> tag properties map for the given location.

  Will intercept a left click, and set the history token.

  Will ignore ctrl/alt/super/shift-clicks, and defer to the browser
  default behaviour (new tab/save page, usually)

  Usage:

  [:a (merge (mux/link {:handler ...
                        :route-params {...}
                        :query-params {...}})
             {:other \"properties\"})]
  "
  [router location]

  (p/-link router location))

(defn set-location!
  "Sets the History location to the given location"
  [router location]

  (p/-set-location! router location))

(defn replace-location!
  "Replaces the current History location with the given location"
  [router location]

  (p/-replace-location! router location))
