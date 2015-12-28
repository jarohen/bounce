(ns todomvc.ui.model
  (:require [bounce.core :as bc]
            [reagent.core :as r]))

(defn initial-state []
  {:todos {0 {:caption "Write some TODOs"
              :done? false}}})

(defn !todos []
  (r/cursor (bc/ask :!app) [:todos]))
