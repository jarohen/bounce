(ns multi-page.ui.app
  (:require [clojure.string :as s]
            [bounce.core :as bc]
            [bounce.mux :as mux]
            [bounce.mux.bidi :as mux.bidi]
            [reagent.core :as r]))

(enable-console-print!)

(defn render-page! []
  (r/render-component [(fn []
                         [@(r/cursor (bc/ask :!app) [::root-component])])]
                      js/document.body))

(defn home-page [{:keys [!count]}]
  [:div
   "Home Page"
   [:p
    [:a (mux/link (bc/ask :router) {:handler ::page-two
                                    :route-params {:id 0}})
     "Page 2"]]

   [:p "Count: " (pr-str @!count)]
   [:p [:button {:on-click #(swap! !count inc)}
        "Inc"]]])

(defn page-two []
  [:div
   "Page 2"
   [:p
    [:a (mux/link (bc/ask :router) {:handler ::home-page})
     "Home page"]]
   [:p
    [:a (mux/link (bc/ask :router) {:handler ::page-two
                                    :route-params {:id 1}})
     "Page 2 (id = 1)"]]])

(defn make-system-map []
  {:!app (fn []
           (bc/->component (r/atom {})))

   :router (-> (fn []
                 (mux/make-router {:token-mapper (mux.bidi/token-mapper ["" {"/" ::home-page
                                                                             "/page-two" {["/" :id] ::page-two}}])
                                   :listener (fn [{:keys [location page]}]
                                               (reset! (r/cursor (bc/ask :!app) [:location]) location)
                                               (reset! (r/cursor (bc/ask :!app) [::root-component]) page))

                                   :default-location {:handler ::home-page}

                                   :pages {::home-page (fn [{:keys [old-location new-location same-handler?]}]
                                                         (let [!count (r/cursor (bc/ask :!app) [::home-count])]
                                                           (when-not same-handler?
                                                             (reset! !count 0))

                                                           (mux/->page (fn []
                                                                         [home-page {:!count !count}])

                                                                       (fn [{:keys [old-location new-location same-handler?]}]
                                                                         (when-not same-handler?
                                                                           (swap! (bc/ask :!app) dissoc ::home-count))))))

                                           ::page-two (fn [{:keys [old-location new-location same-handler?]}]
                                                        (when-not same-handler?
                                                          (println "mount page-2"))

                                                        (mux/->page (fn []
                                                                      [page-two])

                                                                    (fn [{:keys [old-location new-location same-handler?]}]
                                                                      (when-not same-handler?
                                                                        (println "unmount page 2!")))))}}))
               (bc/using #{:!app}))

   :renderer (-> (fn []
                   (bc/->component (render-page!)
                                   (fn []
                                     (r/unmount-component-at-node js/document.body))))

                 (bc/using #{:!app :router}))})

(set! (.-onload js/window)
      (fn []
        (bc/set-system-map-fn! #(make-system-map))
        (bc/start!)))
