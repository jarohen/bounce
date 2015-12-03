(ns bounce.core
  (:require [bounce.reresolve :as br]
            [bounce.system :as sys]
            [clojure.set :as set]
            #?(:clj [clojure.tools.namespace.repl :as ctn])
            [com.stuartsierra.dependency :as deps]))

;; -- Creating a system --

(def ^:dynamic ^:private *!system*
  nil)

(defn ->component
  "Creates a close-able component, with an optional close function."

  ([value]
   (->component value nil))

  ([value close!]
   (sys/->Component value close!)))

(defn using
  "Accepts a component function (a 0-arg function returning a component) and a
  set of dependencies, and returns an augmented component function with the
  declared dependencies.

   Usage:

   (-> (fn []
         (->component ... (fn [] ...)))
       (using #{:db-conn :config}))"

  [component-fn dependencies]

  (vary-meta component-fn assoc ::deps dependencies))

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
  (when !system
    (boolean (loop []
               (when-let [system @!system]
                 (let [close! (sys/-close-fn system)]
                   (if (compare-and-set! !system system nil)
                     (do
                       (close!)
                       true)

                     (recur))))))))

(defn make-system
  "Starts a system from the given system map, returning a system value that can be closed.

   Accepts a map, from a component key to a 0-arg function returning a component. (The component can then be wrapped in a call to 'using')

   Usage:
   (require '[bounce.core :as bc])

   (bc/make-system {:config (fn []
                              (->component (read-config ...)))

                    :db-conn (-> (fn []
                                   (let [db-config (:db (bc/ask :config))
                                         db-conn (open-db-conn! db-config)]
                                     (bc/->component db-conn (fn []
                                                            (close-db-conn! db-conn)))))

                                 (bc/using #{:config}))

                    :web-server (-> (fn []
                                      ...)
                                    (bc/using #{:config :db-conn}))})"

  ([system-map]
   (make-system system-map {:targets (keys system-map)}))

  ([system-map {:keys [targets]}]
   (let [ordered-dep-ids (-> (order-deps (->> (for [[dep-id dep] system-map]
                                                [dep-id (set (::deps (meta dep)))])
                                              (into {}))
                                         {:targets targets})

                             (doto (check-deps! (keys system-map))))

         !new-system (atom (sys/->system {:value {}
                                          :expected-dep-ids (set ordered-dep-ids)}))]

     (binding [*!system* !new-system]
       (doseq [dep-id ordered-dep-ids]
         (satisfy! !new-system dep-id (try
                                        (let [component-fn (get system-map dep-id)]
                                          (or (component-fn)
                                              (->component nil)))

                                        (catch Exception e
                                          (close-system! !new-system)
                                          (throw e)))))

       @!new-system))))

;; -- REPL API --

(def ^:private !system
  (atom nil))

(defn- !current-system []
  (or *!system* !system))

(def ^:private !system-fn
  (atom nil))

(defn set-system-fn!
  "Expects a 0-arg function returning a system, setting it as the function used
  to create a system when `start!` or `reload!` are called."
  [system-fn]
  (reset! !system-fn (br/with-reresolve system-fn)))

(defn start!
  "REPL function - starts a system by calling the previously set system-fn.

   If there is already a system running, this is a no-op"
  []
  (when-not @!system
    (if-let [system-fn @!system-fn]
      (let [new-system (system-fn)]
        (when-not (satisfies? sys/ISystem new-system)
          (throw (ex-info "Expecting a system, got" {:type (type new-system)
                                                     :system new-system})))

        (boolean (reset! !system new-system)))

      (throw (ex-info "Please set a Bounce system-fn!" {})))))

(defn stop!
  "REPL function - stops the running system.

   If there is no system started, this is a no-op."
  []
  (close-system! !system))

(defn reload!
  "REPL function - reloads the running system.

   Optional parameters (CLJ only):
   - refresh?     :- Refreshes any namespaces that have changed, using clojure.tools.namespace
   - refresh-all? :- Refreshes all namespaces."

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

(defn snapshot
  "Returns the value of the given system.

   If no system is provided, uses the current running system"
  ([]
   (snapshot (some->> (!current-system)
                      deref)))

  ([system]
   (some->> system
            sys/-snapshot)))

(defn ask
  "Asks for the component value under the provided key 'k' in the current system.

   If the component has not yet been started:

   - If the value is immediately available, it will be returned
   - If the value is not immediately available, and this function is called on the same thread as the system is being started, it means that we've got an invalid dependency order, so this function will throw an error.
   - If the value is not immediately available, and this function is called on a different thread, then it will be being started concurrently, so:
     - If we're in Clojure, this function will block waiting on the value.
     - If we're in CLJS, we can't block, so this function will throw an error.
     - If we're blocking, and creating the requested value fails, this function will throw an error.

  If 'ks' are provided, they will be looked up as a nested path in the resulting value, like 'get-in'"
  [k & ks]
  (let [!system (!current-system)
        get-dep (loop []
                  (if-let [system @!system]
                    (let [{:keys [next-system get-dep]} (sys/-ask system k)]
                      (if (compare-and-set! !system system next-system)
                        get-dep
                        (recur)))

                    (throw (ex-info "No system available." {}))))]

    (-> (get-dep)
        (get-in ks))))

;; -- System test/utility functions --

(defn with-system
  "Runs the given function, in the context of the provided system.

   Optionally, takes a parameter that determines whether this function will
   close the system afterwards (defaults to true)"
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

(defn with-vary-system
  "Varies the system context for the duration of the function.

   Usage:
   (with-vary-system #(assoc-in % [:config :db-config :db] \"test\")
     (fn []
       ;; => \"test\"
       (println (bc/ask :config :db-config :db))))"
  [vary-fn f]
  (let [system-value (snapshot)]
    (with-system (vary-fn system-value) {:close? false} f)))

(defn fmap-component
  "Updates the given component using the given function (and extra args, if provided)

   Usage:

   (fmap-component ... assoc :key :value)"
  [component f & args]
  (sys/-fmap-component component #(apply f % args)))

(defn fmap-component-fn
  "Updates the given component using the given function (and extra args, if provided)

   Usage:

   (defn db-conn-component []
     (-> (fn []
           (let [db-conn (open-db-conn! (bc/ask :config :db))]
             (->component db-conn (fn []
                                    (close-db-conn! db-conn)))))
         (bc/using #{:config})))

   (fmap-component-fn (db-conn-component) #(update % ...))"

  [component-fn f & args]

  (-> (fn []
        (apply fmap-component (component-fn) f args))
      (using (::deps (meta component-fn)))))

(defn with-component
  "Runs the provided function, passing it the value of the component. When the
  function returns, the component is closed."
  [component f]

  (try
    (f (sys/-value component))
    (finally
      (sys/-close! component))))
