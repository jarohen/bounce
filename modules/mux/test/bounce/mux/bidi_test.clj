(ns bounce.mux.bidi-test
  (:require [bounce.mux.protocols :as p]
            [bounce.mux.bidi :refer :all]
            [bidi.bidi :as bidi]
            [clojure.test :as t]))

(def test-mapper
  (token-mapper ["" {["/foo" "/" [bidi/uuid :id]] ::handler}]))

(def test-location
  {:route-params {:id #uuid "634ea5e6-523a-41bc-9ee8-d69a48984b7b"},
   :handler ::handler
   :query-params {"email" "james@example.com"
                  "bar" "baz"}})

(def test-url
  "/foo/634ea5e6-523a-41bc-9ee8-d69a48984b7b?bar=baz&email=james%40example.com")

(t/deftest parses-token
  (t/is (= (p/-token->location test-mapper test-url)
           test-location)))

(t/deftest unparses-token
  (t/is (= (p/-location->token test-mapper test-location)
           test-url)))
