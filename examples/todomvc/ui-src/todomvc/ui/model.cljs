(ns todomvc.ui.model
  (:require [bounce.core :as bc]
            [reagent.core :as r]))

(defn app-state-system-map []
  {::!app (fn []
            (bc/->component (r/atom {:todos {0 {:caption "Write some TODOs"
                                                :done? false}}})))})

(defn !app []
  (bc/ask ::!app))

(defn !todos []
  (r/cursor (!app) [:todos]))
