(ns oilsfats.operation
  "Operation state machine: Advisor → Governor → Effect (or HOLD).

  The stateless operation function is the canonical business logic,
  independent of langgraph / StateGraph. Given store, request, advisor,
  and governor, it returns {:effect :propose/:hold, :fact ..., :next-store ...}
  suitable for both synchronous testing and async langgraph integration.")

;; ============= Operation execution (core business logic, no langgraph) =============

(defn run-operation
  "Canonical operation: request -> advisor proposal -> governor check -> effect.
  Returns {:effect :propose/:hold/:escalate
           :fact audit-fact
           :next-store updated-store
           :violation-detail (optional, for human review)}."
  [store request context advisor governor]
  (let [;; Step 1: Advisor proposes action
        proposal (advisor store request)

        ;; Step 2: Governor checks proposal
        verdict (governor request context proposal store)

        ;; Step 3: Render effect based on verdict
        effect (cond
                 (:ok? verdict) :propose
                 (:hard? verdict) :hold
                 (:escalate? verdict) :escalate
                 :else :hold)

        ;; Step 4: Audit fact
        fact (case effect
              :propose {:t :operation-proposed
                        :op (:op request)
                        :actor (:actor-id context)
                        :subject (:subject request)
                        :disposition :propose
                        :proposal proposal
                        :confidence (:confidence verdict)}
              :hold (if (:hard? verdict)
                      {:t :governor-hold
                       :op (:op request)
                       :actor (:actor-id context)
                       :subject (:subject request)
                       :disposition :hold
                       :reason :hard-violation
                       :basis (mapv :rule (:violations verdict))
                       :violations (:violations verdict)}
                      {:t :governor-escalate
                       :op (:op request)
                       :actor (:actor-id context)
                       :subject (:subject request)
                       :disposition :escalate
                       :reason (if (:high-stakes? verdict) :high-stakes :low-confidence)
                       :confidence (:confidence verdict)})
              :escalate {:t :governor-escalate
                         :op (:op request)
                         :actor (:actor-id context)
                         :subject (:subject request)
                         :disposition :escalate
                         :reason (if (:high-stakes? verdict) :high-stakes :low-confidence)
                         :confidence (:confidence verdict)})

        ;; Step 5: Update store (add audit fact)
        next-store (update store :facts conj fact)]

    {:effect effect
     :fact fact
     :next-store next-store
     :verdict verdict}))

;; ============= Batch operation helpers =============

(defn create-batch-request
  "Construct a `:log-production-batch` request for an incoming intake lot."
  [batch-id product-id jurisdiction & {:as optional-fields}]
  (merge
   {:op :log-production-batch
    :subject batch-id
    :product-id product-id
    :jurisdiction jurisdiction}
   optional-fields))

(defn create-maintenance-request
  "Construct a `:schedule-maintenance` request."
  [equipment-id maintenance-type & {:as optional-fields}]
  (merge
   {:op :schedule-maintenance
    :subject equipment-id
    :maintenance-type maintenance-type}
   optional-fields))

(defn create-shipment-request
  "Construct a `:coordinate-shipment` request."
  [batch-id destination & {:as optional-fields}]
  (merge
   {:op :coordinate-shipment
    :subject batch-id
    :destination destination}
   optional-fields))

(defn create-concern-request
  "Construct a `:flag-food-safety-concern` request."
  [batch-id concern-type detail & {:as optional-fields}]
  (merge
   {:op :flag-food-safety-concern
    :subject batch-id
    :concern-type concern-type
    :detail detail}
   optional-fields))
