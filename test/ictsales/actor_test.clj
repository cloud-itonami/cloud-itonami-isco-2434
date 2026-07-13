(ns ictsales.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [ictsales.actor :as actor]
            [ictsales.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-bundle! st {:bundle-id "B-1" :client-id "client-1"
                                :name "team-suite"
                                :max-seats 100
                                :compatible-components #{"core"}})
    st))

(deftest commits-an-in-seats-compatible-quote
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-quote :stake :low
                 :bundle-id "B-1" :seats 80 :components #{"core"}}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-seat-quote
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-quote :stake :low
                 :bundle-id "B-1" :seats 500 :components #{"core"}}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-enterprise-quote-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-enterprise-quote :stake :high
                 :bundle-id "B-1" :seats 80 :components #{"core"}}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
