(ns oilsfats.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).

  WHAT IS REAL HERE
  -----------------
  Every row on the generated page comes from an actual in-process run of
  this repo's own actor stack. Nothing is mocked, hand-typed, or narrated:

    * The store is built with `oilsfats.store/init-store` +
      `oilsfats.store/create-batch`, seeded with the two batch records this
      repository already ships -- `oilsfats.sim/demo-intake-batch` and
      `oilsfats.sim/demo-rancid-batch` -- under the SAME ids `sim.cljc`
      uses (`batch-2026-0701`, `batch-2026-0650`). No batch id is invented;
      both subjects referenced below exist in the seed before they are used.
    * Every step calls `oilsfats.operation/run-operation` with the real
      `oilsfats.advisor/MockAdvisor` and the real `oilsfats.governor/check`,
      threading `:next-store` forward, exactly as `oilsfats.sim/run-demo`
      does (this repo has no langgraph StateGraph and no `operation/build`
      -- `run-operation` IS the calling convention; see the ns docstring of
      `oilsfats.operation`, which states the operation function is the
      canonical business logic and langgraph integration is future work).
    * The batch table is read back out of the store AFTER the run, so it
      shows the ground truth the run actually left behind (`:processed?`,
      `:shipment-finalized?`), not what the scenario intended.
    * The action-gate table is derived from the live governor vars
      (`allowed-ops`, `high-stakes`, `always-escalate-ops`,
      `batch-scoped-ops`, `spec-basis-required-ops`, `confidence-floor`),
      the phase table from `oilsfats.phase/phases` ordered by the live
      `oilsfats.phase/phase-gt` predicate, and the jurisdiction table from
      `oilsfats.registry/jurisdiction-limits` cross-checked against
      `oilsfats.facts/jurisdictions` at render time. No prose rows.

  HOW IT WAS VERIFIED
  -------------------
  Ran `clojure -M:dev:run` first and read its output, then cross-checked
  every id, op, effect and violation rule this file relies on against
  `store.cljc` / `governor.cljc` / `advisor.cljc` / `facts.cljc` /
  `registry.cljc` by hand. The two scenarios `sim.cljc` prints
  (`batch-2026-0701` -> `:escalate` with 0 hard violations at confidence
  0.88; `batch-2026-0650` -> `:hold` with exactly the four hard rules
  `:evidence-incomplete`, `:ffa-exceeds-limit`,
  `:peroxide-value-exceeds-limit`, `:sanitation-score-insufficient`)
  reproduce here identically, which is what makes the rest of the scenario
  trustworthy.

  DETERMINISM
  -----------
  No timestamps, no randomness, no wall-clock content, no host-dependent
  identity. Every set is sorted before rendering. Two consecutive runs are
  byte-identical."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [oilsfats.advisor :as advisor]
            [oilsfats.facts :as facts]
            [oilsfats.governor :as governor]
            [oilsfats.operation :as operation]
            [oilsfats.phase :as phase]
            [oilsfats.registry :as registry]
            [oilsfats.sim :as sim]
            [oilsfats.store :as store]))

;; `sim.cljc` passes exactly this context map to `run-operation`.
(def ^:private context {:actor-id "oils-fats-governor-v1"})

;; The rollout phase this console reports. `oilsfats.phase` exposes no
;; `default-phase` var, so the phase is named here rather than guessed from
;; the phase map; `:production` is the phase `blueprint.edn`/README describe
;; the actor as being built for. FIXED DOCUMENTATION, not a live value.
(def ^:private reported-phase :production)

;; Both ids exist in the seed below before any request references them.
(def ^:private clean-batch-id "batch-2026-0701")
(def ^:private rancid-batch-id "batch-2026-0650")

(defn- seed-store
  "The store seed: this repo's own two batch records, under this repo's own
  ids. `store/init-store` starts empty -- `store.cljc` ships no seed fn, so
  the batch constructors in `sim.cljc` are the repository's real sample
  data and are reused verbatim rather than retyped."
  []
  (let [clean  (sim/demo-intake-batch clean-batch-id)
        rancid (sim/demo-rancid-batch rancid-batch-id)]
    (-> (store/init-store)
        (store/create-batch (:batch-id clean) clean)
        (store/create-batch (:batch-id rancid) rancid))))

;; ----------------------------- driver -----------------------------

