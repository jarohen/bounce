(ns bounce.mux.bidi
  (:require [bounce.mux :as mux]
            [bidi.bidi :as bidi]))

(defn token-mapper [bidi-routes]
  (reify mux/TokenMapper
    (token->location [_ token]
      (bidi/match-route bidi-routes token))

    (location->token [_ {:keys [handler route-params]}]
      (bidi/unmatch-pair bidi-routes {:handler handler
                                      :params route-params}))))
