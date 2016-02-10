(ns todomvc.ui.view
  (:require [goog.events.KeyCodes :as kc]
            [reagent.core :as r]))

(defn on-enter [f]
  (fn [e]
    (when (= kc/ENTER (.-keyCode e))
      (f e)
      (.preventDefault e))))

(defn new-todo-view [{:keys [save-new-todo! !new-caption] :as ctx}]
  [:input#new-todo {:placeholder "What needs to be done?"
                    :type "text"
                    :value @!new-caption
                    :on-change #(reset! !new-caption (.. % -target -value))
                    :on-key-down (on-enter save-new-todo!)}])

(defn toggle-all-view [{:keys [!todos set-all-done!]}]
  (let [all-done? (every? :done? (vals @!todos))]
    [:input#toggle-all {:type "checkbox"
                        :checked all-done?
                        :on-change #(set-all-done! (not all-done?))}]))

(defn todo-item [todo-id {:keys [!todo edit! editing? !new-caption toggle-done! delete! save-todo!]}]
  (let [{:keys [done? caption] :as todo-item} @!todo]
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
                        :on-change #(toggle-done!)}]

        [:label {:on-double-click #(edit!)}
         caption]

        [:button.destroy {:on-click #(delete!)}]]])))

(defn stats-view [{:keys [!todos]}]
  (let [todo-count (->> @!todos
                        vals
                        (remove :done?)
                        count)]
    [:span#todo-count
     [:strong todo-count]
     [:span " items left"]]))

(defn clear-completed-view [{:keys [clear-completed! !todos]}]
  (let [completed-count (->> @!todos
                             vals
                             (filter :done?)
                             count)]
    [:div
     (when-not (zero? completed-count)
       [:button#clear-completed {:on-click #(clear-completed!)}
        (str "Clear completed " completed-count)])]))

(def filter-labels
  {:all "All"
   :active "Active"
   :completed "Completed"})

(defn filters-view [{:keys [!filter]}]
  [:ul#filters
   (let [todo-filter @!filter]
     (for [filter-option (keys filter-labels)]
       [:li {:style {:cursor :pointer}
             :key filter-option}
        [:a {:class (when (= todo-filter filter-option)
                      "selected")
             :on-click #(reset! !filter filter-option)}
         (get filter-labels filter-option)]]))])

(def todo-filters
  {:all identity
   :active (complement :done?)
   :completed :done?})

(defn todo-list [{:keys [!todos !filter todo-item-ctx] :as ctx}]
  [:section#todoapp
   [:header#header
    [:h1 "todos"]
    [new-todo-view ctx]]

   [:section#main
    [toggle-all-view ctx]
    [:label {:for "toggle-all"}
     "Mark all as complete"]

    [:ul#todo-list
     (-> (for [todo-id (->> @!todos
                            (filter (comp (get todo-filters @!filter) val))
                            (map key)
                            sort)]
           (-> [todo-item todo-id (todo-item-ctx todo-id)]
               (with-meta {:key todo-id})))
         doall)]]

   [:footer#info
    [:p "Double-click to edit a todo"]]

   [:footer#footer
    [stats-view ctx]
    [filters-view ctx]
    [clear-completed-view ctx]]])
