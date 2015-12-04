(ns bounce.system-test
  (:require [bounce.core :as bc]
            [bounce.system :as bs]
            [clojure.test :as t]))

(t/deftest closes-values-in-reverse-order
  (let [!closed-values (atom [])
        system (-> (bs/->system {:value {}
                                 :expected-dep-ids #{:foo :bar}})

                   (bs/-satisfy :foo (bc/->component :foo-value
                                                     (fn []
                                                       (swap! !closed-values conj :foo))))
                   :next-system

                   (bs/-satisfy :bar (bc/->component :bar-value
                                                     (fn []
                                                       (swap! !closed-values conj :bar))))

                   :next-system)]

    ((bs/-close-fn system))

    (t/is (= [:bar :foo] @!closed-values))))

(t/deftest ask-returns-satisfied-value
  (let [system (-> (bs/->system {:value {}
                                 :expected-dep-ids #{:foo}})
                   (bs/-satisfy :foo (bc/->component :foo-value))

                   :next-system)
        {:keys [next-system get-dep]} (bs/-ask system :foo)]

    (t/is (= next-system system))
    (t/is (= (get-dep) :foo-value))))

(t/deftest unsatisfied-values-throw
  (let [system (bs/->system {:value {}
                             :expected-dep-ids #{:foo}})]
    (t/is (thrown? Exception (bs/-ask system :foo)))

    (t/is (thrown? Exception (bs/-ask system :bar)))))

(t/deftest unsatisfied-values-in-different-threads-block
  (let [system (bs/->system {:value {}
                             :expected-dep-ids #{:foo}})
        !system-after-ask (promise)
        !future (future
                  (let [{:keys [get-dep next-system]} (bs/-ask system :foo)]
                    (deliver !system-after-ask next-system)
                    (get-dep)))

        {:keys [notify! next-system]} (-> @!system-after-ask
                                          (bs/-satisfy :foo (bc/->component :foo-value)))]

    (notify!)

    (t/is (= (deref !future 500 nil) :foo-value))))
