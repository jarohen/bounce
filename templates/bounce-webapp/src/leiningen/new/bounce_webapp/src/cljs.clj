(ns {{name}}.service.cljs
  (:require [bounce.figwheel :as cljs]))

(def cljs-config
  {:source-paths ["ui-src"]
   :target-path "target/cljs/"

   :main '{{name}}.ui.app

   :web-context-path "/js"

   :figwheel {:client {:on-jsload "{{name}}.ui.app/render-page!"}}

   :dev {:optimizations :none
         :pretty-print? true}

   :build {:optimizations :advanced
           :pretty-print? false
           :classpath-prefix "js"}})

(defn make-cljs-compiler []
  (cljs/start-cljs-compiler! cljs-config))

(defn build-cljs! []
  (cljs/build-cljs! cljs-config))
