(defproject jarohen/bounce.examples.todomvc ""
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.reader "0.9.2"]
                 [jarohen/embed-nrepl "0.1.7"]

                 [jarohen/bounce "0.0.1-alpha1"]

                 [ring/ring-core "1.3.2"]
                 [jarohen/bounce.aleph "0.0.1-alpha1"]
                 [bidi "1.21.1"]
                 [hiccup "1.0.5"]
                 [garden "1.2.1"]
                 [ring-middleware-format "0.5.0" :exclusions [ring]]

                 [traversy "0.4.0"]

                 [org.webjars/jquery "2.1.4"]
                 [org.webjars/bootstrap "3.3.5"]

                 [jarohen/bounce.figwheel "0.0.1-alpha1"]

                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.9"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.1"]
                 [org.apache.logging.log4j/log4j-core "2.1"]]

  :exclusions [org.clojure/clojure org.clojure/clojurescript]

  :profiles {:dev {:dependencies [[org.clojure/clojurescript "1.7.170"]
                                  [jarohen/bounce.mux "0.0.1-alpha2"]
                                  [lein-figwheel "0.5.0-2"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [reagent "0.5.1"]]}}

  :auto-clean false

  :filespecs [{:type :path, :path "target/cljs/build/mains"}]

  :aliases {"dev" ["run" "-m" "todomvc.service.main"]
            "build" ["do"
                     "clean"
                     ["run" "-m" "todomvc.service.main/build!"]
                     "uberjar"]})
