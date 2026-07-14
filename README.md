# cloud-itonami-isic-1040

Open Business Blueprint for **ISIC 1040**: Manufacture of vegetable and animal
oils and fats — the industrial feedstock vertical of the vegetable-oils supply
chain (食油 / edible & industrial fats).

**Maturity: `:blueprint`** — this repository publishes the business
blueprint and reference implementation. The actor is ready for integration into
the cloud-itonami fleet but requires robotics premise gating per ADR-2607121000.
ISIC division 10-12 (food) sits in **rollout Wave 3 (production/robotics)**:
implementation is gated on the robotics premise (ADR-2607011000) — a real robot
fleet plus an independent governor with an accident-free audit ledger.

## What the actor is

**OilsFatsOps-LLM ⊣ Oils/Fats Manufacturing Governor** — the fleet-standard
pattern: the advisor LLM coordinates intake, quality assay scheduling (FFA/PV
monitoring), batch traceability, and shipment logistics; the independent
`:oils-fats-governor` (a keyword unique fleet-wide) gates every action; physical
extraction/refining equipment control is executed by robots under
`kotoba-lang/robotics` safety classes, never dispatched directly by the LLM.
Food-safety-critical actions (batch logging, contamination flagging) require
human sign-off.

## Operating model

Plant operations coordination: **NOT** direct process control.

The actor receives:
1. **Intake batches** — incoming feedstock (crude soybean, palm, olive oils;
   animal tallow) with provenance and initial screening
2. **Quality assay results** — FFA (free fatty acid, indicator of rancidity),
   peroxide value (PV, indicator of oxidation), microbial counts, metal detector
   screening
3. **Facility state** — sanitation scores, maintenance schedules, storage
   conditions (temperature, humidity)

The actor proposes:
- `:log-production-batch` — register batch into production records (requires
  FFA/PV/sanitation/evidence within limits + human sign-off)
- `:schedule-maintenance` — preventative equipment maintenance (requires human
  sign-off during Phase 2; autonomous in Phase 3)
- `:coordinate-shipment` — finalize cold-chain logistics and dispatch (requires
  human sign-off for high-stakes batches)
- `:flag-food-safety-concern` — escalate rancidity, oxidation, contamination
  concerns (always escalates)

The Governor enforces **hard blocks**:
- FFA exceeds jurisdiction limit (rancidity sign) → HOLD
- Peroxide value exceeds limit (oxidation sign) → HOLD
- Batch temperature out of spec range → HOLD
- Holding time exceeded (quality degradation risk) → HOLD
- Sanitation score below minimum → HOLD
- Metal detector failure → HOLD
- Microbial test failure → HOLD
- Contamination flag unresolved → HOLD
- Evidence checklist incomplete → HOLD

Operating states:
```
intake → [quality-assay-queue] → [governance-propose] → [human-review] → production-log
                                                      ↘ escalate (contamination/low-confidence)
                                                      ↘ hold (hard violation)
    ↓
storage [temperature-maintained, FFA/PV monitored]
    ↓
[shipment-propose] → [human-sign-off] → cold-chain-dispatch → customer-delivery
```

## Regulatory scope

**Food-safety jurisdiction**: applies to all oils/fats destined for food use.
The actor knows three jurisdictions (extensible):
- **US**: USDA-FSIS, 21 CFR 184 (GRAS oils), FSMA rules
- **EU**: EFSA, Commission Directive 91/321/EEC (stricter FFA limits)
- **JP**: MHLW-FHSIS, 食品衛生法 (FSL)

Each jurisdiction has different FFA limits (0.3–0.6%), PV limits (5.0–10.0
mEq/kg), holding-time windows (60–90 days), and evidence requirements.

## Testing

All source modules are `.cljc` (portable Clojure) with full test coverage.

```bash
# Run full test suite
clojure -M:test

# Run specific namespace
clojure -M:test --exclude oilsfats.sim-test

# Static analysis (clj-kondo)
clojure -M:lint
```

Example simulation (mock advisor):
```bash
clojure -M:dev:run
```

## Architecture

### Core modules

- `oilsfats.facts` — domain model (product types, jurisdictions, evidence reqs)
- `oilsfats.registry` — pure quality/food-safety checks (FFA/PV/temp/time/sanitation)
- `oilsfats.governor` — independent compliance layer (HOLD/ESCALATE decisions)
- `oilsfats.advisor` — proposal layer (mock + production LLM interface)
- `oilsfats.operation` — state machine core (advisor → governor → effect)
- `oilsfats.store` — working memory (batch state, audit ledger)
- `oilsfats.phase` — rollout gate (which ops allowed at which stage)
- `oilsfats.sim` — demo driver

### Key design decisions

1. **Governor is independent**: `:oils-fats-governor` is a separate keyword-
   identity system that CANNOT be overridden by LLM confidence. All hard checks
   are unconditional.

2. **FFA/PV checks are *data*, not authority**: FFA and peroxide value limits
   are facts living in `registry.cljc`, defensible under audit. No soft thresholds.

3. **Jurisdictional limits baked in**: Each jurisdiction (US/EU/JP) has its own
   FFA/PV/sanitation/holding-time windows. The actor knows these and enforces them.

4. **All proposals cascade through Governor**: Every LLM proposal is routed through
   `governor.check`, which independently verifies the batch data against spec.
   Proposals that fail HOLD; borderline/low-confidence proposals ESCALATE.

5. **High-stakes actions always escalate**: `:log-production-batch`,
   `:schedule-maintenance`, `:coordinate-shipment` are real-world operations that
   require human sign-off, even if all checks pass.

6. **Contamination is hard-hold**: Unresolved contamination flags (FFA spike,
   mold, water intrusion, suspected pathogen) ALWAYS block batch progression
   until human resolves.

7. **No direct process control**: This actor operates on batch metadata
   (quality assays, traceability, sanitation records). Equipment control
   (extraction pressure, refining temperature, centrifuge speed) remains
   exclusively under robotics/operator authority.

## Fork safety

AGPL-3.0-or-later, forkable by any qualified operator, so local oil processors
never surrender production and traceability data to a closed SaaS. This repo is
standalone:

- `:local/root` dependencies (`kotoba-lang/langchain`, `kotoba-lang/langgraph`)
  resolve inside the workspace monorepo only.
- Standalone forks should override `:local/root` with git/github coordinates.
- The `registry.cljc` checks are pure functions — deployable without external
  libs, auditable offline, testable in isolation.

## Deploy path

1. **Phase 0 (now)**: Blueprint / mock advisor + test suite
2. **Phase 1**: Integration with `kotoba-lang/langgraph` StateGraph + kototama
   actor host
3. **Phase 2**: Robotics premise gating (fleet-wide admission, ADR-2607011000)
4. **Phase 3**: Production (all operators escalate to human)
5. **Phase 4+**: Optimization (predictive trends, auto-scheduling)

## References

- **ADR-2607121000**: Reverse-toposort rollout plan (Wave 3: robotics/production)
- **ADR-2607122200**: Ishokuju blueprint satellites
- **ADR-2607011000**: Robotics premise
- **ISIC Rev. 5**: UN industrial classification (1040 = oils/fats manufacture)
- **21 CFR 184**: FDA GRAS (generally recognized as safe) oils & fats
- **Commission Directive 91/321/EEC**: EU oils/fats food standards
- **食品衛生法** (FSL): Japanese food sanitation law

## License

AGPL-3.0-or-later.

---

**Next**: Read `docs/adr/0001-architecture.md` for design deep-dive.
