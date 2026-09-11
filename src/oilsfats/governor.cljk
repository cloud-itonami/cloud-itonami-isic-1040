(ns oilsfats.governor
  "OilsFatsOps Governor -- the independent plant-operations-coordination
  compliance layer that earns the Advisor the right to propose. The Advisor
  (LLM/mock) has no notion of:
    - Whether a batch's FFA (free fatty acid, rancidity indicator) is within
      jurisdiction limits
    - Whether the peroxide value (oxidation indicator) is within limits
    - Whether storage/holding temperature is within the product's range
    - Whether holding time has exceeded the jurisdiction's window
    - Whether plant sanitation meets jurisdiction requirements
    - Whether metal-detector / microbial screening passed
    - Whether an open contamination flag has been resolved
    - Whether the batch's evidence checklist is complete per jurisdiction
    - Whether the batch/plant record has even been verified/registered

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD. Unlike direct extraction/refining-line control (NEVER done by
  this actor -- process equipment operation remains exclusive to licensed
  plant operators and robotics safety systems), the Governor operates on
  batch metadata: quality assays, storage records, sanitation records, and
  food-safety flags. This is plant-operations coordination, not process
  control.

  HARD invariants (always -> HOLD, no override, no confidence can buy past
  these):
    1. Op is outside the closed allowlist (`allowed-ops`) -- this INCLUDES
       any proposal that would touch extraction/refining-line control or
       food-safety certification authority, neither of which is ever a
       member of the allowlist. This is a permanent block, not a phase gate.
    2. The proposal's `:effect` is present and is not `:propose` -- this
       actor NEVER commits/executes, only proposes.
    3. No jurisdiction citation (`:cites` empty, or `:value` explicitly
       carries a nil `:jurisdiction`) -- never invent a jurisdiction's
       food-safety requirements.
    4. The batch/plant record referenced by `:subject` has not been
       verified/registered in the store -- no action is considered against
       an unverified subject.
    5. Evidence checklist incomplete for the jurisdiction (only evaluated
       once an evidence checklist has actually been submitted).
    6. FFA exceeds the jurisdiction limit (rancidity).
    7. Peroxide value exceeds the jurisdiction limit (oxidation).
    8. Storage/holding temperature outside the product's required range.
    9. Holding time exceeds the jurisdiction's maximum window.
   10. Sanitation score below the jurisdiction minimum.
   11. Metal-detector screening failed.
   12. Microbial screening failed.
   13. Contamination flag raised and not resolved.
   14. Batch already processed (`:log-production-batch` is not idempotent).
   15. Shipment already finalized (`:coordinate-shipment` is not idempotent).

  ESCALATE (always requires human sign-off, never independently approved):
    - `:flag-food-safety-concern` ALWAYS escalates -- the Advisor's
      confidence is never sufficient for a food-safety decision.
    - Any high-stakes op (`:log-production-batch`, `:schedule-maintenance`,
      `:coordinate-shipment` -- real plant-operations actuation) escalates
      even when the Governor finds nothing wrong.
    - Low Advisor confidence (< `confidence-floor`)."
  (:require [oilsfats.facts :as facts]
            [oilsfats.registry :as registry]
            [oilsfats.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "Closed allowlist. This actor is a plant-OPERATIONS coordinator, never a
  process-control or certification authority. Any op outside this set --
  including a hypothetical proposal to actuate extraction/refining-line
  equipment directly, or to self-certify food-safety compliance -- is a
  hard, permanent block; it can never be phased into autonomy because it is
  never proposed in the first place."
  #{:log-production-batch :schedule-maintenance
    :flag-food-safety-concern :coordinate-shipment})

(def high-stakes
  "Real-world plant-operations actuation events. Always requires human
  sign-off, regardless of Governor cleanliness or Advisor confidence."
  #{:log-production-batch :schedule-maintenance :coordinate-shipment})

(def always-escalate-ops
  "Ops that ALWAYS escalate to a human, independent of the `:stake` on the
  proposal -- food-safety concerns are never resolved by confidence alone."
  #{:flag-food-safety-concern})

(def batch-scoped-ops
  "Ops whose `:subject` refers to a batch/plant record that MUST already be
  verified/registered in the store before any proposal touching it is even
  considered."
  #{:log-production-batch :coordinate-shipment :flag-food-safety-concern})

(def spec-basis-required-ops
  "Ops that must cite an official jurisdiction/spec basis -- never invent
  food-safety requirements."
  #{:log-production-batch :coordinate-shipment :flag-food-safety-concern})

;; ----------------------------- checks -----------------------------

(defn- disallowed-op-violations
  "HARD invariant 1: closed allowlist. Also the structural home of the
  'no extraction/refining-line control, no food-safety certification'
  block -- those actions are simply never members of `allowed-ops`."
  [{:keys [op]}]
  (when-not (contains? allowed-ops op)
    [{:rule :disallowed-operation
      :detail (str op " はクローズドallowlist外 -- 抽出/精製ラインの直接制御や"
                   "食品安全認証への権限行使はこのactorには恒久的に許可されない")}]))

(defn- effect-not-propose-violations
  "HARD invariant 2: a proposal's :effect must be :propose, or absent."
  [proposal]
  (when (and (contains? proposal :effect) (not= :propose (:effect proposal)))
    [{:rule :effect-not-propose
      :detail (str "proposalの:effectは:proposeのみ許容 -- 実際は " (:effect proposal))}]))

(defn- no-spec-basis-violations
  "HARD invariant 3: no jurisdiction citation."
  [{:keys [op]} proposal]
  (when (contains? spec-basis-required-ops op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式specificationの引用が無い提案は法域要件として扱えない"}]))))

(defn- batch-not-registered-violations
  "HARD invariant 4: subject batch/plant record must be verified/registered
  before ANY action touching it."
  [{:keys [op subject]} st]
  (when (contains? batch-scoped-ops op)
    (when-not (store/production-batch st subject)
      [{:rule :batch-not-registered
        :detail (str subject " はプラント記録として未登録/未検証 -- "
                     "登録前のいかなる提案も進められない")}])))

(defn- evidence-incomplete-violations
  "HARD invariant 5: once an evidence checklist has been submitted for
  `:log-production-batch`, it must be complete per jurisdiction."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (contains? b :evidence-checklist) (:jurisdiction b))
        (when-not (facts/required-evidence-satisfied?
                   (keyword (:jurisdiction b)) (:evidence-checklist b))
          [{:rule :evidence-incomplete
            :detail (str subject " の法域必要書類(evidence-checklist)が充足していない")}])))))

(defn- ffa-violations
  "HARD invariant 6: FFA (rancidity) exceeds jurisdiction limit. Verified
  INDEPENDENTLY via `oilsfats.registry`, never trusting the Advisor."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:jurisdiction b)
                 (registry/ffa-exceeds-limit? (:ffa-percent b) (:product-id b) (:jurisdiction b)))
        [{:rule :ffa-exceeds-limit
          :detail (str subject " のFFA(遊離脂肪酸/酸敗指標, " (:ffa-percent b)
                       "%)が法域限度を超過 -- バッチ登録提案は進められない")}]))))

(defn- peroxide-violations
  "HARD invariant 7: peroxide value (oxidation) exceeds jurisdiction
  limit."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:jurisdiction b)
                 (registry/peroxide-value-exceeds-limit?
                  (:peroxide-value-meq-kg b) (:product-id b) (:jurisdiction b)))
        [{:rule :peroxide-value-exceeds-limit
          :detail (str subject " の過酸化物価(酸化指標, " (:peroxide-value-meq-kg b)
                       " mEq/kg)が法域限度を超過 -- バッチ登録提案は進められない")}]))))

