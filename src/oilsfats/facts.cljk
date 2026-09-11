(ns oilsfats.facts
  "Reference facts for oils/fats manufacturing: jurisdiction FFA (free fatty
  acid, rancidity indicator) and peroxide-value (oxidation indicator) limits,
  sanitation minimums, holding-time windows, evidence-checklist requirements,
  and per-product storage-temperature ranges. Pure lookup functions only --
  the Governor calls these to independently validate proposals against
  jurisdiction/product requirements. The Advisor never invents these values."
  (:require [clojure.set :as set]))

(def jurisdictions
  "Oils/fats manufacturing jurisdictions and their food-safety limits. Keyed
  by keyword id. See `oilsfats.registry` for the independently-maintained,
  string-keyed mirror of these limits used by the Governor's redundant
  verification path."
  {:US
   {:id :US
    :name "United States"
    :ffa-limit-percent 0.5
    :peroxide-limit-meq-kg 10.0
    :sanitation-min-score 85
    :max-holding-hours 2160 ;; 90 days
    :required-evidence #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit}}

   :EU
   {:id :EU
    :name "European Union"
    :ffa-limit-percent 0.3
    :peroxide-limit-meq-kg 5.0
    :sanitation-min-score 90
    :max-holding-hours 1440 ;; 60 days
    :required-evidence #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit
                          :traceability-log}}

   :JP
   {:id :JP
    :name "日本"
    :ffa-limit-percent 0.4
    :peroxide-limit-meq-kg 8.0
    :sanitation-min-score 88
    :max-holding-hours 1800 ;; 75 days
    :required-evidence #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit
                          :sensory-test}}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that all required-evidence items for `jurisdiction-id` are present
  in `checklist` (a set or seq). Returns false for an unknown jurisdiction --
  never invent evidence requirements."
  [jurisdiction-id checklist]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (boolean (and j (set/subset? (:required-evidence j) (set checklist))))))

(defn ffa-exceeds-limit?
  "FFA (free fatty acid) is the rancidity indicator. Exceeding the
  jurisdiction limit is a hard food-safety/quality concern. Missing data or
  an unknown jurisdiction is treated as exceeding (fail closed)."
  [ffa-percent jurisdiction-id]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (or (nil? ffa-percent) (nil? j) (> ffa-percent (:ffa-limit-percent j)))))

(defn peroxide-value-exceeds-limit?
  "Peroxide value (PV) is the oxidation indicator. Fail closed on missing
  data or unknown jurisdiction."
  [pv jurisdiction-id]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (or (nil? pv) (nil? j) (> pv (:peroxide-limit-meq-kg j)))))

(defn holding-time-exceeded?
  "Cumulative hours a batch has been held since intake/processing, compared
  against the jurisdiction's maximum holding window."
  [hours jurisdiction-id]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (or (nil? hours) (nil? j) (> hours (:max-holding-hours j)))))

(defn sanitation-score-insufficient?
  "Plant sanitation score (0-100) below the jurisdiction minimum."
  [score jurisdiction-id]
  (let [j (jurisdiction-by-id jurisdiction-id)]
    (or (nil? score) (nil? j) (< score (:sanitation-min-score j)))))

(defn batch-temp-out-of-range?
  "Storage/holding temperature outside the product's required range. Pure,
  jurisdiction-independent -- storage physics doesn't vary by law."
  [temp-c min-c max-c]
  (or (< temp-c min-c) (> temp-c max-c)))

(def product-types
  "Oils/fats product categories and their storage-temperature requirements.
  Vegetable oils generally store at or below room temperature; some (e.g.
  palm oil) must be kept warmer to remain liquid/pumpable; animal fats store
  colder to slow rancidity."
  {:soybean-oil
   {:id :soybean-oil
    :name "Soybean Oil"
    :storage-temp-min-c 15
    :storage-temp-max-c 25}

   :palm-oil
   {:id :palm-oil
    :name "Palm Oil"
    :storage-temp-min-c 25
    :storage-temp-max-c 35}

   :olive-oil
   {:id :olive-oil
    :name "Olive Oil"
    :storage-temp-min-c 14
    :storage-temp-max-c 24}

   :animal-fat
   {:id :animal-fat
    :name "Animal Fat (Tallow/Lard)"
    :storage-temp-min-c 5
    :storage-temp-max-c 20}})

(defn product-type-by-id [id]
  (get product-types id))
