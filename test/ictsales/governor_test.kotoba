(ns ictsales.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [ictsales.store :as store]
            [ictsales.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-bundle! st {:bundle-id "B-1" :client-id "client-1"
                                :name "team-suite"
                                :max-seats 100
                                :compatible-components #{"core" "analytics" "sso"}})
    st))

(defn- mkquote [seats components]
  {:op :approve-quote :effect :propose :bundle-id "B-1"
   :seats seats :components components :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-seats-and-compatible-components
  (let [st (fresh-store)
        v (governor/check req {} (mkquote 80 #{"core" "sso"}) st)]
    (is (:ok? v))))

(deftest ok-at-exact-seat-ceiling
  (testing "seat count exactly at the ceiling is within margin"
    (let [st (fresh-store)
          v (governor/check req {} (mkquote 100 #{"core"}) st)]
      (is (:ok? v)))))

(deftest hard-on-seat-count-exceeds-ceiling
  (testing "seat-count arithmetic is not a negotiating position"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (mkquote 150 #{"core"}) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :seat-count-exceeds-ceiling (:rule %)) (:violations v))))))

(deftest hard-on-incompatible-component
  (testing "incompatibility is a matrix lookup, not a sales pitch"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (mkquote 80 #{"legacy-plugin"}) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :incompatible-component (:rule %)) (:violations v))))))

(deftest hard-on-unknown-bundle
  (let [st (fresh-store)
        v (governor/check req {} (assoc (mkquote 80 #{"core"}) :bundle-id "B-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-bundle (:rule %)) (:violations v)))))

(deftest hard-on-foreign-bundle
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (mkquote 80 #{"core"}) st)]
      (is (:hard? v))
      (is (some #(= :bundle-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (mkquote 80 #{"core"}) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (mkquote 80 #{"core"}) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-enterprise-quote
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-enterprise-quote :effect :propose
                                  :bundle-id "B-1" :seats 80 :components #{"core"}
                                  :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (mkquote 80 #{"core"}) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
