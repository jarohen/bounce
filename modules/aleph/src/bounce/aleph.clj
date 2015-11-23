(ns bounce.aleph
  (:require [bounce.core :as bc]
            [aleph.http :as http]
            [clojure.tools.logging :as log]))

(defn start-server!
  " :: {server-opts} -> Component <Server>

  This returns a Component containing a started HTTP-kit server

  Usage:
    (require '[bounce.aleph :refer [start-server!]]
             '[bounce.core :as yc])

    (bc/with-component (start-server! {...})
      (fn [server-opts]
        ;; ...
        ))

  - handler : a ring handler
  - server-opts : {:port 3000
                   ... ; anything else you'd pass to aleph.http/start-server
                  }

  If the handler returns `nil` for a given request, will return a simple 404."
  [{:keys [handler server-opts]}]

  (log/infof "Starting web server on port %d..." (:port server-opts))
  (let [server (http/start-server (some-fn handler
                                           (constantly {:status 404
                                                        :body "Not found"}))

                                  server-opts)]
    (log/info "Started web server.")

    (bc/->component server
                    (fn []
                      (log/info "Stopping web server...")
                      (.close server)
                      (log/info "Stopped web server.")))))
