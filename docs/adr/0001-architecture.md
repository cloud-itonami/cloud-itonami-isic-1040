# ADR-0001: OilsFatsOps Actor Architecture

**Date**: 2026-07-14  
**Status**: Accepted  
**Scope**: cloud-itonami-isic-1040 reference implementation  

## Context

ISIC 1040 (Manufacture of vegetable and animal oils and fats) is a food-
manufacturing vertical requiring strict quality and food-safety governance.
Production operations involve:

1. **Intake**: receiving feedstock (crude oils, animal fats) from suppliers
2. **Processing**: extraction, refining, blending
3. **Quality assurance**: measuring FFA (rancidity), peroxide value (oxidation),
   sanitation
4. **Logistics**: storage under temperature control, shipment to customers

Upstream actors (robotics, extraction units) are outside scope; this actor
coordinates the *operations* (batch logging, maintenance, shipment).

The challenge: **LLM confidence is insufficient for food-safety decisions**.
FFA levels, peroxide values, and batch holding times are regulatory facts, not
soft recommendations.

## Solution: Advisor + Independent Governor

```
[Advisor] (LLM proposes action)
    ↓
[Governor] (Independent compliance layer — CANNOT be overridden)
    ↓
[Effect] (propose / hold / escalate)
    ↓
[Audit Ledger] (append-only record)
```

### Advisor layer (`oilsfats.advisor`)

The LLM (or mock) proposes an action given:
- Current batch state (FFA, PV, sanitation, temperature, age)
- Plant state (maintenance schedule, storage capacity)
- Request (log-batch, schedule-maintenance, coordinate-shipment, flag-concern)

Proposal includes:
- `:op` — operation kind
- `:confidence` — LLM's confidence (0.0–1.0)
- `:value` — structured proposal details
- `:cites` — regulatory/policy references
- `:stake` — high-stakes flag (if true, always escalates)

**Current implementation**: MockAdvisor (deterministic test scenarios)  
**Production implementation**: langchain + Claude backend (future)

### Governor layer (`oilsfats.governor`)

The Governor censors every proposal against hard rules:

**Hard blocks (ALWAYS → HOLD)**:
1. No jurisdiction citation
2. Evidence checklist incomplete
3. FFA exceeds limit (per product + jurisdiction)
4. Peroxide value exceeds limit
5. Batch temperature out of range
6. Holding time exceeded
7. Sanitation score insufficient
8. Metal detector failure
9. Microbial test failure
10. Contamination flag unresolved
11. Batch already processed
12. Shipment already finalized

**Soft gates (→ ESCALATE)**:
- LLM confidence < 0.6
- High-stakes operation (log, maintenance, shipment)

**Verdict**: `{:ok? bool :hard? bool :escalate? bool :confidence c :violations [...]}`

Effects:
- `:ok?` true && `:escalate?` false → `:propose` (accept proposal, no escalation needed)
- `:hard?` true → `:hold` (REJECT, document violation)
- `:escalate?` true → `:escalate` (accept in theory, but require human sign-off)

### Registry layer (`oilsfats.registry`)

Pure functions implementing quality checks. No external deps, no state.

```clojure
(ffa-exceeds-limit? 0.6 :soybean-oil "US")
(peroxide-value-exceeds-limit? 15.0 :soybean-oil "EU")
(holding-time-exceeded? 2500 "US")  ;; 2500 hours > 2160 (90 days)
```

Each check is **unconditional**, **auditable**, and **defensible under inspection**.

### Store layer (`oilsfats.store`)

Working memory: batch records, maintenance logs, shipment records, audit facts.

**Immutable updates**: store operations return new store, never mutate.

**Append-only ledger**: all proposals, holds, escalations logged as facts.

```clojure
{:batches {"b1" {...batch-data...}}
 :facts [{:t :governor-hold, :op :log-production-batch, ...}
         {:t :governor-escalate, :op :coordinate-shipment, ...}]
 :maintenance {...}
 :shipments {...}}
```

### Operation layer (`oilsfats.operation`)

Synchronous business logic (stateless):

```clojure
(operation/run-operation store request context advisor governor)
→ {:effect :propose/:hold/:escalate
   :fact audit-fact
   :next-store updated-store
   :verdict governor-verdict}
```

**No side effects** (except store state update). Ready for langgraph StateGraph
integration or standalone batch processing.

## Data flow: Example

**Scenario**: Soybean oil batch arrives; FFA is 1.2% (exceeds 0.5% US limit).

