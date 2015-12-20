(ns todomvc.ui.app
  (:require [todomvc.ui.model :as model]
            [todomvc.ui.events :as events]
            [todomvc.ui.view :as view]
            [bounce.core :as bc]
            [clojure.string :as s]
            [reagent.core :as r]))

(enable-console-print!)

(defn page-component []
  (let [!todo-controller (doto (r/cursor (model/!app) [::events/todo-controller])
                           (events/mount-todo-list!))]
    (fn []
      [view/todo-list (events/todo-list-controller !todo-controller)])))

(defn render-page! []
  (r/render-component [page-component] js/document.body))

(defn make-bounce-map []
  (model/app-state-system-map))

(set! (.-onload js/window)
      (fn []
        (bc/set-system-map-fn! make-bounce-map)
        (bc/start!)

        (render-page!)))