(defn- temp-out-of-range-violations
  "HARD invariant 8: storage/holding temperature outside the product's
  required range."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/product-type-by-id (:product-id b)))]
      (when (and b p (:batch-temp-c b)
                 (registry/batch-temp-out-of-range?
                  (:batch-temp-c b) (:storage-temp-min-c p) (:storage-temp-max-c p)))
        [{:rule :batch-temp-out-of-range
          :detail (str subject " の保管温度(" (:batch-temp-c b) "℃)が製品保管範囲["
                       (:storage-temp-min-c p) ", " (:storage-temp-max-c p)
                       "]℃の外 -- バッチ登録提案は進められない")}]))))

(defn- holding-time-violations
  "HARD invariant 9: holding time exceeds jurisdiction maximum."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:jurisdiction b) (:holding-time-hours b)
                 (registry/holding-time-exceeded? (:holding-time-hours b) (:jurisdiction b)))
        [{:rule :holding-time-exceeded
          :detail (str subject " の保持時間(" (:holding-time-hours b)
                       "時間)が法域限度を超過 -- バッチ登録提案は進められない")}]))))

(defn- sanitation-violations
  "HARD invariant 10: plant sanitation score below jurisdiction minimum."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:jurisdiction b) (:sanitation-score b)
                 (registry/sanitation-score-insufficient?
                  (:sanitation-score b) (:jurisdiction b)))
        [{:rule :sanitation-score-insufficient
          :detail (str subject " のプラント衛生スコア(" (:sanitation-score b)
                       ")が最低要件を下回る -- バッチ登録提案は進められない")}]))))

