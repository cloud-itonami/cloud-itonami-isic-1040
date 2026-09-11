(ns oilsfats.registry
  "Pure, independently-maintained validation functions for oils/fats
  manufacturing quality and food-safety checks. These are called by the
  Governor to INDEPENDENTLY verify physical/quality constraints -- the
  Advisor's confidence is never sufficient to override these checks.

  This module deliberately keeps its own jurisdiction-limits table (string-
  keyed, mirroring `oilsfats.facts`'s keyword-keyed table) rather than
  delegating to `oilsfats.facts` for numeric limits: two independently
  maintained sources agreeing is a stronger safety property than one shared
  source, matching the actor pattern's Governor-independence ethos. Product-
  level storage-temperature ranges are still sourced from `oilsfats.facts`
  since they are physical facts about the product, not jurisdictional law."
  (:require [oilsfats.facts :as facts]))

(def jurisdiction-limits
  "String-keyed (matches how batch records carry :jurisdiction, e.g. \"US\")
  mirror of `oilsfats.facts/jurisdictions`'s numeric limits."
  {"US" {:ffa-limit-percent 0.5 :peroxide-limit-meq-kg 10.0
         :sanitation-min-score 85 :max-holding-hours 2160}
   "EU" {:ffa-limit-percent 0.3 :peroxide-limit-meq-kg 5.0
         :sanitation-min-score 90 :max-holding-hours 1440}
   "JP" {:ffa-limit-percent 0.4 :peroxide-limit-meq-kg 8.0
         :sanitation-min-score 88 :max-holding-hours 1800}})

(defn ffa-exceeds-limit?
  "FFA (free fatty acid, rancidity indicator) check. `product-id` is accepted
  for future per-product overrides but the limit currently comes from
  jurisdiction alone. Missing FFA data or unknown jurisdiction fails closed
  (treated as exceeding)."
  [ffa-percent _product-id jurisdiction]
  (let [limit (get-in jurisdiction-limits [jurisdiction :ffa-limit-percent])]
    (or (nil? ffa-percent) (nil? limit) (> ffa-percent limit))))

(defn peroxide-value-exceeds-limit?
  "Peroxide value (PV, oxidation indicator) check."
  [pv _product-id jurisdiction]
  (let [limit (get-in jurisdiction-limits [jurisdiction :peroxide-limit-meq-kg])]
    (or (nil? pv) (nil? limit) (> pv limit))))

(defn batch-temp-out-of-range?
  "Storage/holding temperature outside [min-c, max-c] (inclusive bounds)."
  [temp-c min-c max-c]
  (or (< temp-c min-c) (> temp-c max-c)))

(defn holding-time-exceeded?
  "Cumulative holding hours exceeding the jurisdiction's maximum window."
  [hours jurisdiction]
  (let [limit (get-in jurisdiction-limits [jurisdiction :max-holding-hours])]
    (or (nil? hours) (nil? limit) (> hours limit))))

(defn sanitation-score-insufficient?
  "Plant sanitation score below the jurisdiction minimum."
  [score jurisdiction]
  (let [min-score (get-in jurisdiction-limits [jurisdiction :sanitation-min-score])]
    (or (nil? score) (nil? min-score) (< score min-score))))

(defn metal-detector-pass?
  "Metal-detector screening result. Only `:pass`/`true` are a pass."
  [v]
  (boolean (or (= v :pass) (true? v))))

(defn microbial-test-passed?
  "Microbial screening result. `:pass`/`:compliant`/`true` are a pass."
  [v]
  (boolean (or (= v :pass) (= v :compliant) (true? v))))

(defn batch-quality-acceptable?
  "Holistic quality gate combining every independent check: FFA, peroxide
  value, product-specific storage temperature, holding time, sanitation,
  metal-detector, and microbial screening. Does NOT check evidence-checklist
  completeness or contamination flags -- those are governor-level, op-scoped
  concerns, not raw quality facts."
  [{:keys [ffa-percent peroxide-value-meq-kg batch-temp-c holding-time-hours
           sanitation-score product-id jurisdiction metal-detector microbial-test]}]
  (let [product (facts/product-type-by-id product-id)]
    (boolean
     (and product
          (not (ffa-exceeds-limit? ffa-percent product-id jurisdiction))
          (not (peroxide-value-exceeds-limit? peroxide-value-meq-kg product-id jurisdiction))
          (not (batch-temp-out-of-range? batch-temp-c
                                          (:storage-temp-min-c product)
                                          (:storage-temp-max-c product)))
          (not (holding-time-exceeded? holding-time-hours jurisdiction))
          (not (sanitation-score-insufficient? sanitation-score jurisdiction))
          (metal-detector-pass? metal-detector)
          (microbial-test-passed? microbial-test)))))