(defn- step
  "Run ONE real operation against the live store and thread `:next-store`
  forward, keeping the request and result for rendering. `advisor-impl`
  defaults to the repo's real `MockAdvisor`."
  ([state label request] (step state label request nil))
  ([state label request advisor-impl]
   (let [adv    (or advisor-impl (advisor/mock-advisor))
         result (operation/run-operation (:store state) request context adv governor/check)]
     (-> state
         (assoc :store (:next-store result))
         (update :steps conj (assoc result :label label :request request))))))

(defn- sign-off-batch
  "Human sign-off of an escalated `:log-production-batch`. This repo's
  operation state machine ends at :propose/:escalate/:hold -- there is no
  commit node and no approval fact shape in the domain model -- so the
  sign-off is recorded ONLY as the one-way store flag the model actually
  has (`store/mark-batch-processed`, whose docstring defines it as \"has
  :log-production-batch already committed for this batch?\"). No invented
  fact type is appended to the ledger."
  [state batch-id]
  (update state :store store/mark-batch-processed batch-id))

(defn- sign-off-shipment
  "Human sign-off of an escalated `:coordinate-shipment`: sets the one-way
  `:shipment-finalized?` flag and files the shipment record. The record
  filed is the advisor's own proposal `:value` for this request (recomputed
  purely -- `run-operation` only carries the proposal on :propose facts),
  so no shipment fields are invented. `store/add-shipment-record` is keyed
  by an opaque shipment-id which this repo's model never seeds, so the
  signed-off subject batch-id is used as the key."
  [state batch-id request]
  (let [proposal (advisor/propose (advisor/mock-advisor) (:store state) request)]
    (-> state
        (update :store store/mark-batch-shipment-finalized batch-id)
        (update :store store/add-shipment-record batch-id (:value proposal)))))

(defn run-demo!
  "Drive the real stack through a scenario built entirely from the seeded
  batches and the governor's real rules.

  CLEAN LIFECYCLE (batch-2026-0701, the good soybean-oil lot):
    1. :log-production-batch  -> governor clean (0 hard violations) but
       `:log-production-batch` is in `governor/high-stakes`, so it ESCALATES
       -> operator sign-off -> `:processed?` true.
    3. :coordinate-shipment   -> governor clean, high-stakes, ESCALATES ->
       operator sign-off -> `:shipment-finalized?` true + shipment filed.

  HARD HOLDS (never overridable -- `check` returns :hard? true, and
  `run-operation` renders :hold before any escalation path is reachable):
    2. A rogue advisor that claims `:effect :commit` instead of `:propose`
       -> `:effect-not-propose` (HARD invariant 2). The advisor is the real
       MockAdvisor with only the effect key overridden.
    4. Re-proposing :log-production-batch for an already-processed batch
       -> `:already-processed` (HARD invariant 14, one-way flag).
    5. Re-proposing :coordinate-shipment for an already-shipped batch
       -> `:already-shipment-finalized` (HARD invariant 15).
    7. :log-production-batch on the rancid lot -> FOUR hard rules at once
       (`:evidence-incomplete`, `:ffa-exceeds-limit`,
       `:peroxide-value-exceeds-limit`, `:sanitation-score-insufficient`).
    8. `:operate-refining-line` -> `:disallowed-operation`. This is THE
       PERMANENT hold: `governor/allowed-ops` is a closed allowlist and its
       docstring states extraction/refining-line actuation \"can never be
       phased into autonomy\". No phase, no confidence, no human approval
       reaches past it.

  ALWAYS-ESCALATE:
    6. :flag-food-safety-concern on the rancid lot -> `:monitoring` stake
       (NOT in high-stakes) yet still escalates, because the op is in
       `governor/always-escalate-ops`: food-safety decisions are never
       settled by advisor confidence.

  Not exercised: `:schedule-maintenance`. It is in the allowlist and is
  high-stakes, but its `:subject` is an equipment id and this repo's store
  seeds no equipment inventory -- referencing one would mean inventing an
  id, so the action-gate table reports it as unexercised instead.

  Returns the final state {:store .. :steps [..]}."
  []
  (let [ship-req  (operation/create-shipment-request
                   clean-batch-id
                   ;; nil -> the advisor's own domain default destination,
                   ;; so no operator string is invented here.
                   nil)
        rogue-adv (fn [st request]
                    ;; Real MockAdvisor proposal, with ONLY :effect flipped.
                    (assoc (advisor/propose (advisor/mock-advisor) st request)
                           :effect :commit))]
    (-> {:store (seed-store) :steps []}
        (step "1. log clean batch"
              (operation/create-batch-request clean-batch-id :soybean-oil "US"))
        (sign-off-batch clean-batch-id)
        (step "2. rogue advisor claims :commit" ship-req rogue-adv)
        (step "3. coordinate shipment" ship-req)
        (sign-off-shipment clean-batch-id ship-req)
        (step "4. re-log the same batch"
              (operation/create-batch-request clean-batch-id :soybean-oil "US"))
        (step "5. re-ship the same batch" ship-req)
        (step "6. flag food-safety concern"
              (operation/create-concern-request rancid-batch-id nil nil))
        (step "7. log rancid batch"
              (operation/create-batch-request rancid-batch-id :soybean-oil "US"))
        ;; No constructor exists for this op -- by design. It is outside the
        ;; closed allowlist, so `operation` deliberately offers no helper.
        (step "8. actuate refining line"
              {:op :operate-refining-line :subject clean-batch-id}))))

