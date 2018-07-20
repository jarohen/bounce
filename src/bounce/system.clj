(ns bounce.system
  (:require [clojure.set :as set]
            [com.stuartsierra.dependency :as deps]))

(def ^:private !system
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

(defn fmap-component [component f]
  (let [{:keys [value stop!]} (->started-component component)]
    (->started-component (f value) stop!)))

(defn with-component* [component f]
  (let [{:keys [value stop!]} (->started-component component)]
    (try
      (f value)
      (finally
        (try
          (stop!)
          (catch Exception e
            (.printStackTrace e)))))))

(defmacro with-component [[binding component] & body]
  `(with-component* ~component (fn [~binding] ~@body)))

(defn- resolve-dep [dep]
  (cond
    (var? dep) dep
    (symbol? dep) (or (do
                        (some-> (namespace dep) symbol require)
                        (resolve dep))
                      (throw (ex-info "Could not resolve dependency" {:dep dep})))))

(defn order-deps [deps]
  (loop [[dep & more-deps] (seq deps)
         seen #{}
         g (deps/graph)]
    (if dep
      (let [upstream-deps (map resolve-dep (:bounce/deps (meta dep)))]
        (recur (distinct (remove seen (concat more-deps upstream-deps)))
               (conj seen dep)
               (reduce (fn [g upstream-dep]
                         (deps/depend g dep upstream-dep))
                       (deps/depend g :system dep)
                       upstream-deps)))
      (remove #{:system} (deps/topo-sort g)))))

(defn- normalise-deps+args [deps args]
  (reduce (fn [[deps args] dep-or-dep+args]
            (let [[dep dep-args] (if (vector? dep-or-dep+args)
                                   [(first dep-or-dep+args) (rest dep-or-dep+args)]
                                   [dep-or-dep+args nil])
                  dep (resolve-dep dep)]
              [(conj deps dep) (merge {dep dep-args} args)]))
          [#{} (->> args (into {} (map (fn [[dep args]]
                                         [(resolve-dep dep) args]))))]
          deps))

(defn start-system
  ([deps] (start-system deps {}))
  ([deps {:bounce/keys [args overrides]}]
   (let [[deps args] (normalise-deps+args deps args)
         dep-order (order-deps deps)
         start-fn (reduce (fn [f dep]
                            (fn [system]
                              (let [component-fn (or (get overrides dep)
                                                     (:bounce/component (meta dep)))
                                    started-component (->started-component (apply component-fn (get args dep)))]
                                (with-bindings {dep (:value started-component)}
                                  (try
                                    (f (assoc system dep started-component))
                                    (catch Exception e
                                      (try
                                        ((:stop! started-component))
                                        (catch Exception e (.printStackTrace e)))
                                      (throw e)))))))
                          identity
                          (reverse dep-order))]
     {:dep-order dep-order
      :components (start-fn {})})))

(defn stop-system [{:keys [dep-order components] :as system}]
  (let [stop-fn (reduce (fn [f dep]
                          (fn [system]
                            (let [{:keys [value stop!]} (get components dep)]
                              (with-bindings {dep value}
                                (f (assoc system dep value))
                                (stop!)))))
                        identity
                        (reverse dep-order))]
    (stop-fn {})
    (set dep-order)))

(defn with-system* [system f]
  (with-bindings (->> (:components system)
                      (into {} (map (fn [[component-var {:keys [value]}]]
                                      [component-var value]))))
    (try
      (f)
      (finally
        (stop-system system)))))

(defmacro with-system [system & body]
  `(with-system* ~system (fn [] ~@body)))

(def ^:private !last-opts (atom nil))

(defn start!
  ([] (if-let [[deps opts] @!last-opts]
        (start! deps opts)
        #{}))

  ([deps] (start! deps {}))

  ([deps {:bounce/keys [args overrides] :as opts}]
   (reset! !last-opts [deps opts])

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
         (throw e))))))

(defn stop! []
  (let [system @!system]
    (when (and (map? system) (compare-and-set! !system system :stopping))
      (try
        (stop-system system)
        (finally
          (reset! !system nil))))))

(defn restart! []
  (stop!)
  (start!))
