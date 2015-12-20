(ns todomvc.ui.events
  (:require [todomvc.ui.model :as model]
            [clojure.string :as s]
            [reagent.core :as r]
            [traversy.lens :as tl]))

(defn todo-item-controller [todo-id {:keys [!todos !todo-component]}]
  (let [!todo (r/cursor !todos [todo-id])

        !todo-items (r/cursor !todo-component [:todo-items])
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
                   (swap! !todo-item
                          assoc
                          :editing? false
                          :caption nil))

     :editing? (fn [] (:editing? @!todo-item))

     :set-done! (fn [done?]
                  (swap! !todo assoc :done? done?))

     :delete! (fn []
                (swap! (model/!todos) dissoc todo-id)
                (swap! (model/!todo-items) dissoc todo-id))}))

(defn new-todo-controller [{:keys [!todos !todo-component]}]
  (let [!new-caption (r/cursor !todo-component [:new-todo-caption])]
    {:!new-caption !new-caption

     :save-new-todo! (fn []
                       (swap! !todos
                              (fn [todos]
                                (let [new-id (inc (apply max 0 (keys todos)))]
                                  (assoc todos new-id {:caption @!new-caption}))))

                       (reset! !new-caption nil))}))

(defn mount-todo-list! [!todo-component]
  (reset! !todo-component {:todo-filter :all}))

(defn todo-list-controller [!todo-component]
  (let [!todos (model/!todos)]

    (merge (new-todo-controller {:!todos !todos
                                 :!todo-component !todo-component})

           {:!filter (r/cursor !todo-component [:todo-filter])

            :set-all-done! (fn [done?]
                             (swap! !todos
                                    tl/update
                                    tl/all-values
                                    #(assoc % :done? done?)))

            :clear-completed! (fn []
                                (swap! !todos
                                       (fn [todos]
                                         (select-keys todos (->> todos
                                                                 (remove (comp :done? val))
                                                                 (map key))))))

            :todo-item-controller (fn [todo-id]
                                    (todo-item-controller todo-id
                                                          {:!todos !todos
                                                           :!todo-component !todo-component}))})))
