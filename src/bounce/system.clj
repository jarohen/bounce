(ns bounce.system
  (:require [clojure.set :as set]
            [com.stuartsierra.dependency :as deps]))

(def !system
  (atom nil))

(defrecord StartedComponent [value stop!])

(defn ->started-component
  ([value] (if (instance? StartedComponent value)
             value
             (->StartedComponent value (fn []))))
  ([value stop!] (->StartedComponent value stop!)))

(defmacro defcomponent [sym deps bindings & body]
  `(doto (def ~(-> sym (with-meta {:dynamic true})))
     (alter-meta! merge {:bounce/deps ~deps
                         :bounce/component (fn ~(symbol (str "start-" (name sym))) [~@bindings]
                                             (->started-component (do ~@body)))})))

(defmacro with-stop [value & body]
  `(->started-component ~value (fn ~'stop [] ~@body)))

(defn order-deps [deps]
  (loop [[dep & more-deps] (seq deps)
         g (deps/graph)]
    (if dep
      (let [upstream-deps (:bounce/deps (meta dep))]
        (recur (distinct (remove (set (deps/nodes g)) (concat more-deps upstream-deps)))
               (reduce (fn [g upstream-dep]
                         (deps/depend g dep upstream-dep))
                       (deps/depend g :system dep)
                       upstream-deps)))
      (remove #{:system} (deps/topo-sort g)))))

(defn start-system
  ([deps] (start-system deps {}))
  ([deps {:bounce/keys [args overrides]}]
   (let [dep-order (order-deps deps)
         start-fn (reduce (fn [f dep]
                            (fn [system]
                              (let [component-fn (or (get overrides dep)
                                                     (:bounce/component (meta dep)))
                                    started-component (->started-component (apply component-fn (get args dep)))]
                                (try
                                  (push-thread-bindings {dep (:value started-component)})
                                  (f (assoc system dep started-component))
                                  (catch Exception e
                                    (try
                                      ((:stop! started-component))
                                      (catch Exception e (.printStackTrace e))))
                                  (finally
                                    (pop-thread-bindings))))))
                          identity
                          (reverse dep-order))]
     {:dep-order dep-order
      :components (start-fn {})})))

(defn stop-system [{:keys [dep-order components] :as system}]
  (let [stop-fn (reduce (fn [f dep]
                          (fn [system]
                            (let [{:keys [value stop!]} (get components dep)]
                              (try
                                (push-thread-bindings {dep value})
                                (f (assoc system dep value))
                                (stop!)
                                (finally
                                  (pop-thread-bindings))))))
                        identity
                        (reverse dep-order))]
    (stop-fn {})
    (set dep-order)))

(defn- resolve-dep [dep]
  (cond
    (var? dep) dep
    (symbol? dep) (or (do
                        (some-> (namespace dep) symbol require)
                        (resolve dep))
                      (throw (ex-info "Could not resolve dependency" {:dep dep})))))

(defn start!
  ([deps] (start! deps {}))

  ([deps {:bounce/keys [args overrides] :as opts}]
   (let [deps (into #{} (map resolve-dep) deps)]
     (if-not (compare-and-set! !system nil :starting)
       (throw (ex-info "System is already starting/started" {:system @!system}))

       (try
         (let [{:keys [dep-order components] :as started-system} (start-system deps opts)]
           (doseq [[dep {:keys [value]}] components]
             (alter-var-root dep (constantly value)))
           (reset! !system started-system)
           (set dep-order))
         (catch Exception e
           (reset! !system nil)
           (throw e)))))))

(defn stop! []
  (let [system @!system]
    (when (and (map? system) (compare-and-set! !system system :stopping))
      (try
        (stop-system system)
        (finally
          (reset! !system nil))))))
