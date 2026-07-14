# Contributing

## Overview

This repository publishes the **OilsFatsOps** actor blueprint and reference
implementation under AGPL-3.0-or-later. Contributions are welcome from
researchers, food-safety practitioners, and ops engineers.

## Before you start

1. **Read the ADRs**: Start with `docs/adr/0001-architecture.md` to understand
   the governor design and rollout phases.

2. **Understand the scope**: This actor coordinates plant **operations** (batch
   logging, maintenance scheduling, shipment), NOT **extraction/refining control**.
   Equipment control remains exclusive to licensed operators under robotics safety.

3. **Food-safety criticality**: Changes to `registry.cljc` (FFA/PV/sanitation
   checks) or `governor.cljc` require peer review by someone with food-safety
   domain knowledge. All quality limits are auditable facts, not overridable.

## Development

### Setup

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-1040
cd cloud-itonami-isic-1040

# Run tests
clojure -M:test

# Run linter (clj-kondo)
clojure -M:lint

# View demo (mock advisor)
clojure -M:dev:run
```

### Code style

- All source is `.cljc` (portable Clojure)
- No JVM-only code in the app layer
- Tests are in `test/oilsfats/*_test.cljc`
- Keep registry functions pure (no side effects)

### Test coverage

- All modules require test coverage
- Governor hard-blocks must be tested
- FFA/PV/temperature checks must exercise jurisdiction boundaries
- Use `clojure.test` fixtures for repeated setup

### Documentation

- Update README if adding operations or jurisdictions
- Add ADR notes for architectural changes
- Document all new governor rules in inline comments

## Submitting changes

1. **Open an issue first** for large changes (new operations, new jurisdictions,
   significant refactors)
2. **Create a branch** off `main`
3. **Run full test suite** before pushing (`clojure -M:test && clojure -M:lint`)
4. **Keep commits focused** (one feature per commit)
5. **Write clear commit messages** referencing the issue
6. **Submit a PR** with a summary of changes

## Review process

- Maintainers will review for:
  - Correctness (does it do what it says?)
  - Food-safety compliance (are limits correct per jurisdiction?)
  - Test coverage
  - Documentation clarity

- PRs require at least one approving review before merge
- Changes to `registry.cljc` or `governor.cljc` require a food-safety domain
  expert review

## Adding a new jurisdiction

1. Add jurisdiction to `oilsfats.facts/jurisdictions`
2. Set FFA, PV, sanitation, holding-time limits
3. Define `:required-evidence` (assays, tests)
4. Add test cases to `facts_test.cljc` and `registry_test.cljc`
5. Update README
6. Cite regulatory source (CFR, EU Directive, etc.)

## Adding a new operation

1. Define operation case in `advisor.cljc` (MockAdvisor)
2. Add governor checks in `governor.cljc` (if needed)
3. Add test cases in `governor_test.cljc`
4. Add operation helper to `operation.cljc`
5. Update README with example flow
6. Add to `phase.cljc` allowed-ops (if applicable)

## Reporting issues

Please report bugs via GitHub Issues with:
- Clear title
- Steps to reproduce
- Expected behavior
- Actual behavior
- Environment (Clojure version, OS)

For security issues, see SECURITY.md.

## License

By contributing, you agree that your contributions will be licensed under the
same AGPL-3.0-or-later license as the repository.

---

Thank you for contributing to safer, more transparent food-manufacturing ops!