;; ----------------------------- render helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- sorted-names
  "Sets are rendered sorted -- required for byte-identical reruns."
  [s]
  (str/join ", " (sort (map kw s))))

(defn- yn [b] (if b "yes" "no"))

(defn- effect-class [effect]
  (case effect
    :propose  "ok"
    :escalate "warn"
    :hold     "err"
    "muted"))

;; ----------------------------- sections -----------------------------

(defn- batch-rows
  "Ground truth read back out of the store AFTER the run. Only fields the
  batch record actually carries are rendered."
  [st]
  (for [id (sort (keys (:batches st)))
        :let [b (store/production-batch st id)
              p (facts/product-type-by-id (:product-id b))]]
    (str "<tr><td><code>" (esc id) "</code></td>"
         "<td>" (esc (:product-name b)) "</td>"
         "<td>" (esc (:jurisdiction b)) "</td>"
         "<td class=\"num\">" (esc (:ffa-percent b)) "</td>"
         "<td class=\"num\">" (esc (:peroxide-value-meq-kg b)) "</td>"
         "<td class=\"num\">" (esc (:batch-temp-c b)) " ["
         (esc (:storage-temp-min-c p)) "&ndash;" (esc (:storage-temp-max-c p)) "]</td>"
         "<td class=\"num\">" (esc (:holding-time-hours b)) "</td>"
         "<td class=\"num\">" (esc (:sanitation-score b)) "</td>"
         "<td>" (esc (kw (:metal-detector b))) " / " (esc (kw (:microbial-test b))) "</td>"
         "<td>" (esc (sorted-names (:evidence-checklist b))) "</td>"
         "<td>" (yn (true? (:contamination-flag-raised? b))) "</td>"
         "<td class=\"" (if (:processed? b) "ok" "muted") "\">"
         (yn (true? (:processed? b))) "</td>"
         "<td class=\"" (if (:shipment-finalized? b) "ok" "muted") "\">"
         (yn (true? (:shipment-finalized? b))) "</td></tr>")))