```
1. Store: create-batch("b1", {:product-id :soybean-oil, :ffa-percent 1.2, ...})
2. Advisor: propose({:op :log-production-batch, :subject "b1", :confidence 0.85, ...})
3. Governor: check(request, context, proposal, store)
   - evidence-incomplete-violations? NO
   - ffa-exceeds-limit-violations? YES → [{:rule :ffa-exceeds-limit, :detail "..."}]
   - Result: {:ok? false, :hard? true, :escalate? false}
4. Operation: run-operation(...)
   - Effect: :hold
   - Fact: {:t :governor-hold, :violations [{:rule :ffa-exceeds-limit, ...}]}
   - Next store: append fact
5. Ledger: fact recorded; batch CANNOT proceed until FFA ≤ 0.5%
```

## Jurisdictional limits

Each jurisdiction defines its own bounds:

| Jurisdiction | FFA limit | PV limit | Sanitation min | Holding time max | Evidence required |
|---|---|---|---|---|---|
| US | 0.5% | 10.0 mEq/kg | 85 | 90 days | ffa-assay, peroxide-test, microbial, sanitation-audit |
| EU | 0.3% | 5.0 mEq/kg | 90 | 60 days | + traceability-log |
| JP | 0.4% | 8.0 mEq/kg | 88 | 75 days | + sensory-test |

Governor looks up jurisdiction on proposal and enforces that set of limits
unconditionally.

## Rollout phases

```
Phase 0 (now): Blueprint + mock advisor + test suite ✓
Phase 1: langgraph StateGraph integration
Phase 2: All proposals escalate (intake-propose phase)
Phase 3: Independent governance (production phase)
Phase 4: Optimization (predictive quality trends)
```

Each phase gates which operations are allowed (see `oilsfats.phase`).

## Testing strategy

- **Unit tests**: Governor hard-blocks, registry checks, store ops
- **Integration tests**: advisor → governor → operation flow
- **Scenario tests**: realistic batches (good, rancid, incomplete evidence)
- **Jurisdiction tests**: US/EU/JP limit boundaries
- **Ledger tests**: audit trail correctness

All tests in `.cljc` (JVM + ClojureScript compatible).

## Non-scope

This actor does **NOT**:
- Control extraction/refining equipment (exclusive to robotics/operators)
- Certify food-safety compliance (that's regulatory authority)
- Mutate external registries or supply-chain systems
- Perform real actuation (only proposes; humans/downstream execute)

## References

- **ADR-2607121000**: Reverse-toposort rollout plan (Wave 3)
- **ADR-2607122200**: Ishokuju blueprint satellites (ISIC 10-12 food verticals)
- **ADR-2607011000**: Robotics premise (fleet infrastructure)
- **21 CFR 184**: FDA GRAS oils/fats standards
- **Commission Directive 91/321/EEC**: EU oils/fats food rules
- **食品衛生法** (FSL): Japanese food sanitation law

## Alternatives considered

1. **Single LLM authority**: Rejected. LLM confidence is insufficient for
   food-safety decisions; hard governance requires independent system.

2. **Soft thresholds in Governor**: Rejected. Quality limits are regulatory
   facts, not soft heuristics. All checks are hard blocks.

3. **Centralized compliance service**: Rejected. This actor is self-contained
   and forkable; all limits are baked in.

4. **Process control via actor**: Rejected. Equipment operation (extraction
   pressure, refining temp) remains exclusive to licensed operators and robotics
   safety systems.

## Decisions

1. **Governor is separate**: A distinct `:oils-fats-governor` keyword-identity
   system that cannot be overridden by LLM confidence.

2. **Registry is pure**: All quality checks are stateless functions, auditable,
   testable in isolation.

3. **Limits are facts**: FFA/PV/sanitation/holding-time thresholds are baked
   into `registry.cljc`, versioned, and cited in ADRs.

4. **All proposals escalate high-stakes**: Batch logging, maintenance, shipment
   are real-world operations requiring human sign-off, even if Governor approves.

5. **Contamination is hard-hold**: Unresolved contamination flags ALWAYS block
   batch progression.

6. **Ledger is append-only**: No mutations; every decision is timestamped and
   attributed to the actor.

## Future work

- Langgraph StateGraph integration (Phase 1)
- Production LLM backend (Claude)
- Predictive quality trends (Phase 4)
- Integration with supply-chain registry (kotobase)
- Real-time telemetry (temperature, FFA monitoring)

---

**Approved by**: Jun Kawasaki (cloud-itonami lead)  
**Date**: 2026-07-14
