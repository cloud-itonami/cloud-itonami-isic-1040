(ns oilsfats.facts-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oilsfats.facts :as facts]))

(deftest test-product-type-by-id
  "Retrieve product type specifications."
  (let [soy (facts/product-type-by-id :soybean-oil)]
    (is (= :soybean-oil (:id soy)))
    (is (= "Soybean Oil" (:name soy)))
    (is (= 15 (:storage-temp-min-c soy)))
    (is (= 25 (:storage-temp-max-c soy)))))

(deftest test-jurisdiction-by-id
  "Retrieve jurisdiction regulatory requirements."
  (let [us (facts/jurisdiction-by-id :US)]
    (is (= :US (:id us)))
    (is (= "United States" (:name us)))
    (is (= 0.5 (:ffa-limit-percent us)))
    (is (= 10.0 (:peroxide-limit-meq-kg us)))))

(deftest test-eu-stricter-than-us
  "EU has stricter FFA limits than US."
  (let [us (facts/jurisdiction-by-id :US)
        eu (facts/jurisdiction-by-id :EU)]
    (is (< (:ffa-limit-percent eu) (:ffa-limit-percent us)))))

(deftest test-required-evidence-satisfied
  "Check if batch evidence meets jurisdiction requirements."
  (is (facts/required-evidence-satisfied?
       :US
       #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit})
      "All evidence present should pass")

  (is (not (facts/required-evidence-satisfied?
            :US
            #{:ffa-assay :peroxide-test}))
      "Missing evidence should fail")

  (is (facts/required-evidence-satisfied?
       :EU
       #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit :traceability-log})
      "EU requiring traceability-log"))

(deftest test-ffa-exceeds-limit
  "FFA (rancidity) checks."
  (is (not (facts/ffa-exceeds-limit? 0.5 :US))
      "FFA 0.5% at US limit should pass")
  (is (facts/ffa-exceeds-limit? 0.6 :US)
      "FFA 0.6% over US limit should fail")
  (is (facts/ffa-exceeds-limit? 0.4 :EU)
      "FFA 0.4% over EU limit (0.3%) should fail"))

(deftest test-peroxide-value-exceeds-limit
  "Peroxide value (oxidation) checks."
  (is (not (facts/peroxide-value-exceeds-limit? 10.0 :US))
      "PV 10.0 at limit should pass")
  (is (facts/peroxide-value-exceeds-limit? 11.0 :US)
      "PV 11.0 over limit should fail")
  (is (facts/peroxide-value-exceeds-limit? 6.0 :EU)
      "PV 6.0 over EU limit (5.0) should fail"))

(deftest test-holding-time-exceeded
  "Holding time checks (input in hours, jurisdiction limits in days)."
  (is (not (facts/holding-time-exceeded? 2160 :US))
      "2160 hours (90 days) at US limit should pass")
  (is (facts/holding-time-exceeded? 2161 :US)
      "2161 hours over US limit should fail")
  (is (not (facts/holding-time-exceeded? 1440 :EU))
      "1440 hours (60 days) at EU limit should pass")
  (is (facts/holding-time-exceeded? 1441 :EU)
      "1441 hours over EU limit should fail"))

(deftest test-sanitation-score-insufficient
  "Sanitation score checks (0-100)."
  (is (not (facts/sanitation-score-insufficient? 85 :US))
      "Score 85 at US minimum should pass")
  (is (facts/sanitation-score-insufficient? 84 :US)
      "Score 84 below US minimum should fail")
  (is (not (facts/sanitation-score-insufficient? 90 :EU))
      "Score 90 at EU minimum should pass")
  (is (facts/sanitation-score-insufficient? 89 :EU)
      "Score 89 below EU minimum should fail"))

(deftest test-batch-temp-out-of-range
  "Storage temperature range checks."
  (is (not (facts/batch-temp-out-of-range? 20 15 25))
      "20°C within [15,25] should pass")
  (is (facts/batch-temp-out-of-range? 14 15 25)
      "14°C below range should fail")
  (is (facts/batch-temp-out-of-range? 26 15 25)
      "26°C above range should fail"))

(deftest test-product-specs-differ
  "Different products have different storage requirements."
  (let [soy (facts/product-type-by-id :soybean-oil)
        palm (facts/product-type-by-id :palm-oil)
        animal (facts/product-type-by-id :animal-fat)]
    ;; Palm oil stores warmer
    (is (> (:storage-temp-max-c palm) (:storage-temp-max-c soy)))
    ;; Animal fat stores colder
    (is (< (:storage-temp-min-c animal) (:storage-temp-min-c soy)))))
