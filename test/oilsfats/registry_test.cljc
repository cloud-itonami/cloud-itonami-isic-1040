(ns oilsfats.registry-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oilsfats.registry :as registry]))

(deftest test-ffa-exceeds-limit-soybean-us
  "Soybean oil FFA limits for US jurisdiction."
  (is (not (registry/ffa-exceeds-limit? 0.5 :soybean-oil "US"))
      "FFA 0.5% should pass (at limit)")
  (is (registry/ffa-exceeds-limit? 0.6 :soybean-oil "US")
      "FFA 0.6% should fail (over product limit)")
  (is (registry/ffa-exceeds-limit? 1.0 :soybean-oil "US")
      "FFA 1.0% should fail (way over)")
  (is (registry/ffa-exceeds-limit? nil :soybean-oil "US")
      "Missing FFA data should fail"))

(deftest test-ffa-exceeds-limit-eu-stricter
  "EU jurisdiction has stricter FFA limits than US."
  (is (registry/ffa-exceeds-limit? 0.4 :soybean-oil "EU")
      "FFA 0.4% should fail EU (limit 0.3%)"))

(deftest test-peroxide-value-exceeds-limit
  "Peroxide value limits per product and jurisdiction."
  (is (not (registry/peroxide-value-exceeds-limit? 10.0 :soybean-oil "US"))
      "PV 10.0 mEq/kg should pass (at limit)")
  (is (registry/peroxide-value-exceeds-limit? 11.0 :soybean-oil "US")
      "PV 11.0 should fail")
  (is (registry/peroxide-value-exceeds-limit? 6.0 :soybean-oil "EU")
      "PV 6.0 should fail EU (limit 5.0)"))

(deftest test-batch-temp-out-of-range
  "Temperature range checks."
  (is (not (registry/batch-temp-out-of-range? 20 15 25))
      "20°C within range [15,25]")
  (is (registry/batch-temp-out-of-range? 10 15 25)
      "10°C below range")
  (is (registry/batch-temp-out-of-range? 30 15 25)
      "30°C above range"))

(deftest test-holding-time-exceeded-us
  "US holding time limits (90 days = 2160 hours)."
  (is (not (registry/holding-time-exceeded? 2160 "US"))
      "2160 hours (90 days) should pass")
  (is (registry/holding-time-exceeded? 2161 "US")
      "2161 hours should fail")
  (is (registry/holding-time-exceeded? 5000 "US")
      "5000 hours should fail"))

(deftest test-holding-time-exceeded-eu-stricter
  "EU has stricter holding time (60 days = 1440 hours)."
  (is (not (registry/holding-time-exceeded? 1440 "EU"))
      "1440 hours (60 days) should pass")
  (is (registry/holding-time-exceeded? 1441 "EU")
      "1441 hours should fail"))

(deftest test-sanitation-score-insufficient
  "Sanitation scoring varies by jurisdiction."
  (is (not (registry/sanitation-score-insufficient? 85 "US"))
      "Score 85 should pass US (limit 85)")
  (is (registry/sanitation-score-insufficient? 84 "US")
      "Score 84 should fail US")
  (is (registry/sanitation-score-insufficient? 89 "EU")
      "Score 89 should fail EU (limit 90)"))

(deftest test-metal-detector-pass
  "Metal detector pass/fail check."
  (is (registry/metal-detector-pass? :pass)
      ":pass should return true")
  (is (registry/metal-detector-pass? true)
      "true should return true")
  (is (not (registry/metal-detector-pass? :fail))
      ":fail should return false")
  (is (not (registry/metal-detector-pass? false))
      "false should return false")
  (is (not (registry/metal-detector-pass? nil))
      "nil should return false"))

(deftest test-microbial-test-passed
  "Microbial test pass/fail check."
  (is (registry/microbial-test-passed? :pass)
      ":pass should return true")
  (is (registry/microbial-test-passed? :compliant)
      ":compliant should return true")
  (is (registry/microbial-test-passed? true)
      "true should return true")
  (is (not (registry/microbial-test-passed? :fail))
      ":fail should return false")
  (is (not (registry/microbial-test-passed? nil))
      "nil should return false"))

(deftest test-batch-quality-acceptable
  "Holistic batch quality check."
  (is (registry/batch-quality-acceptable?
       {:ffa-percent 0.3 :peroxide-value-meq-kg 5.0
        :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
        :product-id :soybean-oil :jurisdiction "US"
        :metal-detector :pass :microbial-test :pass})
      "All-green batch should pass")

  (is (not (registry/batch-quality-acceptable?
            {:ffa-percent 1.5 :peroxide-value-meq-kg 5.0
             :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
             :product-id :soybean-oil :jurisdiction "US"
             :metal-detector :pass :microbial-test :pass}))
      "High FFA should fail")

  (is (not (registry/batch-quality-acceptable?
            {:ffa-percent 0.3 :peroxide-value-meq-kg 5.0
             :batch-temp-c 35 :holding-time-hours 24 :sanitation-score 90
             :product-id :soybean-oil :jurisdiction "US"
             :metal-detector :pass :microbial-test :pass}))
      "Out-of-range temp should fail"))
