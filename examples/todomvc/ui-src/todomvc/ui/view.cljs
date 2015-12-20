(ns todomvc.ui.view
  (:require [todomvc.ui.model :as model]
            [goog.events.KeyCodes :as kc]
            [reagent.core :as r]))

(defn on-enter [f]
  (fn [e]
    (when (= kc/ENTER (.-keyCode e))
      (f e)
      (.preventDefault e))))

(defn new-todo-widget [{:keys [save-new-todo! !new-caption] :as handlers}]
  [:input#new-todo {:placeholder "What needs to be done?"
                    :type "text"
                    :value @!new-caption
                    :on-change #(reset! !new-caption (.. % -target -value))
                    :on-key-down (on-enter save-new-todo!)}])

(defn toggle-all-widget [{:keys [set-all-done!]}]
  (let [all-done? (every? :done? (vals @(model/!todos)))]
    [:input#toggle-all {:type "checkbox"
                        :checked all-done?
                        :on-change #(set-all-done! (not all-done?))}]))

(defn todo-item [todo-id {:keys [edit! editing? !new-caption set-done! delete! save-todo!]}]
  (let [{:keys [done? caption] :as todo-item} @(r/cursor (model/!todos) [todo-id])]
    (if (editing?)
      [:li.editing
       [:input.edit {:value @!new-caption
                     :autofocus true
                     :type "text"
                     :on-change #(reset! !new-caption (.. % -target -value))
                     :on-key-down (on-enter save-todo!)}]]

      [:li
       [:div.view
        [:input.toggle {:type "checkbox",
                        :checked done?
                        :on-change #(set-done! (not done?))}]

        [:label {:on-double-click #(edit!)}
         caption]

        [:button.destroy {:on-click #(delete!)}]]])))

(defn stats-view []
  (let [todo-count (->> @(model/!todos)
                        vals
                        (remove :done?)
                        count)]
    [:span#todo-count
     [:strong todo-count]
     [:span " items left"]]))

(def filter-todos
  {:all identity
   :active (complement :done?)
   :completed :done?})

(defn clear-completed-view [{:keys [clear-completed!]}]
  (let [completed-count (->> @(model/!todos)
                             vals
                             (filter :done?)
                             count)]
    [:div
     (when-not (zero? completed-count)
       [:button#clear-completed {:on-click #(clear-completed!)}
        (str "Clear completed " completed-count)])]))

(def filter-label
  {:all "All"
   :active "Active"
   :completed "Completed"})

(defn filters-view [{:keys [!filter]}]
  [:ul#filters
   (let [todo-filter @!filter]
     (for [filter-option [:all :active :completed]]
       [:li {:style {:cursor :pointer}
             :key filter-option}
        [:a {:class (when (= todo-filter filter-option)
                      "selected")
             :on-click #(reset! !filter filter-option)}
         (filter-label filter-option)]]))])

(defn todo-list [{:keys [!filter todo-item-controller] :as handlers}]
  [:section#todoapp
   [:header#header
    [:h1 "todos"]
    [new-todo-widget handlers]]

   [:section#main
    [toggle-all-widget handlers]
    [:label {:for "toggle-all"}
     "Mark all as complete"]

    [:ul#todo-list
     (-> (for [todo-id (->> @(model/!todos)
                            (filter (comp (get filter-todos @!filter) val))
                            (map key)
                            sort)]
           (-> [todo-item todo-id (todo-item-controller todo-id)]
               (with-meta {:key todo-id})))
         doall)]]

   [:footer#info
    [:p "Double-click to edit a todo"]]

   [:footer#footer
    [stats-view]
    [filters-view handlers]
    [clear-completed-view handlers]]])
