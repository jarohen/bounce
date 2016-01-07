(defproject jarohen/bounce.mux "0.0.1-alpha2"
  :description "A CLJS router for Bounce systems, based on Bidi"
  :url "https://github.com/jarohen/bounce"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.cemerick/url "0.1.1" :exclusions [org.clojure/clojurescript]]]

  :profiles {:dev {:dependencies [[org.clojure/clojurescript "1.7.170"]
                                  [bidi "1.21.1"]]}})
