(ns bounce.system-test
  (:require [bounce.system :as b]
            [clojure.test :as t]))

(t/deftest deps-ordered-correctly
  (t/is (= [:quux :baz :bar :foo]
           (#'b/order-deps {:foo #{:bar}
                             :bar #{:baz :quux}
                             :baz #{:quux}
                             :quux #{}}
                            {:targets #{:foo :bar :baz :quux}})))

  (t/is (contains? #{[:quux :baz :bar :foo]
                     [:quux :bar :baz :foo]}
                   (#'b/order-deps {:foo #{:bar :baz}
                                     :bar #{:quux}
                                     :baz #{:quux}
                                     :quux #{}}
                                    {:targets #{:foo :bar :baz :quux}}))))

(t/deftest only-orders-requested-targets
  (t/is (= [:quux :baz :bar]
           (#'b/order-deps {:foo #{:bar}
                             :bar #{:baz :quux}
                             :baz #{:quux}
                             :quux #{}}
                            {:targets #{:bar}})))

  (t/is (= [:quux :bar]
           (#'b/order-deps {:foo #{:bar :baz}
                             :bar #{:quux}
                             :baz #{:quux}
                             :quux #{}}
                            {:targets #{:bar}}))))

(defn happy-c1 [!events]
  (fn []
    (swap! !events conj :start-c1)
    (b/->component :c1-value
                    (fn []
                      (swap! !events conj :stop-c1)))))

(defn happy-c2 [!events]
  (-> (fn []
        (swap! !events conj [:start-c2 (b/ask :c1)])
        (b/->component :c2-value
                        (fn []
                          (swap! !events conj :stop-c2))))

      (b/using #{:c1})))

(defn sad-c2 [!events]
  (-> (fn []
        (swap! !events conj :c2-fails)
        (throw (ex-info "bleurgh" {})))

      (b/using #{:c1})))

(t/deftest system-e2e
  (let [!events (atom [])]
    (b/with-system (b/make-system {:c1 (happy-c1 !events)

                                     :c2 (happy-c2 !events)})
      (fn []
        (swap! !events conj [:system-up (b/snapshot)])))

    (t/is (= @!events [:start-c1 [:start-c2 :c1-value]
                       [:system-up {:c1 :c1-value, :c2 :c2-value}]
                       :stop-c2 :stop-c1]))))

(t/deftest system-fail-e2e
  (let [!events (atom [])]
    (t/is (thrown-with-msg? Exception #"bleurgh"
                            (b/make-system {:c1 (happy-c1 !events)

                                             :c2 (sad-c2 !events)})))

    (t/is (= @!events [:start-c1 :c2-fails :stop-c1]))))

(t/deftest async-ask
  (let [!events (atom [])
        !async-event (promise)]
    (b/with-system (b/make-system {:c1 (fn []
                                           (swap! !events conj :start-c1)

                                           (future
                                             (deliver !async-event (b/ask :c2)))

                                           (b/->component :c1-value
                                                           (fn []
                                                             (swap! !events conj :stop-c1))))

                                     :c2 (happy-c2 !events)})
      (fn []))

    (t/is (= @!events [:start-c1 [:start-c2 :c1-value] :stop-c2 :stop-c1]))
    (t/is (= :c2-value (deref !async-event 500 nil)))))

(t/deftest async-ask-handles-error
  (let [!events (atom [])
        !async-event (promise)]
    (t/is (thrown? Exception (b/make-system {:c1 (fn []
                                                    (swap! !events conj :start-c1)

                                                    (future
                                                      (try
                                                        (b/ask :c2)
                                                        (catch Exception e
                                                          (deliver !async-event :error-thrown))))

                                                    (b/->component :c1-value
                                                                    (fn []
                                                                      (swap! !events conj :stop-c1))))

                                              :c2 (sad-c2 !events)})))

    (t/is (= @!events [:start-c1 :c2-fails :stop-c1]))
    (t/is (= :error-thrown (deref !async-event 500 nil)))))
