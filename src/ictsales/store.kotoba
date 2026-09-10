(ns ictsales.store
  "SSoT for the ISCO-08 2434 community ICT sales professionals actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    bundle — a registered licensing bundle {:bundle-id :client-id
             :name :max-seats number
             :compatible-components #{component-str}}.
             `:max-seats` is the registered ceiling a proposed quote's
             seat count must not exceed; `:compatible-components` is
             the registered set a proposed quote's included components
             must all be members of (an incompatible bundle is a
             matrix lookup, not a sales pitch).
    record — a committed operating record (approved quote) — written
             ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (bundle [s bundle-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-bundle! [s b])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (bundle [_ bundle-id] (get-in @a [:bundles bundle-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-bundle! [s b]
    (swap! a assoc-in [:bundles (:bundle-id b)] b) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :bundles {} :records [] :ledger []}
                                   seed)))))
