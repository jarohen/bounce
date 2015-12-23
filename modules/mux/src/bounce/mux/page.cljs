(ns bounce.mux.page)

(defprotocol IPage
  (-value [_])
  (-unmount! [_ location]))

(defrecord Page [value unmount-fn]
  IPage
  (-value [_] value)
  (-unmount! [_ location] (unmount-fn location)))
