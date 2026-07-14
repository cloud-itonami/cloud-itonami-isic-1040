(ns oilsfats.operation-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oilsfats.advisor :as advisor]
   [oilsfats.governor :as governor]
   [oilsfats.operation :as operation]
   [oilsfats.store :as store]))

(deftest test-run-operation-good-batch-escalates-high-stakes
  "Good batch with high-stakes op should escalate (not independent approve)."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false
               :evidence-checklist #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit}}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        advisor-impl (advisor/->MockAdvisor)
        req (operation/create-batch-request "b1" :soybean-oil "US")
        context {:actor-id "test-actor"}
        result (operation/run-operation st req context advisor-impl governor/check)]

    (is (= :escalate (:effect result))
        "High-stakes op should escalate even if all checks pass")))

(deftest test-run-operation-bad-batch-holds
  "Batch failing governor checks should be held."
  (let [batch {:batch-id "b1" :ffa-percent 1.5 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        advisor-impl (advisor/->MockAdvisor)
        req (operation/create-batch-request "b1" :soybean-oil "US")
        context {:actor-id "test-actor"}
        result (operation/run-operation st req context advisor-impl governor/check)]

    (is (= :hold (:effect result))
        "Bad batch should be held")))

(deftest test-run-operation-appends-audit-fact
  "Operation should append audit fact to store."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        advisor-impl (advisor/->MockAdvisor)
        req (operation/create-batch-request "b1" :soybean-oil "US")
        context {:actor-id "test-actor"}
        result (operation/run-operation st req context advisor-impl governor/check)
        next-st (:next-store result)]

    (is (= 1 (count (:facts next-st)))
        "Should have appended one fact")
    (is (= :escalate (:disposition (first (:facts next-st))))
        "Fact disposition should match effect")))

(deftest test-create-batch-request
  "Helper to create batch logging request."
  (let [req (operation/create-batch-request "b1" :soybean-oil "US" :weight-kg 1000)]
    (is (= :log-production-batch (:op req)))
    (is (= "b1" (:subject req)))
    (is (= :soybean-oil (:product-id req)))
    (is (= "US" (:jurisdiction req)))
    (is (= 1000 (:weight-kg req)))))

(deftest test-create-maintenance-request
  "Helper to create maintenance scheduling request."
  (let [req (operation/create-maintenance-request "equip-A" :filter-replacement)]
    (is (= :schedule-maintenance (:op req)))
    (is (= "equip-A" (:subject req)))
    (is (= :filter-replacement (:maintenance-type req)))))

(deftest test-create-shipment-request
  "Helper to create shipment coordination request."
  (let [req (operation/create-shipment-request "b1" "Customer warehouse")]
    (is (= :coordinate-shipment (:op req)))
    (is (= "b1" (:subject req)))
    (is (= "Customer warehouse" (:destination req)))))

(deftest test-create-concern-request
  "Helper to create food-safety concern flag request."
  (let [req (operation/create-concern-request "b1" :ffa-spike "FFA elevated due to hydrolysis")]
    (is (= :flag-food-safety-concern (:op req)))
    (is (= "b1" (:subject req)))
    (is (= :ffa-spike (:concern-type req)))
    (is (= "FFA elevated due to hydrolysis" (:detail req)))))
