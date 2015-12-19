(ns leiningen.new.bounce-webapp
  (:require [clojure.java.io :as io]
            [clojure.tools.reader.edn :as edn]
            [leiningen.new.templates :refer [renderer name-to-path ->files]]))

(def render (renderer "bounce-webapp"))

(defn bounce-webapp
  "Create a new Bounce Single Page Application"
  [app-name]
  (println "Creating a new Bounce Single Page Application...")

  (let [data {:name app-name
              :sanitized (name-to-path app-name)}]

    (->files data
             ["project.clj" (render "project.clj" data)]
             [".gitignore" (render "gitignore" data)]
             ["resources/log4j2.json" (render "resources/log4j2.json" data)]

             ["src/{{sanitized}}/service/main.clj" (render "src/main.clj" data)]

             ["src/{{sanitized}}/service/handler.clj" (render "src/handler.clj" data)]
             ["src/{{sanitized}}/service/cljs.clj" (render "src/cljs.clj" data)]
             ["src/{{sanitized}}/service/css.clj" (render "src/css.clj" data)]

             ["ui-src/{{sanitized}}/ui/app.cljs" (render "ui-src/app.cljs" data)]))

  (println "Created!")
  (println "To start the application, run `lein dev`, and then go to http://localhost:3000"))
