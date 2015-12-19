(ns bounce.figwheel
  (:require [bounce.core :as bc]
            [bidi.ring :as br]
            [clojure.core.async :as a :refer [go-loop]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defprotocol CLJSCompiler
  (bidi-routes [_])
  (cljs-handler [_])
  (path-for-js [_])
  (path-for-module [_ module]))

(try
  (require '[cljs.closure :as cljs]
           '[cljs.env :as cljs-env]
           '[figwheel-sidecar.system :as fs]
           '[com.stuartsierra.component :as c])

  (catch Exception e))

(defmacro assert-cljs [& body]
  (if (find-ns 'cljs.closure)
    `(do ~@body)
    `(throw (Exception. "No CLJS dependency available."))))

(defn- normalise-output-locations [{:keys [web-context-path target-path classpath-prefix modules], :or {target-path "target/cljs"} :as opts} build-mode]
  (let [output-dir (doto (io/file target-path (name build-mode))
                     .mkdirs)
        mains-dir (doto (io/file output-dir "mains" (or classpath-prefix ""))
                    .mkdirs)
        module-dir (when (not-empty modules)
                     (doto (io/file mains-dir "modules")
                       .mkdirs))]
    (-> opts
        (cond-> (empty? modules) (assoc :output-to (.getPath (io/file mains-dir "main.js"))))

        (assoc :output-dir (.getPath output-dir)
               :target-path target-path
               :asset-path web-context-path)

        (update-in [:modules] (fn [modules]
                                (when modules
                                  (->> (for [[module-key module-opts] modules]
                                         [module-key (assoc module-opts
                                                       :output-to (.getPath (io/file module-dir
                                                                                     (str (name module-key) ".js"))))])
                                       (into {}))))))))

(defn- compile-cljs! [{:keys [source-paths target-path] :as cljs-opts}]
  (assert-cljs
   (assert (not-empty source-paths) "Please provide some source-paths!")

   (let [start-time (System/nanoTime)
         cljs-compilable (reify cljs/Compilable
                           (-compile [_ opts]
                             (mapcat #(cljs/-compile % opts) source-paths))

                           (-find-sources [_ opts]
                             (mapcat #(cljs/-find-sources % opts) source-paths)))]

     (log/infof "Compiling CLJS, from %s to '%s'..." source-paths target-path)

     (try
       (log/with-logs ['cljs.closure :debug :warn]
         (cljs/build cljs-compilable cljs-opts))

       (log/infof "Compiled CLJS, from %s to '%s', in %.2fs."
                  source-paths
                  target-path
                  (/ (- (System/nanoTime) start-time) 1e9))
       (catch Exception e
         (log/errorf e "Error compiling CLJS..."))))))

(defn build-cljs! [cljs-opts]
  (assert-cljs
   (let [{:keys [output-dir] :as cljs-opts} (-> cljs-opts
                                                (merge (:build cljs-opts))
                                                (normalise-output-locations :build))]

     (compile-cljs! cljs-opts)

     (.getPath (io/file output-dir "mains")))))

(defn- watch-cljs! [{:keys [source-paths figwheel-options] :as cljs-opts}]
  (assert-cljs
   (let [{:keys [target-path web-context-path], :as cljs-opts} (-> cljs-opts
                                                                   (merge (:dev cljs-opts))
                                                                   (normalise-output-locations :dev))]

     (log/infof "Watching CLJS directories %s..." source-paths)

     (let [figwheel-system (-> (fs/create-figwheel-system {:figwheel-options figwheel-options
                                                           :all-builds [{:id "bounce"
                                                                         :source-paths source-paths
                                                                         :figwheel {:build-id "bounce"}
                                                                         :build-options cljs-opts}]
                                                           :build-ids #{"bounce"}})
                               (c/start-system))]

       (bc/->component (reify CLJSCompiler
                         (bidi-routes [_]
                           [web-context-path (-> (br/files {:dir (.getPath (io/file target-path (name :dev)))})
                                                 (br/wrap-middleware wrap-no-cache))])

                         (cljs-handler [this]
                           (br/make-handler (bidi-routes this)))

                         (path-for-js [_]
                           (format "%s/mains/main.js" web-context-path))

                         (path-for-module [_ module]
                           (format "%s/mains/modules/%s.js" web-context-path (name module))))

                       (fn []
                         (c/stop-system figwheel-system)
                         (log/info "Stopped watching CLJS.")))))))

(defn- wrap-no-cache [handler]
  (fn [req]
    (when-let [resp (handler req)]
      (-> resp
          (assoc-in [:headers "cache-control"] "no-cache")))))

(defn- pre-built-cljs-compiler [{:keys [web-context-path] :as cljs-opts}]
  (log/info "Using pre-built CLJS")

  (bc/->component (reify CLJSCompiler
                    (bidi-routes [_]
                      [web-context-path (-> (br/resources {:prefix (get-in cljs-opts [:build :classpath-prefix])})
                                            (br/wrap-middleware wrap-no-cache))])

                    (cljs-handler [this]
                      (br/make-handler (bidi-routes this)))

                    (path-for-js [_]
                      (format "%s/main.js" web-context-path))

                    (path-for-module [_ module]
                      (format "%s/modules/%s.js" web-context-path (name module))))))

(defn- pre-built? [cljs-opts]
  (let [classpath-prefix (get-in cljs-opts [:build :classpath-prefix])]
    (boolean (or (io/resource (str classpath-prefix "/modules"))
                 (io/resource (str classpath-prefix "/main.js"))))))

(defn start-cljs-compiler! [cljs-opts]
  (if-not (pre-built? cljs-opts)
    (watch-cljs! cljs-opts)
    (pre-built-cljs-compiler cljs-opts)))

(comment
  (def foo
    (start-cljs-compiler! {:source-paths #{"/tmp/project"}
                           :target-path "/tmp/project-target/cljs/"
                           :web-context-path "file:///tmp/project-target/cljs/dev"
                           :main 'example.app
                           :figwheel-options {:css-dirs ["/tmp/project/css"]}
                           :dev {:optimizations :none
                                 :pretty-print? true}}))

  ((:close! foo))

  )
