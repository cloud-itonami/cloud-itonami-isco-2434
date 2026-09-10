(ns ictsales.governor
  "ICTSalesGovernor — the independent safety/traceability layer for
  the ISCO-08 2434 community ICT sales professionals actor (itonami
  actor pattern, ADR-2607011000 / CLAUDE.md Actors section). Modeled
  on cloud-itonami-isco-4311's bookkeeping.governor. ICT-sales twist:
  a quote's seat count is arithmetic comparison against the registered
  tier ceiling, and every included component must be a member of the
  registered compatible-components set — an incompatible bundle is a
  matrix lookup, not a sales pitch.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. bundle basis        — a quote approval must cite a REGISTERED
                           bundle belonging to this client.
    4. seat-count ceiling  — the proposed seat count must not exceed
                           the bundle's registered :max-seats
                           (arithmetic, not a negotiating position).
    5. component compatibility — every proposed component must be a
                           member of the bundle's registered
                           :compatible-components set (incompatibility
                           is a matrix lookup, not a sales pitch).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-enterprise-quote (large-account commitment).
    7. low confidence (< `confidence-floor`)."
  (:require [clojure.set :as set]
            [ictsales.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record b]
  (let [{:keys [op seats components]} proposal
        approve? (= :approve-quote op)
        incompatible (when b (set/difference (set components) (:compatible-components b)))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and approve? (nil? b))
      (conj {:rule :unknown-bundle :detail "未登録 bundle への見積承認は不可"})

      (and approve? b (not= (:client-id b) (:client-id request)))
      (conj {:rule :bundle-wrong-client :detail "bundle が別 client のもの"})

      (and approve? b (number? seats) (> seats (:max-seats b)))
      (conj {:rule :seat-count-exceeds-ceiling
             :detail (str "席数 " seats " > 登録済み上限 " (:max-seats b)
                          "（席数算術は交渉材料ではない）")})

      (and approve? b (seq incompatible))
      (conj {:rule :incompatible-component
             :detail (str "非互換コンポーネント " (vec incompatible)
                          "（互換性はマトリクス照合であって販売トークではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `ictsales.store/Store`. Pure — never mutates
  the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        b (some->> (:bundle-id proposal) (store/bundle store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record b)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :approve-enterprise-quote (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
