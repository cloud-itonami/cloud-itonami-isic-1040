(ns oilsfats.advisor
  "OilsFatsOps advisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes plant-operations-coordination actions based on
  batch/plant state; it is SEALED behind `oilsfats.governor/check` in
  `oilsfats.operation/run-operation` -- every proposal is independently
  censored before anything can propose/commit.

  The advisor has NO direct authority and NEVER controls extraction/refining
  equipment. Every proposal it returns carries `:effect :propose` (never
  `:commit`/`:execute`) -- the Governor hard-blocks any proposal that claims
  a different effect (see `oilsfats.governor`'s `effect-not-propose` rule).

  Current implementation is a mock advisor for testing/simulation.
  Production should swap in a real LLM backend (langchain/Claude) behind the
  same 2-arity call signature `(advisor store request)` -- see
  `oilsfats.operation/run-operation`."
  (:require [oilsfats.store :as store]))

;; Protocol for swappable advisor implementations. `propose` is the stable
;; extension point; the record ALSO implements IFn below so it can be called
;; directly as `(advisor store request)`, matching `operation/run-operation`.
(defprotocol Advisor
  (propose [advisor store request]
    "Given the store and a request map ({:op :subject ...}), return a
    proposal map with :op :effect :stake :value :cites :summary
    :confidence."))

(defrecord MockAdvisor []
  Advisor
  (propose [_ st request]
    (let [{:keys [op subject jurisdiction destination maintenance-type
                  concern-type detail]} request
          batch (when subject (store/production-batch st subject))
          batch-jurisdiction (or jurisdiction (:jurisdiction batch))]
      (case op
        :log-production-batch
        {:op :log-production-batch
         :effect :propose
         :stake :log-production-batch
         :value {:jurisdiction batch-jurisdiction
                 :batch-id subject
                 :action "Log batch into production records"}
         :cites ["21-CFR-184" "product-quality-assay"]
         :summary "Quality assay (FFA/PV), storage temperature, holding time, and sanitation reviewed; batch appears ready for production logging."
         :confidence (if batch 0.88 0.35)}

        :schedule-maintenance
        {:op :schedule-maintenance
         :effect :propose
         :stake :schedule-maintenance
         :value {:equipment-id subject
                 :maintenance-type (or maintenance-type :routine-inspection)}
         :cites ["Equipment-Manual"]
         :summary "Equipment due for scheduled preventative maintenance."
         :confidence 0.9}

        :coordinate-shipment
        {:op :coordinate-shipment
         :effect :propose
         :stake :coordinate-shipment
         :value {:batch-id subject
                 :destination (or destination "customer warehouse")}
         :cites ["Cold-Chain-Logistics-Spec"]
         :summary "Final product ready for shipment coordination."
         :confidence (if batch 0.82 0.3)}

        :flag-food-safety-concern
        {:op :flag-food-safety-concern
         :effect :propose
         :stake :monitoring
         :value {:batch-id subject
                 :concern-type (or concern-type :quality-deviation)
                 :description (or detail "Quality assay deviation observed")}
         :cites ["Food-Safety-Escalation-Policy"]
         :summary "Potential food-safety concern detected; escalating for human review."
         :confidence 0.65}

        ;; fallback: closed allowlist -- unknown ops never get a confident
        ;; proposal. The Governor's `disallowed-operation` rule also hard-
        ;; blocks this independently.
        {:op op
         :effect :propose
         :stake :unknown
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0})))

  #?@(:clj [clojure.lang.IFn
            (invoke [this st request] (propose this st request))]
      :cljs [IFn
             (-invoke [this st request] (propose this st request))]))

(defn mock-advisor
  "Construct a MockAdvisor. Callable directly as `(advisor store request)`."
  []
  (->MockAdvisor))

(defn trace
  "Audit-trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether the Governor later approves it."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
