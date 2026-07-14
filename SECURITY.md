# Security Policy

## Scope

This repository publishes an open-source actor blueprint and reference
implementation for oils/fats manufacturing coordination. Security concerns fall
into two categories:

1. **Code security** — bugs in Clojure source, dependency vulnerabilities
2. **Food-safety governance** — concerns about the quality-check logic itself

## Reporting vulnerabilities

### Code vulnerabilities

If you discover a security bug in the code (e.g., unsafe dependency, input
validation flaw, authentication bypass), please:

1. **DO NOT open a public issue**
2. Email details to the maintainer listed in `GOVERNANCE.md`
3. Include:
   - Clear description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Proposed fix (if you have one)

Expected response time: 48 hours.

### Food-safety governance concerns

If you identify a concern about the Governor's quality checks or food-safety
logic (e.g., FFA/PV thresholds are incorrect for a jurisdiction, missing
evidence type, incorrect sanitation scoring), please:

1. Open a GitHub Issue with:
   - Title: `[FOOD-SAFETY] <brief description>`
   - Regulatory reference (CFR section, EU Directive, etc.)
   - Proposed fix with citations
2. Maintainers will prioritize review and respond within 5 business days

Note: These are NOT confidential; food-safety concerns must be transparent and
auditable.

## Dependency updates

- Clojure dependencies are pinned in `deps.edn`
- Security updates will be applied promptly to `deps.edn`
- Major version bumps require ADR review

## Testing security

Before deploying to production:

1. Run full test suite (`clojure -M:test`)
2. Run linter (`clojure -M:lint`)
3. Review Governor hard-blocks for your jurisdiction
4. Verify all quality limits against regulatory sources
5. Obtain food-safety domain expert sign-off before go-live

## AGPL compliance

This repository is AGPL-3.0-or-later. Forks and modifications must:

- Retain the AGPL license
- Disclose all modifications
- Make source code available to users
- Not add proprietary restrictions

See LICENSE file for full terms.

## Audit trail

All production use MUST maintain an append-only audit ledger:

- Every proposal logged to `oilsfats.store/facts`
- Timestamp, actor ID, batch ID, decision (propose/hold/escalate)
- Governor violations recorded with rule and detail
- Ledger never mutated — only appended

Auditing the ledger is the path to food-safety accountability.

---

Thank you for helping keep this system secure and compliant.
