(ns oilsfats.governor-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oilsfats.governor :as governor]
   [oilsfats.store :as store]))

(deftest ^{:doc "Proposal with no jurisdiction citation must be rejected."} test-no-spec-basis-violation
  (let [proposal {:op :log-production-batch :cites [] :value {}}
        request {:op :log-production-batch}
        st (store/init-store)]
    (is (seq (governor/check request {} proposal st))
        "Should have violations")))

(deftest ^{:doc "Proposals below confidence floor must escalate."} test-confidence-floor
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

(deftest ^{:doc "High-stakes operations must always escalate."} test-high-stakes-ops-escalate
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

(deftest ^{:doc "Batches with FFA above jurisdiction limit must be rejected."} test-ffa-exceeds-limit
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

(deftest ^{:doc "Batches with PV above jurisdiction limit must be rejected."} test-peroxide-value-exceeds-limit
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

(deftest ^{:doc "Batches outside storage temp range must be rejected."} test-temp-out-of-range
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

(deftest ^{:doc "Batches exceeding max holding time must be rejected."} test-holding-time-exceeded
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

(deftest ^{:doc "Unresolved contamination flags must block batch."} test-contamination-flag-unresolved
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

(deftest ^{:doc "Metal detector failures must block batch."} test-metal-detector-failure
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

(deftest ^{:doc "Already-processed batches must be rejected."} test-already-processed
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

(deftest ^{:doc "Well-formed batch with all checks passing should be approved."} test-good-batch-passes
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
