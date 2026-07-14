(ns oilsfats.phase
  "Rollout phases for OilsFatsOps actor.

  Phases gate which operations are allowed at which stage of the rollout.
  Phase progression is manual (oat-gating via ADR), not automatic.
  See ADR-2607121000 (reverse-toposort rollout plan) for justification.

  Phase 0: :disabled        -- Actor not deployed (blueprint only)
  Phase 1: :intake-only     -- Can receive intake batches, flag concerns
  Phase 2: :intake-propose  -- Can propose actions (log, maintenance) but all escalate
  Phase 3: :production      -- Full production (batch logging, maintenance, shipment)
  Phase 4: :optimization    -- Historical analysis, predictive scheduling (future)")

;; ============= Phase definitions =============

(def phases
  {
   :disabled
   {:id :disabled
    :name "Disabled (blueprint stage)"
    :allowed-ops #{}}

   :intake-only
   {:id :intake-only
    :name "Intake-only (data collection)"
    :allowed-ops #{:intake-batch :flag-food-safety-concern}}

   :intake-propose
   {:id :intake-propose
    :name "Intake + Proposal (all escalate to human)"
    :allowed-ops #{:log-production-batch :schedule-maintenance :coordinate-shipment
                   :flag-food-safety-concern}
    :escalate-all-proposals true}

   :production
   {:id :production
    :name "Production (independent governance)"
    :allowed-ops #{:log-production-batch :schedule-maintenance :coordinate-shipment
                   :flag-food-safety-concern}}

   :optimization
   {:id :optimization
    :name "Optimization (predictive dispatch)"
    :allowed-ops #{:log-production-batch :schedule-maintenance :coordinate-shipment
                   :flag-food-safety-concern :predict-quality-trend :auto-reorder}}})

;; ============= Phase queries =============

(defn phase-by-id [id]
  (get phases id))

(defn phase-allows-operation?
  "Can this operation run in this phase?"
  [phase-id op]
  (when-let [p (phase-by-id phase-id)]
    (contains? (:allowed-ops p) op)))

(defn escalate-all-proposals?
  "In this phase, do all proposals escalate to human (no independent approval)?"
  [phase-id]
  (when-let [p (phase-by-id phase-id)]
    (boolean (:escalate-all-proposals p))))

(defn- index-of
  "Portable (no JVM/JS host-interop) index lookup -- `.indexOf` is a
  java.util.List method that ClojureScript vectors do not implement, so it
  is not usable directly from `.cljc`. Returns -1 when not found, matching
  `java.util.List#indexOf` semantics."
  [coll x]
  (or (first (keep-indexed (fn [i v] (when (= v x) i)) coll)) -1))

(defn phase-gt
  "Is phase-a a later stage than phase-b in rollout order?"
  [phase-a phase-b]
  (let [order [:disabled :intake-only :intake-propose :production :optimization]
        idx-a (index-of order phase-a)
        idx-b (index-of order phase-b)]
    (and (>= idx-a 0) (>= idx-b 0) (> idx-a idx-b))))
