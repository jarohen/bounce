(ns todomvc.ui.events
  (:require [todomvc.ui.model :as model]
            [bounce.core :as bc]
            [clojure.string :as s]
            [reagent.core :as r]
            [traversy.lens :as tl]))

(defn !todo-component []
  (r/cursor (bc/ask :!app) [::todo-component]))

(defn todo-item-controller [todo-id]
  (let [!todo (r/cursor (model/!todos) [todo-id])

        !todo-items (r/cursor (!todo-component) [:todo-items])
        !todo-item (r/cursor !todo-items [todo-id])
        !new-caption (r/cursor !todo-item [:new-caption])]

    {:!new-caption !new-caption

     :edit! (fn []
              (swap! !todo-item
                     assoc
                     :editing? true
                     :new-caption (get @!todo :caption)))

     :save-todo! (fn []
                   (swap! !todo assoc :caption (or (let [new-caption (:new-caption @!todo-item)]
                                                     (when-not (s/blank? new-caption)
                                                       new-caption))
                                                   (:caption @!todo)))

                   (swap! !todo-item assoc :editing? false))

     :editing? (fn []
                 (:editing? @!todo-item))

     :set-done! (fn [done?]
                  (swap! !todo assoc :done? done?))

     :delete! (fn []
                (swap! (model/!todos) dissoc todo-id)
                (swap! !todo-items dissoc todo-id))}))

(defn new-todo-controller []
  (let [!new-caption (r/cursor (!todo-component) [:new-todo-caption])]
    {:!new-caption !new-caption

     :save-new-todo! (fn []
                       (swap! (model/!todos)
                              (fn [todos]
                                (let [new-id (inc (apply max 0 (keys todos)))]
                                  (assoc todos new-id {:caption @!new-caption}))))

                       (reset! !new-caption nil))}))

(defn mount-todo-list! []
  (reset! (!todo-component) {:todo-filter :all}))

(defn todo-list-controller []
  (merge (new-todo-controller)

         {:!filter (r/cursor (!todo-component) [:todo-filter])

          :set-all-done! (fn [done?]
                           (swap! (model/!todos)
                                  tl/update
                                  tl/all-values
                                  #(assoc % :done? done?)))

          :clear-completed! (fn []
                              (swap! (model/!todos)
                                     (fn [todos]
                                       (select-keys todos (->> todos
                                                               (remove (comp :done? val))
                                                               (map key))))))

          :todo-item-controller (fn [todo-id]
                                  (todo-item-controller todo-id))}))
