(ns bounce.jdbc-pool
  (:require [bounce.core :as bc]
            [clojure.tools.logging :as log])
  (:import [org.apache.commons.dbcp2 BasicDataSource]))

(def known-drivers
  {"postgresql" "org.postgresql.Driver"
   "mysql" "com.mysql.jdbc.Driver"
   "mssql" "com.microsoft.sqlserver.jdbc.SQLServerDriver"
   "odbc" "sun.jdbc.odbc.JdbcOdbcDriver"
   "sqlite" "org.sqlite.JDBC"
   "h2" "org.h2.Driver"})

(defn open-db-pool
  " :: pool-config -> Component {:datasource ...}

  This returns a Component containing a started JDBC database pool.

  Usage:
    (require '[bounce.jdbc-pool :as db]
             '[bounce.core :as bc]
             '[clojure.java.jdbc :as jdbc])

    (bc/with-component (open-db-pool {...})
      (fn [db-pool]
        (jdbc/with-db-transaction [tx db-pool]
          (jdbc/query tx
                      \"SELECT * FROM users\"))))

  Will guess the driver based on the subprotocol, if driver is omitted."

  [{:keys [driver subprotocol host port username password db max-total max-idle]}]

  (log/info "Starting JDBC pool...")

  (let [pool {:datasource (doto (BasicDataSource.)
                            (.setDriverClassName (or driver (get known-drivers subprotocol)))
                            (.setAccessToUnderlyingConnectionAllowed true)
                            (.setUrl (format "jdbc:%s://%s:%s/%s" subprotocol host port db))
                            (.setUsername username)
                            (.setPassword password)
                            (cond-> max-total (.setMaxTotal max-total))
                            (cond-> max-idle (.setMaxIdle max-idle)))}]

    (log/info "Started JDBC pool.")

    (bc/->component pool
                    (fn []
                      (log/info "Stopping JDBC pool...")
                      (.close (:datasource pool))
                      (log/info "Stopped JDBC pool...")))))
