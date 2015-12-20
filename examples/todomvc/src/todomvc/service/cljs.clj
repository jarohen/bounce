(ns todomvc.service.cljs
  (:require [bounce.figwheel :as cljs]))

(def cljs-config
  {:source-paths ["ui-src"]
   :target-path "target/cljs/"

   :web-context-path "/js"

   :figwheel {:client {:on-jsload "todomvc.ui.app/render-page!"}}

   :dev {:optimizations :none
         :main 'todomvc.ui.app
         :pretty-print? true}

   :build {:optimizations :advanced
           :pretty-print? false
           :classpath-prefix "js"}})

(defn make-cljs-compiler []
  (cljs/start-cljs-compiler! cljs-config))

(defn build-cljs! []
  (cljs/build-cljs! cljs-config))
