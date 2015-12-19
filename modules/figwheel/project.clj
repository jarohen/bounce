(defproject jarohen/bounce.figwheel "0.0.1-SNAPSHOT"
  :description "A module to automatically re-compile ClojureScript files within a Bounce system."
  :url "https://github.com/james-henderson/bounce"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]

                 [bidi "1.21.1"]

                 [jarohen/bounce "0.0.1-20151208.152024-7"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.logging "0.3.1"]]

  :profiles {:dev {:dependencies [[lein-figwheel "0.5.0-2"]
                                  [org.clojure/clojurescript "1.7.170"]]}})
