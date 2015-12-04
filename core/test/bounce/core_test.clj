(ns bounce.core-test
  (:require [bounce.core :as bc]
            [clojure.test :as t]))

(t/deftest deps-ordered-correctly
  (t/is (= [:quux :baz :bar :foo]
           (#'bc/order-deps {:foo #{:bar}
                             :bar #{:baz :quux}
                             :baz #{:quux}
                             :quux #{}}
                            {:targets #{:foo :bar :baz :quux}})))

  (t/is (contains? #{[:quux :baz :bar :foo]
                     [:quux :bar :baz :foo]}
                   (#'bc/order-deps {:foo #{:bar :baz}
                                     :bar #{:quux}
                                     :baz #{:quux}
                                     :quux #{}}
                                    {:targets #{:foo :bar :baz :quux}}))))

(t/deftest only-orders-requested-targets
  (t/is (= [:quux :baz :bar]
           (#'bc/order-deps {:foo #{:bar}
                             :bar #{:baz :quux}
                             :baz #{:quux}
                             :quux #{}}
                            {:targets #{:bar}})))

  (t/is (= [:quux :bar]
           (#'bc/order-deps {:foo #{:bar :baz}
                             :bar #{:quux}
                             :baz #{:quux}
                             :quux #{}}
                            {:targets #{:bar}}))))

(defn happy-c1 [!events]
  (fn []
    (swap! !events conj :start-c1)
    (bc/->component :c1-value
                    (fn []
                      (swap! !events conj :stop-c1)))))

(defn happy-c2 [!events]
  (-> (fn []
        (swap! !events conj [:start-c2 (bc/ask :c1)])
        (bc/->component :c2-value
                        (fn []
                          (swap! !events conj :stop-c2))))

      (bc/using #{:c1})))

(defn sad-c2 [!events]
  (-> (fn []
        (swap! !events conj :c2-fails)
        (throw (ex-info "bleurgh" {})))

      (bc/using #{:c1})))

(t/deftest system-e2e
  (let [!events (atom [])]
    (bc/with-system (bc/make-system {:c1 (happy-c1 !events)

                                     :c2 (happy-c2 !events)})
      (fn []
        (swap! !events conj [:system-up (bc/snapshot)])))

    (t/is (= @!events [:start-c1 [:start-c2 :c1-value]
                       [:system-up {:c1 :c1-value, :c2 :c2-value}]
                       :stop-c2 :stop-c1]))))

(t/deftest system-fail-e2e
  (let [!events (atom [])]
    (t/is (thrown-with-msg? Exception #"bleurgh"
                            (bc/make-system {:c1 (happy-c1 !events)

                                             :c2 (sad-c2 !events)})))

    (t/is (= @!events [:start-c1 :c2-fails :stop-c1]))))

(t/deftest async-ask
  (let [!events (atom [])
        !async-event (promise)]
    (bc/with-system (bc/make-system {:c1 (fn []
                                           (swap! !events conj :start-c1)

                                           (future
                                             (deliver !async-event (bc/ask :c2)))

                                           (bc/->component :c1-value
                                                           (fn []
                                                             (swap! !events conj :stop-c1))))

                                     :c2 (happy-c2 !events)})
      (fn []))

    (t/is (= @!events [:start-c1 [:start-c2 :c1-value] :stop-c2 :stop-c1]))
    (t/is (= :c2-value (deref !async-event 500 nil)))))

(t/deftest async-ask-handles-error
  (let [!events (atom [])
        !async-event (promise)]
    (t/is (thrown? Exception (bc/make-system {:c1 (fn []
                                                    (swap! !events conj :start-c1)

                                                    (future
                                                      (try
                                                        (bc/ask :c2)
                                                        (catch Exception e
                                                          (deliver !async-event :error-thrown))))

                                                    (bc/->component :c1-value
                                                                    (fn []
                                                                      (swap! !events conj :stop-c1))))

                                              :c2 (sad-c2 !events)})))

    (t/is (= @!events [:start-c1 :c2-fails :stop-c1]))
    (t/is (= :error-thrown (deref !async-event 500 nil)))))
