(ns bounce.core
  (:require [bounce.reresolve :as br]
            [bounce.system :as sys]
            [bounce.watcher :as w]
            [clojure.set :as set]
            [clojure.tools.namespace.repl :as ctn]
            [com.stuartsierra.dependency :as deps]))

;; -- Creating a system --

(def ^:dynamic ^:private *!system*
  nil)

(defn ->component
  ([value]
   (->component value nil))

  ([value close!]
   (sys/->Component value close!)))

(defn using [component dependencies]
  (cond
    (map? dependencies) (vary-meta component update ::deps merge dependencies)

    (set? dependencies) (using component (->> (for [dep-id dependencies]
                                                [dep-id dep-id])
                                              (into {})))

    :else (throw (ex-info "Dependencies must be a map or a set!"
                          {:dependencies dependencies}))))

(defn- order-deps [dep-map {:keys [targets]}]
  (loop [g (deps/graph)
         work-set (set targets)
         done-set #{}]
    (if-let [dep-id (first (seq work-set))]
      (let [next-deps (get dep-map dep-id)]
        (recur (reduce (fn [g dependent-id]
                         (deps/depend g dep-id dependent-id))
                       (deps/depend g ::sys dep-id)
                       next-deps)

               (-> work-set
                   (set/union next-deps)
                   (set/difference done-set))

               (conj done-set dep-id)))

      (remove #{::sys} (deps/topo-sort g)))))

(defn- check-deps! [needed available]
  (when-let [missing-deps (seq (set/difference (set needed)
                                               (set available)))]
    (throw (ex-info "Missing dependencies:" {:missing (set missing-deps)}))))

(defn- satisfy! [!system dep-id component]
  (loop []
    (let [system @!system
          {:keys [next-system notify!]} (sys/-satisfy system dep-id component)]
      (if (compare-and-set! !system system next-system)
        (do
          (notify!)
          next-system)

        (recur)))))

(defn- close-system! [!system]
  (boolean (loop []
             (when-let [system @!system]
               (let [close! (sys/-close-fn system)]
                 (if (compare-and-set! !system system nil)
                   (do
                     (close!)
                     true)

                   (recur)))))))

(defn make-system
  ([system-map]
   (make-system system-map {:targets (keys system-map)}))

  ([system-map {:keys [targets]}]
   (let [ordered-dep-ids (-> (order-deps (->> (for [[dep-id dep] system-map]
                                                [dep-id (set (vals (::deps (meta dep))))])
                                              (into {}))
                                         {:targets targets})

                             (doto (check-deps! (keys system-map))))

         !new-system (atom (sys/->system {:value {}
                                          :expected-dep-ids (set ordered-dep-ids)}))]

     (binding [*!system* !new-system]
       (doseq [dep-id ordered-dep-ids]
         (satisfy! !new-system dep-id (try
                                        (let [component-fn (get system-map dep-id)]
                                          (component-fn))

                                        (catch Exception e
                                          (close-system! !new-system)
                                          (throw e)))))

       @!new-system))))

;; -- System test/utility functions --

(defn with-system
  ([system f]
   (with-system system {:close? true} f))

  ([system {:keys [close?]} f]
   (binding [*!system* (atom (if (satisfies? sys/ISystem system)
                               system
                               (sys/->system {:value system
                                              :expected-dep-ids (keys system)})))]
     (try
       (f)

       (finally
         (when close?
           (close-system! *!system*)))))))

;; -- REPL API --

(def ^:private !system
  (atom nil))

(defn- !current-system []
  (or *!system*
      !system))

(def ^:private !system-fn
  (atom nil))

(defn set-system-fn! [system-fn]
  (reset! !system-fn (br/with-reresolve system-fn)))

(defn start! []
  (assert (nil? @!system) "System already started!")

  (if-let [system-fn @!system-fn]
    (let [new-system (system-fn)]
      (when-not (satisfies? sys/ISystem new-system)
        (throw (ex-info "Expecting a system, got" {:type (type new-system)
                                                   :system new-system})))

      (boolean (reset! !system new-system)))

    (throw (ex-info "Please set a Bounce system-var!" {}))))

(defn stop! []
  (close-system! !system))

(defn reload!
  ([]
   (reload! {}))

  ([{:keys [refresh-all? refresh?], :or {refresh? true, refresh-all? false}}]

   (stop!)

   #?(:clj
      (when (or refresh-all? refresh?)
        (let [ctn-result (do
                           (when refresh-all?
                             (ctn/clear))
                           (ctn/refresh))]
          (when-not (= :ok ctn-result)
            (throw ctn-result)))))

   (start!)))

;; -- Getting hold of system values --

(defn snapshot []
  (some->> (!current-system)
           deref
           sys/-snapshot))

(defn ask [k & ks]
  (let [!system (!current-system)

        get-dep (loop []
                  (let [system @!system
                        {:keys [next-system get-dep]} (sys/-ask system k)]
                    (if (compare-and-set! !system system next-system)
                      get-dep
                      (recur))))]

    (cond-> (get-dep)
      (seq ks) (get-in ks))))