(defn- metal-detector-violations
  "HARD invariant 11: metal-detector screening failed."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (contains? b :metal-detector)
                 (not (registry/metal-detector-pass? (:metal-detector b))))
        [{:rule :metal-detector-failure
          :detail (str subject " が金属検出スクリーニングに不合格 -- バッチ登録提案は進められない")}]))))

(defn- microbial-test-violations
  "HARD invariant 12: microbial screening failed."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (contains? b :microbial-test)
                 (not (registry/microbial-test-passed? (:microbial-test b))))
        [{:rule :microbial-test-failed
          :detail (str subject " が微生物検査に不合格 -- バッチ登録提案は進められない")}]))))

(defn- contamination-flag-unresolved-violations
  "HARD invariant 13: an unresolved contamination flag is a permanent hold
  until a human resolves it -- never overridden by confidence."
  [{:keys [op subject]} st]
  (when (contains? #{:log-production-batch :coordinate-shipment} op)
    (let [b (store/production-batch st subject)]
      (when (and b (true? (:contamination-flag-raised? b))
                 (not (true? (:contamination-flag-resolved? b))))
        [{:rule :contamination-flag-unresolved
          :detail (str subject " は未解決の汚染フラグがある -- 提案は進められない")}]))))

(defn- already-processed-violations
  "HARD invariant 14: refuse to log the SAME batch twice."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (when (store/batch-already-processed? st subject)
      [{:rule :already-processed :detail (str subject " は既に登録済み")}])))

(defn- already-shipment-finalized-violations
  "HARD invariant 15: refuse to finalize the SAME batch's shipment twice."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when (store/batch-shipment-finalized? st subject)
      [{:rule :already-shipment-finalized :detail (str subject " は既に出荷確定済み")}])))

(defn check
  "Censors an OilsFatsOps Advisor proposal against every Governor rule.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [op (:op request)
        hard (into []
                   (concat (disallowed-op-violations request)
                           (effect-not-propose-violations proposal)
                           (no-spec-basis-violations request proposal)
                           (batch-not-registered-violations request st)
                           (evidence-incomplete-violations request st)
                           (ffa-violations request st)
                           (peroxide-violations request st)
                           (temp-out-of-range-violations request st)
                           (holding-time-violations request st)
                           (sanitation-violations request st)
                           (metal-detector-violations request st)
                           (microbial-test-violations request st)
                           (contamination-flag-unresolved-violations request st)
                           (already-processed-violations request st)
                           (already-shipment-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (contains? high-stakes (:stake proposal))
                              (contains? always-escalate-ops op)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))
