(ns bounce.mux.bidi
  (:require [bounce.mux.protocols :as p]
            [bidi.bidi :as bidi]
            [cemerick.url :as u]
            [clojure.string :as s]))

(defn token-mapper [bidi-routes]
  (reify p/TokenMapper
    (-token->location [_ token]
      (let [[path query-params] (s/split token #"\?")]
        (when-let [matched-route (bidi/match-route bidi-routes path)]
          (merge matched-route {:query-params (u/query->map query-params)}))))

    (-location->token [_ {:keys [handler route-params query-params]}]
      (when-let [unmatched-route (bidi/unmatch-pair bidi-routes {:handler handler
                                                                 :params route-params})]
        (cond-> unmatched-route
          (not-empty query-params) (str "?" (u/map->query query-params)))))))
