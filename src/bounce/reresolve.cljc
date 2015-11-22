(ns bounce.reresolve)

(defn with-reresolve [v]
  #?(:clj (cond
            (var? v) (with-reresolve (symbol (str (ns-name (:ns (meta v))))
                                             (str (:name (meta v)))))

            (symbol? v) (let [v-ns (symbol (namespace v))
                              v-name (symbol (name v))]
                          (fn [& args]
                            (require v-ns)

                            (apply (or (ns-resolve (find-ns v-ns) v-name)
                                       (throw (ex-info "Can't resolve system-fn!"
                                                       {:sym v})))
                                   args)))

            :else v)

     :cljs v))
