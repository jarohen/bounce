(ns bounce.http-kit
  (:require [bounce.core :as bc]
            [org.httpkit.server :as http]
            [clojure.tools.logging :as log]))

(defn start-server!
  " :: {server-opts} -> Component {server-opts}

  This returns a Component containing a started HTTP-kit server

  Usage:
    (require '[bounce.http-kit :refer [start-server!]]
             '[bounce.core :as bc])

    (bc/with-component (start-server! {...})
      (fn [server-opts]
        ;; ...
        ))

  - handler : a ring handler
  - server-opts : {:port 3000
                   ... ; anything else you'd pass to org.httpkit.server/run-server
                  }

  If the handler returns `nil` for a given request, will return a simple 404."

  [{:keys [handler server-opts] :as http-kit-opts}]

  (log/infof "Starting web server on port %d..." (:port server-opts))
  (let [stop-server! (http/run-server (some-fn handler
                                               (constantly {:status 404
                                                            :body "Not found"}))

                                      server-opts)]
    (log/info "Started web server.")
    (bc/->component http-kit-opts
                    (fn []
                      (log/info "Stopping web server...")
                      (stop-server!)
                      (log/info "Stopped web server.")))))
