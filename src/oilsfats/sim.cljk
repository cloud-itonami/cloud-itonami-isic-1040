(ns oilsfats.sim
  "Simple simulation driver: intake batch -> advisor proposal -> governor -> result.
  Demonstrates the OilsFatsOps actor end-to-end with a mock advisor.

  Usage: clojure -M:dev:run"
  (:require [oilsfats.advisor :as advisor]
            [oilsfats.governor :as governor]
            [oilsfats.operation :as operation]
            [oilsfats.store :as store]))

;; ============= Demo scenario =============

(defn demo-intake-batch
  "Create a realistic intake batch with typical quality data."
  [batch-id]
  {:batch-id batch-id
   :product-id :soybean-oil
   :product-name "Soybean Oil"
   :jurisdiction "US"
   :intake-weight-kg 10000.0
   :intake-date "2026-07-14"

   ;; Quality assay results (typical for good soybean oil)
   :ffa-percent 0.35
   :peroxide-value-meq-kg 7.5
   :batch-temp-c 18.0

   ;; Holding time (hours since intake)
   :holding-time-hours 24.0

   ;; Sanitation score (% compliance)
   :sanitation-score 92

   ;; Safety screening
   :metal-detector :pass
   :microbial-test :pass

   ;; Contamination flags
   :contamination-flag-raised? false
   :contamination-flag-resolved? nil

   ;; Evidence collected
   :evidence-checklist #{:ffa-assay :peroxide-test :microbial-test :sanitation-audit}

   ;; Traceability
   :supplier-id "supplier-AB123"
   :supplier-location "Iowa, USA"
   :cert-gmo-free? true})

(defn demo-rancid-batch
  "Create a batch that FAILS rancidity checks (FFA too high)."
  [batch-id]
  {:batch-id batch-id
   :product-id :soybean-oil
   :product-name "Soybean Oil (Rancid)"
   :jurisdiction "US"
   :intake-weight-kg 5000.0
   :intake-date "2026-07-12"

   ;; Quality assay results (RANCID)
   :ffa-percent 1.2  ;; <- EXCEEDS US/product limit of 0.5/0.6
   :peroxide-value-meq-kg 18.0  ;; <- Also exceeds PV limit
   :batch-temp-c 22.0

   :holding-time-hours 240.0  ;; 10 days in storage

   :sanitation-score 80  ;; Borderline

   :metal-detector :pass
   :microbial-test :pass

   :contamination-flag-raised? false
   :contamination-flag-resolved? nil

   :evidence-checklist #{:ffa-assay :peroxide-test :microbial-test}  ;; <- Missing sanitation-audit

   :supplier-id "supplier-XY789"
   :supplier-location "Unknown"})

(defn- fmt2
  "Portable (no JVM-only `format`) 2-decimal string. `clojure.core/format`
  has no ClojureScript equivalent; `.toFixed` is available on both hosts'
  number types via reader-conditional host interop."
  [x]
  #?(:clj (format "%.2f" (double x))
     :cljs (.toFixed x 2)))

(defn print-verdict
  "Pretty-print governor verdict."
  [verdict]
  (println (str "\n=== Governor Verdict ==="))
  (println (str "OK: " (:ok? verdict)))
  (println (str "Hard violations: " (count (:violations verdict))))
  (when (seq (:violations verdict))
    (doseq [v (:violations verdict)]
      (println (str "  - " (:rule v) ": " (:detail v)))))
  (println (str "Confidence: " (fmt2 (:confidence verdict)))))

(defn print-fact
  "Pretty-print audit fact."
  [fact]
  (println (str "\n=== Audit Fact ==="))
  (println (str "Type: " (:t fact)))
  (println (str "Op: " (:op fact)))
  (println (str "Subject: " (:subject fact)))
  (println (str "Disposition: " (:disposition fact))))

(defn run-demo
  "Execute one demo scenario: batch -> advisor -> governor -> result."
  []
  (println "=== OilsFats Manufacturing Actor Demo ===\n")

  ;; Scenario 1: Good batch
  (println "Scenario 1: Good batch (low FFA, low PV, clean sanitation)")
  (let [batch (demo-intake-batch "batch-2026-0701")
        st (-> (store/init-store)
               (store/create-batch (:batch-id batch) batch))
        advisor-impl (advisor/->MockAdvisor)
        req (operation/create-batch-request (:batch-id batch) :soybean-oil "US")
        context {:actor-id "oils-fats-governor-v1"}
        result (operation/run-operation st req context advisor-impl governor/check)]

    (println (str "Batch: " (:batch-id batch)))
    (println (str "FFA: " (:ffa-percent batch) "% (limit 0.5%)"))
    (println (str "PV: " (:peroxide-value-meq-kg batch) " mEq/kg (limit 10.0)"))
    (println (str "Effect: " (:effect result)))
    (print-verdict (:verdict result))
    (print-fact (:fact result)))

  ;; Scenario 2: Rancid batch (fails)
  (println "\n\n========================================")
  (println "Scenario 2: Rancid batch (high FFA, high PV, incomplete evidence)")
  (let [batch (demo-rancid-batch "batch-2026-0650")
        st (-> (store/init-store)
               (store/create-batch (:batch-id batch) batch))
        advisor-impl (advisor/->MockAdvisor)
        req (operation/create-batch-request (:batch-id batch) :soybean-oil "US")
        context {:actor-id "oils-fats-governor-v1"}
        result (operation/run-operation st req context advisor-impl governor/check)]

    (println (str "Batch: " (:batch-id batch)))
    (println (str "FFA: " (:ffa-percent batch) "% (limit 0.5%) <- RANCIDITY"))
    (println (str "PV: " (:peroxide-value-meq-kg batch) " mEq/kg (limit 10.0) <- OXIDATION"))
    (println (str "Sanitation: " (:sanitation-score batch) "/100 (min 85)"))
    (println (str "Evidence: " (count (:evidence-checklist batch)) "/4 required"))
    (println (str "Effect: " (:effect result)))
    (print-verdict (:verdict result))
    (print-fact (:fact result))))

(defn -main [& _args]
  (run-demo)
  (println "\n=== Demo complete ==="))
