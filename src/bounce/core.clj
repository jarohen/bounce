(ns bounce.core)

(def ^:private ^:dynamic *system*
  {})

(defn ->component
  ([v]
   (->component v (fn [])))
  ([v close!]
   ))

(defn using [component dependencies]
  )

(defn with-component [component f]
  )

(defn with-system [system f]
  )

(defn make-system [dep-map]
  )

(defn ask [k & ks]
  )

(defn system []
  )

(defn set-system-fn! [f]
  )

(defn start! []
  )

(defn stop! []
  )

(defn reload! []
  )

;; example code

(comment
  (defn foo-make-my-system []
    (b/make-system {:config (fn []
                              (b/->component ...))

                    :server (-> (fn []
                                  (b/->component ...
                                                 (fn []
                                                   ...)))

                                (b/using #{:config}))})))
