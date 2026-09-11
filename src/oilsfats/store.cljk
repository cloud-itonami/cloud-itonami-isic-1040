(ns oilsfats.store
  "Working memory for OilsFatsOps: a plain immutable map, threaded through
  `oilsfats.operation/run-operation` and updated via `clojure.core/update` /
  `assoc-in`, never mutated. Production should migrate this to Datomic /
  kotoba-server (the same seam point every cloud-itonami actor uses) behind
  the same function signatures.

  Store shape:
    {:batches     {batch-id -> batch-record}
     :facts       [append-only audit fact ...]
     :shipments   {shipment-id -> shipment-record}
     :maintenance {maintenance-id -> maintenance-record}}

  A batch record is the minimal unit of production work: one lot of
  feedstock (crude soybean/palm/olive oil, animal tallow) tracked from
  intake through quality assay, storage, and shipment. Representative keys:
    :batch-id :product-id :jurisdiction :ffa-percent :peroxide-value-meq-kg
    :batch-temp-c :holding-time-hours :sanitation-score :metal-detector
    :microbial-test :contamination-flag-raised? :contamination-flag-resolved?
    :evidence-checklist :processed? :shipment-finalized?")

(defn init-store
  "Create an empty store."
  []
  {:batches {} :facts [] :shipments {} :maintenance {}})

(defn batch-exists? [st batch-id]
  (contains? (:batches st) batch-id))

(defn production-batch
  "Retrieve a batch record by id, or nil."
  [st batch-id]
  (get-in st [:batches batch-id]))

(defn create-batch
  "Register a new batch. Idempotent: if `batch-id` already exists, the
  existing record is kept (first registration wins -- a proposal can never
  silently overwrite an already-registered batch's data)."
  [st batch-id batch]
  (if (batch-exists? st batch-id)
    st
    (update st :batches assoc batch-id batch)))

(defn update-batch-field
  "Update a single field on an existing batch. No-op if the batch does not
  exist."
  [st batch-id field value]
  (if (batch-exists? st batch-id)
    (assoc-in st [:batches batch-id field] value)
    st))

(defn update-batch
  "Merge `fields` into an existing batch. No-op if the batch does not
  exist."
  [st batch-id fields]
  (if (batch-exists? st batch-id)
    (update-in st [:batches batch-id] merge fields)
    st))

(defn batch-already-processed?
  "One-way flag: has `:log-production-batch` already committed for this
  batch? Used by the Governor to refuse double-processing."
  [st batch-id]
  (true? (:processed? (production-batch st batch-id))))

(defn mark-batch-processed
  "Mark a batch as processed. No-op if the batch does not exist."
  [st batch-id]
  (if (batch-exists? st batch-id)
    (assoc-in st [:batches batch-id :processed?] true)
    st))

(defn batch-shipment-finalized?
  "One-way flag: has `:coordinate-shipment` already committed for this
  batch? Used by the Governor to refuse double-shipment."
  [st batch-id]
  (true? (:shipment-finalized? (production-batch st batch-id))))

(defn mark-batch-shipment-finalized
  "Mark a batch's shipment as finalized. No-op if the batch does not
  exist."
  [st batch-id]
  (if (batch-exists? st batch-id)
    (assoc-in st [:batches batch-id :shipment-finalized?] true)
    st))

(defn add-fact
  "Append an audit fact to the append-only ledger."
  [st fact]
  (update st :facts conj fact))

(defn facts-for-batch
  "Retrieve all audit facts recorded against `batch-id`."
  [st batch-id]
  (filterv #(= batch-id (:subject %)) (:facts st)))

(defn add-maintenance-record [st maintenance-id record]
  (assoc-in st [:maintenance maintenance-id] record))

(defn maintenance-record [st maintenance-id]
  (get-in st [:maintenance maintenance-id]))

(defn add-shipment-record [st shipment-id record]
  (assoc-in st [:shipments shipment-id] record))

(defn shipment-record [st shipment-id]
  (get-in st [:shipments shipment-id]))
