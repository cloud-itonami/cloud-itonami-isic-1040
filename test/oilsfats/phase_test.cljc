(ns oilsfats.phase-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oilsfats.phase :as phase]))

(deftest test-phase-by-id
  "Retrieve phase definitions."
  (let [p (phase/phase-by-id :production)]
    (is (= :production (:id p)))
    (is (= "Production (independent governance)" (:name p)))))

(deftest test-disabled-phase-no-ops
  "Disabled phase allows no operations."
  (is (not (phase/phase-allows-operation? :disabled :log-production-batch)))
  (is (not (phase/phase-allows-operation? :disabled :flag-food-safety-concern))))

(deftest test-intake-only-phase-limited
  "Intake-only phase allows intake and flagging only."
  (is (phase/phase-allows-operation? :intake-only :intake-batch))
  (is (phase/phase-allows-operation? :intake-only :flag-food-safety-concern))
  (is (not (phase/phase-allows-operation? :intake-only :log-production-batch)))
  (is (not (phase/phase-allows-operation? :intake-only :coordinate-shipment))))

(deftest test-intake-propose-phase
  "Intake-propose phase allows proposals but escalates all."
  (is (phase/phase-allows-operation? :intake-propose :log-production-batch))
  (is (phase/phase-allows-operation? :intake-propose :schedule-maintenance))
  (is (phase/phase-allows-operation? :intake-propose :coordinate-shipment))
  (is (phase/escalate-all-proposals? :intake-propose)))

(deftest test-production-phase-full
  "Production phase allows all operations."
  (is (phase/phase-allows-operation? :production :log-production-batch))
  (is (phase/phase-allows-operation? :production :schedule-maintenance))
  (is (phase/phase-allows-operation? :production :coordinate-shipment))
  (is (phase/phase-allows-operation? :production :flag-food-safety-concern))
  (is (not (phase/escalate-all-proposals? :production))))

(deftest test-phase-ordering
  "Phase progression from earliest to latest."
  (is (phase/phase-gt :production :intake-only))
  (is (phase/phase-gt :intake-propose :intake-only))
  (is (not (phase/phase-gt :intake-only :production))))
