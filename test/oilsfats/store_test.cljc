(ns oilsfats.store-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oilsfats.store :as store]))

(deftest test-init-store
  "Initialize empty store."
  (let [st (store/init-store)]
    (is (= {} (:batches st)))
    (is (= [] (:facts st)))
    (is (= {} (:shipments st)))
    (is (= {} (:maintenance st)))))

(deftest test-create-batch
  "Create a new batch in store."
  (let [st (store/init-store)
        batch {:batch-id "b1" :product-id :soybean-oil :ffa-percent 0.3}
        st' (store/create-batch st "b1" batch)]
    (is (= batch (store/production-batch st' "b1")))))

(deftest test-batch-exists
  "Check if batch exists in store."
  (let [st (store/init-store)
        batch {:batch-id "b1"}
        st' (store/create-batch st "b1" batch)]
    (is (store/batch-exists? st' "b1"))
    (is (not (store/batch-exists? st' "b2")))))

(deftest test-batch-already-processed
  "Check if batch has been processed."
  (let [st (store/init-store)
        batch {:batch-id "b1" :processed? false}
        st' (store/create-batch st "b1" batch)]
    (is (not (store/batch-already-processed? st' "b1")))

    (let [st'' (store/mark-batch-processed st' "b1")]
      (is (store/batch-already-processed? st'' "b1")))))

(deftest test-batch-shipment-finalized
  "Check if batch shipment is finalized."
  (let [st (store/init-store)
        batch {:batch-id "b1"}
        st' (store/create-batch st "b1" batch)]
    (is (not (store/batch-shipment-finalized? st' "b1")))

    (let [st'' (store/mark-batch-shipment-finalized st' "b1")]
      (is (store/batch-shipment-finalized? st'' "b1")))))

(deftest test-add-fact
  "Append audit fact to ledger."
  (let [st (store/init-store)
        fact {:t :test-fact :subject "b1"}
        st' (store/add-fact st fact)]
    (is (= 1 (count (:facts st'))))
    (is (= fact (first (:facts st'))))))

(deftest test-facts-for-batch
  "Retrieve facts related to specific batch."
  (let [st (store/init-store)
        fact1 {:t :fact1 :subject "b1"}
        fact2 {:t :fact2 :subject "b1"}
        fact3 {:t :fact3 :subject "b2"}
        st' (-> st
                (store/add-fact fact1)
                (store/add-fact fact2)
                (store/add-fact fact3))
        b1-facts (store/facts-for-batch st' "b1")]
    (is (= 2 (count b1-facts)))
    (is (every? #(= "b1" (:subject %)) b1-facts))))

(deftest test-update-batch-field
  "Update a single field on batch."
  (let [st (store/init-store)
        batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0}
        st' (store/create-batch st "b1" batch)
        st'' (store/update-batch-field st' "b1" :ffa-percent 0.5)
        updated (store/production-batch st'' "b1")]
    (is (= 0.5 (:ffa-percent updated)))
    (is (= 5.0 (:peroxide-value-meq-kg updated)))))

(deftest test-update-batch
  "Update multiple fields on batch."
  (let [st (store/init-store)
        batch {:batch-id "b1" :ffa-percent 0.3 :peroxide-value-meq-kg 5.0}
        st' (store/create-batch st "b1" batch)
        st'' (store/update-batch st' "b1" {:ffa-percent 0.5 :sanitation-score 90})
        updated (store/production-batch st'' "b1")]
    (is (= 0.5 (:ffa-percent updated)))
    (is (= 90 (:sanitation-score updated)))
    (is (= 5.0 (:peroxide-value-meq-kg updated)))))

(deftest test-add-maintenance-record
  "Record equipment maintenance."
  (let [st (store/init-store)
        maint {:equipment-id "pump-A" :type :filter-replacement :date "2026-07-14"}
        st' (store/add-maintenance-record st "maint-1" maint)]
    (is (= maint (store/maintenance-record st' "maint-1")))))

(deftest test-add-shipment-record
  "Record shipment coordination."
  (let [st (store/init-store)
        shipment {:batch-id "b1" :destination "EU warehouse" :mode "truck"}
        st' (store/add-shipment-record st "ship-1" shipment)]
    (is (= shipment (store/shipment-record st' "ship-1")))))

(deftest test-create-batch-idempotent
  "Creating the same batch twice should be idempotent."
  (let [st (store/init-store)
        batch1 {:batch-id "b1" :ffa-percent 0.3}
        st' (store/create-batch st "b1" batch1)
        batch2 {:batch-id "b1" :ffa-percent 0.5} ;; Different data
        st'' (store/create-batch st' "b1" batch2)
        result (store/production-batch st'' "b1")]
    ;; Should keep the first version, not overwrite
    (is (= 0.3 (:ffa-percent result)))))
