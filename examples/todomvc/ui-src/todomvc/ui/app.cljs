(ns todomvc.ui.app
  (:require [todomvc.ui.model :as model]
            [todomvc.ui.events :as events]
            [todomvc.ui.view :as view]
            [bounce.core :as bc]
            [bounce.mux :as mux]
            [bounce.mux.bidi :as mux.bidi]
            [reagent.core :as r]))

(enable-console-print!)

(defn render-page! []
  (r/render-component [(fn []
                         [@(r/cursor (bc/ask :!app) [::root-component])])]
                      js/document.body))

(defn make-bounce-map []
  {:!app (fn []
           (bc/->component (r/atom (model/initial-state))))

   :router (-> (fn []
                 (mux/make-router {:token-mapper (mux.bidi/token-mapper ["" {"/" ::todos}])
                                   :listener (fn [{:keys [location page]}]
                                               (reset! (r/cursor (bc/ask :!app) [:location]) location)
                                               (reset! (r/cursor (bc/ask :!app) [::root-component]) page))

                                   :default-location {:handler ::todos}

                                   :pages {::todos (fn [{:keys [old-location new-location same-handler?]}]
                                                     (when-not same-handler?
                                                       (events/mount-todo-list!))

                                                     (mux/->page (fn []
                                                                   [view/todo-list (events/todo-list-controller)])))}}))
               (bc/using #{:!app}))

   :renderer (-> (fn []
                   (bc/->component (render-page!)
                                   (fn []
                                     (r/unmount-component-at-node js/document.body))))

                 (bc/using #{:!app :router}))})

(set! (.-onload js/window)
      (fn []
        (bc/set-system-map-fn! make-bounce-map)
        (bc/start!)))