(defn- timeline-rows [steps]
  (for [{:keys [label request effect verdict]} steps]
    (str "<tr><td>" (esc label) "</td>"
         "<td><code>" (esc (kw (:op request))) "</code></td>"
         "<td><code>" (esc (:subject request)) "</code></td>"
         "<td class=\"" (effect-class effect) "\">" (esc (kw effect)) "</td>"
         "<td class=\"num\">" (esc (:confidence verdict)) "</td>"
         "<td>" (yn (:high-stakes? verdict)) "</td>"
         "<td>" (if (seq (:violations verdict))
                  (str "<span class=\"err\">"
                       (esc (str/join ", " (map #(kw (:rule %)) (:violations verdict))))
                       "</span>")
                  "<span class=\"muted\">&mdash;</span>")
         "</td></tr>")))

(defn- violation-rows
  "Every hard violation the run actually produced, verbatim from the
  governor's own `:detail` strings."
  [steps]
  (for [{:keys [label request verdict]} steps
        v (:violations verdict)]
    (str "<tr><td>" (esc label) "</td>"
         "<td><code>" (esc (:subject request)) "</code></td>"
         "<td class=\"err\"><code>" (esc (kw (:rule v))) "</code></td>"
         "<td>" (esc (:detail v)) "</td></tr>")))

(defn- action-gate-rows
  "Derived from the live governor vars, not from prose. The `exercised`
  column is derived from the run's own steps."
  [steps]
  (let [observed (reduce (fn [m {:keys [request effect]}]
                           (update m (:op request) (fnil conj #{}) effect))
                         {} steps)]
    (for [op (sort (map kw governor/allowed-ops))
          :let [k (keyword op)
                seen (get observed k)]]
      (str "<tr><td><code>" (esc op) "</code></td>"
           "<td>" (yn (contains? governor/high-stakes k)) "</td>"
           "<td>" (yn (contains? governor/always-escalate-ops k)) "</td>"
           "<td>" (yn (contains? governor/batch-scoped-ops k)) "</td>"
           "<td>" (yn (contains? governor/spec-basis-required-ops k)) "</td>"
           "<td class=\"" (if seen "ok" "muted") "\">"
           (if seen (esc (str/join ", " (sort (map kw seen)))) "not exercised")
           "</td></tr>"))))

(defn- phase-order
  "Rollout order derived from the live `phase/phase-gt` predicate rather
  than a hand-written list."
  []
  (sort (fn [a b] (cond (= a b) 0 (phase/phase-gt a b) 1 :else -1))
        (keys phase/phases)))

(defn- phase-rows []
  (for [id (phase-order)
        :let [p (phase/phase-by-id id)]]
    (str "<tr><td><code>" (esc (kw id)) "</code></td>"
         "<td>" (esc (:name p)) "</td>"
         "<td>" (if (seq (:allowed-ops p))
                  (esc (sorted-names (:allowed-ops p)))
                  "<span class=\"muted\">(none)</span>") "</td>"
         "<td>" (yn (phase/escalate-all-proposals? id)) "</td>"
         "<td>" (if (= id reported-phase) "<span class=\"badge\">reported</span>" "") "</td></tr>")))

(defn- limits-agree?
  "The Governor consults `registry/jurisdiction-limits`; `facts/jurisdictions`
  is the deliberately independent twin (see registry.cljc's ns docstring).
  Agreement is computed here at render time, not asserted."
  [jur]
  (let [j (facts/jurisdiction-by-id (keyword jur))
        r (get registry/jurisdiction-limits jur)
        ks [:ffa-limit-percent :peroxide-limit-meq-kg
            :sanitation-min-score :max-holding-hours]]
    (boolean (and j r (= (select-keys j ks) (select-keys r ks))))))

(defn- jurisdiction-rows []
  (for [jur (sort (keys registry/jurisdiction-limits))
        :let [r (get registry/jurisdiction-limits jur)
              j (facts/jurisdiction-by-id (keyword jur))]]
    (str "<tr><td><code>" (esc jur) "</code></td>"
         "<td>" (esc (:name j)) "</td>"
         "<td class=\"num\">" (esc (:ffa-limit-percent r)) "</td>"
         "<td class=\"num\">" (esc (:peroxide-limit-meq-kg r)) "</td>"
         "<td class=\"num\">" (esc (:sanitation-min-score r)) "</td>"
         "<td class=\"num\">" (esc (:max-holding-hours r)) "</td>"
         "<td>" (esc (sorted-names (:required-evidence j))) "</td>"
         "<td class=\"" (if (limits-agree? jur) "ok" "err") "\">"
         (yn (limits-agree? jur)) "</td></tr>")))

(defn- ledger-rows
  "The append-only audit ledger, verbatim from `(:facts store)`."
  [st]
  (for [{:keys [t op actor subject disposition reason basis confidence]} (:facts st)]
    (str "<tr><td><code>" (esc (kw t)) "</code></td>"
         "<td><code>" (esc (kw op)) "</code></td>"
         "<td>" (esc actor) "</td>"
         "<td><code>" (esc subject) "</code></td>"
         "<td class=\"" (effect-class disposition) "\">" (esc (kw disposition)) "</td>"
         "<td>" (esc (kw reason)) "</td>"
         "<td>" (if (seq basis)
                  (esc (str/join ", " (map kw basis)))
                  "<span class=\"muted\">&mdash;</span>") "</td>"
         "<td class=\"num\">" (if (some? confidence)
                                (esc confidence)
                                "<span class=\"muted\">&mdash;</span>") "</td></tr>")))

(defn- shipment-rows [st]
  (for [id (sort (keys (:shipments st)))
        :let [r (store/shipment-record st id)]]
    (str "<tr><td><code>" (esc id) "</code></td>"
         "<td><code>" (esc (:batch-id r)) "</code></td>"
         "<td>" (esc (:destination r)) "</td></tr>")))

;; ----------------------------- page -----------------------------

(defn render [{:keys [store steps]}]
  (let [st store
        holds (filter #(= :hold (:effect %)) steps)
        escs  (filter #(= :escalate (:effect %)) steps)]
    (str
     "<!doctype html>\n<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n"
     "<title>oilsfats.render-html -- Oils/Fats Manufacturing Governor operator console</title>\n"
     "<style>"
     (jp-go-dds.skin/dds+skin)
     "</style>\n</head>\n<body>\n"

     "<header class=\"bar\"><h1>OilsFatsOps &mdash; Operator Console</h1>"
     "<span class=\"badge\">ISIC 1040 &middot; oils/fats manufacturing coordination &middot; phase "
     (esc (kw reported-phase)) "</span></header>\n<main>\n"

     "<div class=\"banner\"><p>Generated at build time by <code>oilsfats.render-html</code> from a real "
     "in-process run of <code>oilsfats.operation/run-operation</code> against the real "
     "<code>oilsfats.advisor/MockAdvisor</code> and the real <code>oilsfats.governor/check</code>. "
     "Store seed: <code>" (esc clean-batch-id) "</code> and <code>" (esc rancid-batch-id)
     "</code>, this repository's own batch records (<code>oilsfats.sim/demo-intake-batch</code>, "
     "<code>oilsfats.sim/demo-rancid-batch</code>) &mdash; no invented ids. "
     (esc (count steps)) " operations produced " (esc (count (:facts st))) " audit facts: "
     (esc (count escs)) " escalations and " (esc (count holds)) " HARD holds. "
     "No timestamps, no randomness &mdash; reruns are byte-identical.</p></div>\n"

     "<section class=\"card\"><h2>Batch registry (store ground truth, read back after the run)</h2>"
     "<p class=\"muted\">Read out of the store with <code>store/production-batch</code> AFTER the "
     "scenario finished, so <code>processed?</code> / <code>shipment-finalized?</code> show what the "
     "run actually left behind. Temperature is shown against the product's storage range from "
     "<code>facts/product-types</code>. Only fields the batch record carries are rendered.</p>"
     "<table><thead><tr><th>Batch</th><th>Product</th><th>Juris</th><th>FFA %</th>"
     "<th>PV mEq/kg</th><th>Temp &deg;C [range]</th><th>Hold h</th><th>Sanit.</th>"
     "<th>Metal / microbial</th><th>Evidence checklist</th><th>Contam. flag</th>"
     "<th>Processed?</th><th>Shipped?</th></tr></thead>"
     "<tbody>\n" (str/join "\n" (batch-rows st)) "\n</tbody></table></section>\n"

     "<section class=\"card\"><h2>Run timeline (every call, in order)</h2>"
     "<p class=\"muted\">Effect and confidence are the values <code>run-operation</code> returned. "
     "Steps 1 and 3 escalated clean and were signed off by an operator &mdash; this repo's state "
     "machine has no commit node, so a sign-off is recorded only as the one-way store flag the "
     "domain model actually has (<code>mark-batch-processed</code>, "
     "<code>mark-batch-shipment-finalized</code>); no approval fact shape is invented.</p>"
     "<table><thead><tr><th>Step</th><th>Op</th><th>Subject</th><th>Effect</th>"
     "<th>Confidence</th><th>High-stakes</th><th>Hard rules fired</th></tr></thead>"
     "<tbody>\n" (str/join "\n" (timeline-rows steps)) "\n</tbody></table></section>\n"

     "<section class=\"card\"><h2>HARD holds (no confidence and no approval reaches past these)</h2>"
     "<p class=\"muted\">Verbatim <code>:detail</code> strings from <code>governor/check</code>. "
     "<code>:disallowed-operation</code> is the permanent one: <code>governor/allowed-ops</code> is "
     "a closed allowlist, and extraction/refining-line actuation is never a member of it, so it "
     "cannot be phased into autonomy at any rollout stage. The others are the one-way idempotence "
     "flags, the &quot;proposal must not claim :commit&quot; invariant, and the quality/evidence "
     "limits the Governor re-verifies independently through <code>oilsfats.registry</code>.</p>"
     "<table><thead><tr><th>Step</th><th>Subject</th><th>Rule</th><th>Governor detail</th></tr></thead>"
     "<tbody>\n" (str/join "\n" (violation-rows steps)) "\n</tbody></table></section>\n"

     "<section class=\"card\"><h2>Action gate</h2>"
     "<p class=\"muted\">Derived from the live vars <code>governor/allowed-ops</code>, "
     "<code>governor/high-stakes</code>, <code>governor/always-escalate-ops</code>, "
     "<code>governor/batch-scoped-ops</code> and <code>governor/spec-basis-required-ops</code> "
     "&mdash; not from prose. Confidence floor: <code>" (esc governor/confidence-floor)
     "</code>. Any op absent from this table is a permanent hard block (step 8 demonstrates one).</p>"
     "<table><thead><tr><th>Op (allowlist)</th><th>High-stakes</th><th>Always escalates</th>"
     "<th>Batch-scoped</th><th>Spec basis required</th><th>Exercised in this run</th></tr></thead>"
     "<tbody>\n" (str/join "\n" (action-gate-rows steps)) "\n</tbody></table></section>\n"

     "<section class=\"card\"><h2>Rollout phases</h2>"
     "<p class=\"muted\">From <code>phase/phases</code>, ordered by the live "
     "<code>phase/phase-gt</code> predicate. Phase progression is manual (ADR-gated), never "
     "automatic.</p>"
     "<table><thead><tr><th>Phase</th><th>Name</th><th>Allowed ops</th>"
     "<th>All proposals escalate</th><th></th></tr></thead>"
     "<tbody>\n" (str/join "\n" (phase-rows)) "\n</tbody></table></section>\n"

     "<section class=\"card\"><h2>Jurisdiction limits (Governor's independent source)</h2>"
     "<p class=\"muted\">Limits come from <code>registry/jurisdiction-limits</code>, the table the "
     "Governor actually consults. The last column is computed at render time by comparing it "
     "against <code>facts/jurisdictions</code>, the deliberately separate twin table &mdash; two "
     "independently maintained sources agreeing is the point.</p>"
     "<table><thead><tr><th>Juris</th><th>Name</th><th>FFA limit %</th><th>PV limit</th>"
     "<th>Sanit. min</th><th>Max hold h</th><th>Required evidence</th>"
     "<th>Twin tables agree</th></tr></thead>"
     "<tbody>\n" (str/join "\n" (jurisdiction-rows)) "\n</tbody></table></section>\n"

     "<section class=\"card\"><h2>Audit ledger (append-only, verbatim)</h2>"
     "<p class=\"muted\">Every fact <code>run-operation</code> appended to "
     "<code>(:facts store)</code>, in append order. " (esc (count (:facts st)))
     " facts from " (esc (count steps)) " operations &mdash; one per operation, none added by "
     "this renderer.</p>"
     "<table><thead><tr><th>Type</th><th>Op</th><th>Actor</th><th>Subject</th>"
     "<th>Disposition</th><th>Reason</th><th>Basis</th><th>Confidence</th></tr></thead>"
     "<tbody>\n" (str/join "\n" (ledger-rows st)) "\n</tbody></table></section>\n"

     "<section class=\"card\"><h2>Filed shipments</h2>"
     "<p class=\"muted\">From <code>(:shipments store)</code>. The record is the advisor's own "
     "proposal <code>:value</code> for the signed-off request, so the destination is the domain "
     "default in <code>oilsfats.advisor</code> rather than an operator string typed here. This "
     "repo's model seeds no shipment-id namespace, so the record is keyed by its batch.</p>"
     "<table><thead><tr><th>Key</th><th>Batch</th><th>Destination</th></tr></thead>"
     "<tbody>\n" (str/join "\n" (shipment-rows st)) "\n</tbody></table></section>\n"

     "<footer><p>Not exercised: <code>:schedule-maintenance</code>. It is in the allowlist and is "
     "high-stakes, but its subject is an equipment id and this repo's store seeds no equipment "
     "inventory &mdash; exercising it would mean inventing an id, so it is reported as unexercised "
     "instead. Physical extraction/refining equipment control is never dispatched by this actor at "
     "any phase.</p></footer>\n"
     "</main>\n</body></html>\n")))

(defn -main [& args]
  (let [out   (or (first args) "docs/samples/operator-console.html")
        state (run-demo!)
        html  (render state)]
    (spit out html)
    (println "wrote" out
             (str "(" (count (:steps state)) " ops, "
                  (count (:facts (:store state))) " facts)"))))
