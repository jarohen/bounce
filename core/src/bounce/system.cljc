(ns bounce.system)

(defprotocol IComponent
  (-fmap-component [_ f])
  (-close! [_])
  (-value [_]))

(defrecord Component [value close!]
  IComponent
  (-fmap-component [this f]
    (update this :value f))

  (-value [_] value)
  (-close! [_]
    (when close!
      (close!))))

(defprotocol ISystem
  (-ask [_ dep-id])
  (-satisfy [_ dep-id component])
  (-snapshot [_])
  (-close-fn [_]))

(defn- get-thread-id []
  #?(:clj
     (.getId (Thread/currentThread))

     :cljs
     ::cljs-thread))

(defn make-notify-fn [dep-id]
  #?(:clj
     (let [p (promise)]
       {:get-dep #(let [v (deref p)]
                    (if (not= v :bounce/closing)
                      v
                      (throw (ex-info "Couldn't await dependency" {:dep-id dep-id}))))
        :notify! #(deliver p %)})

     :cljs
     (throw (ex-info "Can't block waiting in CLJS"
                     {:dep-id dep-id}))))

(defrecord BounceSystem [sys-map close! notify-fns thread-id]
  ISystem
  (-satisfy [system dep-id component]
    (if-not (contains? sys-map dep-id)
      (let [value (-value component)]
        {:next-system (-> system
                          (update :sys-map assoc dep-id value)
                          (update :close! (fn [close-outer!]
                                            (fn []
                                              (try
                                                (-close! component)
                                                (finally
                                                  (when close-outer!
                                                    (close-outer!)))))))
                          (update :notify-fns dissoc dep-id))
         :notify! (fn []
                    (doseq [notify! (get notify-fns dep-id)]
                      (notify! value)))})

      (throw (ex-info "Dependency already started!"
                      {:previous-value (get sys-map dep-id)
                       :new-value component}))))

  (-ask [system dep-id]
    (let [v (get sys-map dep-id ::not-found)]
      (cond
        (not= v ::not-found) {:next-system system
                              :get-dep (constantly v)}

        (or (= thread-id (get-thread-id))
            (not (contains? notify-fns dep-id)))
        (throw (ex-info "Missing dependency" {:dep-id dep-id}))

        :else (let [{:keys [notify! get-dep]} (make-notify-fn dep-id)]
                {:next-system (update-in system [:notify-fns dep-id] (fnil conj #{}) notify!)
                 :get-dep get-dep}))))

  (-snapshot [_]
    sys-map)

  (-close-fn [_]
    (fn []
      (doseq [notify! (apply concat (vals notify-fns))]
        (notify! :bounce/closing))
      (when close!
        (close!)))))

(defn ->system [{:keys [value expected-dep-ids]}]
  (map->BounceSystem {:sys-map value
                      :notify-fns (->> (for [dep-id expected-dep-ids]
                                         [dep-id #{}])
                                       (into {}))
                      :thread-id (get-thread-id)}))
