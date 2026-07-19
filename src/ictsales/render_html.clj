(ns ictsales.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.
  Closes the ISCO no-demo checklist item for `cloud-itonami-isco-2434`
  (ISCO-08 2434 community, `ictsales` domain) -- this repo previously
  had NO demo page and no generator at all. Pattern lifted from
  `cloud-itonami-isco-1211`'s `finmgmt.render-html` (cloud-itonami
  maturity loop iter9, ADR-2607189200), adapted to this repo's own
  real `ictsales.actor` / `ictsales.governor` / `ictsales.store` shape.

  This namespace drives the REAL actor stack (`ictsales.actor` ->
  `ictsales.governor` -> `ictsales.store`) through a scenario built
  from real, exercised store data and renders the result
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed.

  `client-1` (\"Kobo Trade\") + bundle `B-1` (\"team-suite\", max-seats
  100, compatible-components #{\"core\"}) are lifted VERBATIM from the
  proven-passing `test/ictsales/actor_test.clj` `fresh-store` fixture
  (the ground-truth fixture per this repo's own actor-level tests;
  `governor_test.clj` uses a wider `#{\"core\" \"analytics\" \"sso\"}`
  compatible-components set for its own broader ok/incompatible-component
  cases -- that variant is NOT used here, only noted).

  `client-2` (\"Second Co\") and bundle `B-2` (\"starter-suite\",
  max-seats 20, compatible-components #{\"core\" \"sso\"}) are
  ADDITIONAL demo data registered via the store's own real
  `register-client!`/`register-bundle!` protocol calls -- disclosed
  here plainly, not presented as if pre-existing fixture. `client-2`
  exists to demonstrate `:bundle-wrong-client` (a request from
  client-2 citing client-1's bundle `B-1`) and to prove the graph
  handles a second, independent client cleanly.

  Every other field this page displays is real output read after
  `run-demo!` actually executed the graph.

  Governor coverage (`ictsales.governor`, confirmed by reading the
  real source): of its 6 HARD rules + 2 ESCALATION rules, this
  scenario reaches 6 hard rules (`:no-client` / `:unknown-bundle` /
  `:bundle-wrong-client` / `:seat-count-exceeds-ceiling` /
  `:incompatible-component`) plus the `:approve-enterprise-quote`
  escalation, all through the REAL `ictsales.advisor/mock-advisor` ->
  `run-request!`/`approve!` path. Two rules are structurally
  UNREACHABLE through that real path and are honestly excluded from
  the scenario rather than faked:
    - `:no-actuation` -- `ictsales.advisor/infer` unconditionally sets
      `:effect :propose` on every proposal it produces; there is no
      request shape that makes the real mock-advisor emit a non-
      `:propose` effect. `governor_test.clj`'s
      `hard-on-no-actuation-violation` only reaches this rule by
      calling `governor/check` directly with a hand-built proposal
      that bypasses the advisor entirely -- which this page's harness
      deliberately does not do (it only drives the real graph).
    - low-confidence escalation (`confidence < 0.6`) -- `infer`'s
      confidence table is `:high -> 0.7, :medium -> 0.85, :low ->
      0.95`; every stake value the real mock-advisor supports produces
      confidence >= 0.7, above the 0.6 floor. No real request drives
      this branch.

  Usage: `clojure -M:render-html [out-file]` (default
  `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [ictsales.store :as store]
            [ictsales.actor :as actor]))

(defn- run-op! [graph tid client-id op extra]
  (let [request (merge {:client-id client-id :op op} extra)
        r1 (actor/run-request! graph request {} tid)]
    (if (= :interrupted (:status r1))
      (let [r2 (actor/approve! graph tid)]
        {:thread-id tid :client-id client-id :op op :request request
         :outcome :approved-and-committed
         :record (get-in r2 [:state :record])})
      (let [disposition (get-in r1 [:state :disposition])]
        (if (= :hold disposition)
          {:thread-id tid :client-id client-id :op op :request request
           :outcome :hard-hold
           :verdict (get-in r1 [:state :verdict])
           :rule (-> r1 :state :verdict :violations first :rule)}
          {:thread-id tid :client-id client-id :op op :request request
           :outcome :auto-committed
           :record (get-in r1 [:state :record])})))))

(def ^:private op-specs
  [["c1-approve-quote-ok" "client-1" :approve-quote
    {:stake :low :bundle-id "B-1" :seats 80 :components #{"core"}}]
   ["c1-over-seat-ceiling" "client-1" :approve-quote
    {:stake :low :bundle-id "B-1" :seats 500 :components #{"core"}}]
   ["c1-incompatible-component" "client-1" :approve-quote
    {:stake :low :bundle-id "B-1" :seats 50 :components #{"legacy-plugin"}}]
   ["c1-unknown-bundle" "client-1" :approve-quote
    {:stake :low :bundle-id "B-ghost" :seats 50 :components #{"core"}}]
   ["nobody-no-client" "nobody" :approve-quote
    {:stake :low :bundle-id "B-1" :seats 50 :components #{"core"}}]
   ["c2-bundle-wrong-client" "client-2" :approve-quote
    {:stake :low :bundle-id "B-1" :seats 10 :components #{"core"}}]
   ["c2-approve-quote-ok" "client-2" :approve-quote
    {:stake :low :bundle-id "B-2" :seats 15 :components #{"core"}}]
   ["c1-enterprise-quote" "client-1" :approve-enterprise-quote
    {:stake :high :bundle-id "B-1" :seats 80 :components #{"core"}}]])

(defn run-demo! []
  (let [db (store/mem-store)]
    (store/register-client! db {:client-id "client-1" :name "Kobo Trade"})
    (store/register-bundle! db {:bundle-id "B-1" :client-id "client-1"
                                :name "team-suite"
                                :max-seats 100
                                :compatible-components #{"core"}})
    (store/register-client! db {:client-id "client-2" :name "Second Co"})
    (store/register-bundle! db {:bundle-id "B-2" :client-id "client-2"
                                :name "starter-suite"
                                :max-seats 20
                                :compatible-components #{"core" "sso"}})
    (let [graph (actor/build-graph {:store db})
          runs (mapv (fn [[tid client-id op extra]]
                       (run-op! graph tid client-id op extra))
                     op-specs)]
      {:store db :runs runs})))

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- outcome-cell [{:keys [outcome rule]}]
  (case outcome
    :auto-committed "<span class=\"ok\">committed</span>"
    :approved-and-committed "<span class=\"ok\">approved &amp; committed</span>"
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")
    "<span class=\"muted\">in progress</span>"))

(defn- clients-rows [db client-ids]
  (->> client-ids
       (map (fn [cid]
              (let [c (store/client db cid)]
                (str "<tr><td>" (esc (:client-id c)) "</td><td>" (esc (:name c)) "</td></tr>"))))
       (str/join "\n")))

(defn- bundles-rows [db bundle-ids]
  (->> bundle-ids
       (map (fn [bid]
              (let [b (store/bundle db bid)]
                (str "<tr><td>" (esc (:bundle-id b)) "</td><td>" (esc (:client-id b))
                     "</td><td>" (esc (:name b)) "</td><td>" (esc (:max-seats b))
                     "</td><td>" (esc (str/join ", " (sort (:compatible-components b))))
                     "</td></tr>"))))
       (str/join "\n")))

(defn- request-summary [{:keys [op bundle-id seats components]}]
  (str (name op) " &middot; bundle=" (esc bundle-id)
       " &middot; seats=" (esc seats)
       " &middot; components={" (esc (str/join "," (sort components))) "}"))

(defn- audit-rows [runs]
  (->> runs
       (map (fn [{:keys [thread-id client-id request] :as r}]
              (str "<tr><td><code>" (esc thread-id) "</code></td><td>" (esc client-id)
                   "</td><td>" (request-summary request) "</td><td>" (outcome-cell r) "</td></tr>")))
       (str/join "\n")))

(def ^:private gate-rows
  [["HARD" ":no-client" "client provenance -- the organization must be registered."]
   ["HARD" ":no-actuation" "proposal :effect must be :propose (structurally unreachable via the real mock-advisor -- see namespace docstring)."]
   ["HARD" ":unknown-bundle" "a quote approval must cite a REGISTERED bundle belonging to this client."]
   ["HARD" ":bundle-wrong-client" "the cited bundle must belong to the requesting client."]
   ["HARD" ":seat-count-exceeds-ceiling" "proposed seat count must not exceed the bundle's registered :max-seats (arithmetic, not a negotiating position)."]
   ["HARD" ":incompatible-component" "every proposed component must be a member of the bundle's registered :compatible-components set (a matrix lookup, not a sales pitch)."]
   ["ESCALATE" ":approve-enterprise-quote" "large-account commitment always requires human sign-off (interrupt-before :request-approval, resumed by approve!)."]
   ["ESCALATE" "low confidence (< 0.6)" "confidence floor is 0.6; every stake value the real mock-advisor supports produces >= 0.7, so this branch is not reachable in this scenario (noted honestly, not faked)."]])

(defn- gate-table-rows []
  (->> gate-rows
       (map (fn [[kind rule detail]]
              (str "<tr><td><span class=\""
                   (if (= kind "HARD") "critical" "warn") "\">" kind "</span></td><td><code>"
                   (esc rule) "</code></td><td>" (esc detail) "</td></tr>")))
       (str/join "\n")))

(def ^:private style
  "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:0;padding:2rem;background:#0b0d12;color:#e6e9ef}
h1{font-size:1.4rem;margin:0 0 .25rem}
h2{font-size:1.05rem;margin:2rem 0 .5rem;color:#9fb3c8}
.sub{color:#8792a3;margin:0 0 1.5rem;font-size:.9rem}
table{width:100%;border-collapse:collapse;margin-bottom:1rem;font-size:.88rem}
th,td{text-align:left;padding:.45rem .6rem;border-bottom:1px solid #232833}
th{color:#8792a3;font-weight:600;text-transform:uppercase;font-size:.72rem;letter-spacing:.03em}
code{background:#151922;padding:.1rem .35rem;border-radius:4px;font-size:.85em}
.ok{color:#4ade80;font-weight:600}
.warn{color:#fbbf24;font-weight:600}
.critical{color:#f87171;font-weight:600}
.muted{color:#8792a3}
.card{background:#12151c;border:1px solid #232833;border-radius:10px;padding:1.25rem 1.5rem;margin-bottom:1.5rem}")

(defn render [{:keys [store runs]}]
  (str/join
   "\n"
   ["<!doctype html>"
    "<html><head><meta charset=\"utf-8\">"
    "<title>ictsales operator console -- cloud-itonami-isco-2434</title>"
    (str "<style>" style "</style></head><body>")
    "<h1>ictsales operator console</h1>"
    (str "<p class=\"sub\">ISCO-08 2434 &middot; community ICT sales professionals actor &middot; "
         "generated at build time by driving the real <code>ictsales.actor</code> StateGraph "
         "(<code>intake &rarr; advise &rarr; govern &rarr; decide &rarr; commit/hold</code>, "
         "human-approval interrupt for escalations) &mdash; no invented data, no timestamps.</p>")

    "<div class=\"card\">"
    "<h2>Registered clients</h2>"
    "<table><thead><tr><th>client-id</th><th>name</th></tr></thead><tbody>"
    (clients-rows store ["client-1" "client-2"])
    "</tbody></table>"
    "<h2>Registered bundles</h2>"
    "<table><thead><tr><th>bundle-id</th><th>client-id</th><th>name</th><th>max-seats</th><th>compatible-components</th></tr></thead><tbody>"
    (bundles-rows store ["B-1" "B-2"])
    "</tbody></table>"
    "</div>"

    "<div class=\"card\">"
    "<h2>Governor action gate (ictsales.governor/check)</h2>"
    "<table><thead><tr><th>kind</th><th>rule</th><th>meaning</th></tr></thead><tbody>"
    (gate-table-rows)
    "</tbody></table>"
    "</div>"

    "<div class=\"card\">"
    "<h2>Audit trail (this scenario's real graph runs)</h2>"
    "<table><thead><tr><th>thread-id</th><th>client</th><th>request</th><th>outcome</th></tr></thead><tbody>"
    (audit-rows runs)
    "</tbody></table>"
    "</div>"

    "</body></html>"]))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html (render result)]
    (clojure.java.io/make-parents out)
    (spit out html)
    (println "wrote" out)))
