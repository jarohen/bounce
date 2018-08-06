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

(defn- resolve-dep [ns dep]
  (cond
    (var? dep) dep
    (symbol? dep) (or (ns-resolve ns dep)
                      (throw (ex-info "Could not resolve dependency" {:dep dep})))))

(defn- parse-component-opts [[maybe-opts & body] {:keys [ns]}]
  (if (and (map? maybe-opts) (seq body))
    (merge {:body body
            :bounce/deps (->> (:bounce/deps maybe-opts)
                              (into #{} (map (fn [dep]
                                               (resolve-dep ns dep)))))}
           (select-keys maybe-opts #{:bounce/args}))
    {:body (cons maybe-opts body)}))

(defmacro defcomponent [sym & body]
  (let [{:keys [bounce/deps bounce/args body]} (parse-component-opts body {:ns *ns*})]
    `(doto (def ~(-> sym (with-meta {:dynamic true})) nil)
       (alter-meta! merge {:bounce/deps '~deps
                           :bounce/component (fn ~(symbol (str "start-" (name sym))) [~@args]
                                               (->started-component (do ~@body)))}))))

(defmacro with-stop [value & body]
  `(->started-component ~value (fn ~'stop [] ~@body)))

(defn fmap-component [component f]
  (let [{:keys [value stop!]} (->started-component component)]
    (->started-component (f value) stop!)))

(defn order-deps [deps]
  (loop [[dep & more-deps] (seq deps)
         seen #{}
         g (deps/graph)]
    (if dep
      (let [upstream-deps (:bounce/deps (meta dep))]
        (recur (distinct (remove seen (concat more-deps upstream-deps)))
               (conj seen dep)
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

(defn with-system* [started-system f]
  (with-bindings (->> (:components started-system)
                      (into {} (map (fn [[component-var {:keys [value]}]]
                                      [component-var value]))))
    (try
      (f)
      (finally
        (stop-system started-system)))))

(defmacro with-system [started-system & body]
  `(with-system* ~started-system (fn [] ~@body)))

(def ^:private !opts (atom nil))

(defn set-opts!
  ([deps] (set-opts! deps {}))
  ([deps opts] (reset! !opts [deps opts])))

(defn start! []
  (when-let [[deps opts] @!opts]
    (let [deps (->> deps
                    (into #{} (keep (fn [dep]
                                      (ns-resolve (doto (symbol (namespace dep)) require) dep)))))]

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

(defn restart! []
  (stop!)
  (start!))
