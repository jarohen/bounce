(ns todomvc.ui.events
  (:require [bounce.core :as bc]
            [clojure.string :as s]
            [reagent.core :as r]
            [traversy.lens :as tl]))

(defn todo-item-controller [todo-id {:keys [!todos !todo-component] :as todo-state}]
  (let [!todo (r/cursor !todos [todo-id])

        !todo-items (r/cursor !todo-component [:todo-items])
        !todo-item (r/cursor !todo-items [todo-id])]

    {:!todo !todo
     :!new-caption (r/cursor !todo-item [:new-caption])

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

     :toggle-done! (fn []
                     (swap! !todo update :done? not))

     :delete! (fn []
                (swap! !todos dissoc todo-id)
                (swap! !todo-items dissoc todo-id))}))

(defn new-todo-controller [{:keys [!todos !todo-component] :as todo-state}]
  (let [!new-caption (r/cursor !todo-component [:new-todo-caption])]
    {:!new-caption !new-caption

     :save-new-todo! (fn []
                       (swap! !todos
                              (fn [todos]
                                (let [new-id (inc (apply max 0 (keys todos)))]
                                  (assoc todos new-id {:caption @!new-caption}))))

                       (reset! !new-caption nil))}))

(defn mount-todo-list! [{:keys [!todo-component]}]
  (reset! !todo-component {:todo-filter :all}))

(defn todo-list-controller [{:keys [!todos !todo-component] :as todo-state}]
  (merge (new-todo-controller todo-state)

         {:!todos !todos

          :!filter (r/cursor !todo-component [:todo-filter])

          :set-all-done! (fn [done?]
                           (swap! !todos tl/update tl/all-values #(assoc % :done? done?)))

          :clear-completed! (fn []
                              (swap! !todos
                                     (fn [todos]
                                       (select-keys todos (->> todos
                                                               (remove (comp :done? val))
                                                               (map key))))))

          :todo-item-controller (fn [todo-id]
                                  (todo-item-controller todo-id todo-state))}))
