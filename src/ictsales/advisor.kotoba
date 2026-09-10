(ns ictsales.advisor
  "ICTSalesAdvisor — proposes a quote operation (approve a quote,
  approve an enterprise quote) for a registered organization.
  Swappable mock/llm; the advisor ONLY proposes — `ictsales.governor`
  checks the seat-count ceiling and component compatibility
  independently. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-quote|:approve-enterprise-quote
               :effect :propose :bundle-id str :seats number
               :components #{str} :stake kw :confidence n
               :rationale str}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake bundle-id seats components] :as request}]
  {:op op
   :effect :propose
   :bundle-id bundle-id
   :seats seats
   :components components
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an ICT sales advisor. Given a request, propose an :op, the
   :bundle-id, :seats and :components, an honest :confidence and a
   :stake. Never call an over-seat quote or an incompatible-component
   bundle conforming — the governor checks both against the
   registered bundle record.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
