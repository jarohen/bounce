(ns bounce.mux.protocols)

(defprotocol Router
  (-set-location! [router location])
  (-replace-location! [router location])
  (-link [router location]))

(defprotocol TokenMapper
  (-token->location [token-mapper token])
  (-location->token [token-mapper location]))

(defprotocol IPage
  (-value [_])
  (-unmount! [_ location]))

(defrecord Page [value unmount-fn]
  IPage
  (-value [_] value)
  (-unmount! [_ location] (unmount-fn location)))
