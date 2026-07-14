(ns oilsfats.governor-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oilsfats.governor :as governor]
   [oilsfats.store :as store]))

(deftest test-no-spec-basis-violation
  "Proposal with no jurisdiction citation must be rejected."
  (let [proposal {:op :log-production-batch :cites [] :value {}}
        request {:op :log-production-batch}
        st (store/init-store)]
    (is (seq (governor/check request {} proposal st))
        "Should have violations")))

(deftest test-confidence-floor
  "Proposals below confidence floor must escalate."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.5 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (:escalate? verdict) "Low confidence should escalate")))

(deftest test-high-stakes-ops-escalate
  "High-stakes operations must always escalate."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :stake :log-production-batch
                 :confidence 0.95 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (:escalate? verdict)
        "High-stakes op should escalate even with high confidence")))

(deftest test-ffa-exceeds-limit
  "Batches with FFA above jurisdiction limit must be rejected."
  (let [batch {:batch-id "b1" :ffa-percent 1.2 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.9 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:ok? verdict)) "FFA violation should fail")
    (is (:hard? verdict) "FFA violation is hard")))

(deftest test-peroxide-value-exceeds-limit
  "Batches with PV above jurisdiction limit must be rejected."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 15.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.9 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:ok? verdict)) "PV violation should fail")))

(deftest test-temp-out-of-range
  "Batches outside storage temp range must be rejected."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 30 ;; Soybean oil spec: 15-25°C
               :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.9 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:ok? verdict)) "Temp violation should fail")))

(deftest test-holding-time-exceeded
  "Batches exceeding max holding time must be rejected."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20
               :holding-time-hours 2500 ;; >100 days; US limit 90 days = 2160 hours
               :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.9 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:ok? verdict)) "Holding time violation should fail")))

(deftest test-contamination-flag-unresolved
  "Unresolved contamination flags must block batch."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? true
               :contamination-flag-resolved? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.9 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:ok? verdict)) "Unresolved contamination should fail")
    (is (:hard? verdict) "Unresolved contamination is hard")))

(deftest test-metal-detector-failure
  "Metal detector failures must block batch."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :fail ;; <- FAILED
               :microbial-test :pass
               :contamination-flag-raised? false}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.9 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:ok? verdict)) "Metal detector failure should fail")))

(deftest test-already-processed
  "Already-processed batches must be rejected."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false
               :processed? true} ;; <- ALREADY PROCESSED
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :confidence 0.9 :cites ["spec"] :value {}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:ok? verdict)) "Already-processed batch should fail")))

(deftest test-good-batch-passes
  "Well-formed batch with all checks passing should be approved."
  (let [batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0
               :batch-temp-c 20 :holding-time-hours 24 :sanitation-score 90
               :product-id :soybean-oil :jurisdiction "US"
               :metal-detector :pass :microbial-test :pass
               :contamination-flag-raised? false
               :evidence-checklist #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit}}
        st (-> (store/init-store)
               (store/create-batch "b1" batch))
        proposal {:op :log-production-batch :stake :log-production-batch
                 :confidence 0.95 :cites ["spec"] :value {:jurisdiction "US"}}
        request {:op :log-production-batch :subject "b1"}
        verdict (governor/check request {} proposal st)]
    (is (not (:hard? verdict)) "Good batch should pass governor")
    ;; Note: still escalates because :high-stakes, but not hard
    (is (:escalate? verdict) "High-stakes should escalate")))
