(defproject jarohen/bounce.jdbc-pool "0.0.1-alpha1"
  :description "A module to set up a JDBC connection pool in Bounce"
  :url "https://github.com/james-henderson/bounce"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [jarohen/bounce "0.0.1-alpha1"]
                 [org.clojure/tools.logging "0.3.1"]

                 [org.clojure/java.jdbc "0.3.5"]
                 [org.apache.commons/commons-dbcp2 "2.0.1"]])
