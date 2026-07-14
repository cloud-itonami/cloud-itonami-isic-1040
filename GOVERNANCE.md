# Governance

## Overview

This repository is maintained as part of the **cloud-itonami** open business
fleet (ADR-2607122200). Governance follows the cloud-itonami standards:

- **Maintained by**: cloud-itonami collective (individual maintainers listed below)
- **License**: AGPL-3.0-or-later (derivative forks must remain AGPL)
- **Decision model**: Consensus-seeking on food-safety matters; maintainers have
  final decision authority on code merges
- **Community**: Open to contributors from food-safety, ops, and Clojure communities

## Maintainers

- **Primary**: Jun Kawasaki <jun784@gmail.com> (cloud-itonami lead, architecture)
- **Review (food-safety domain)**: TBD (domain expert participation encouraged)

## Decision authority

### Code merges

Maintainers approve/merge PRs for:
- New features, bug fixes, refactors
- Test additions, documentation improvements

No single maintainer blocks code forever; disputes escalate to the broader
cloud-itonami collective.

### Food-safety changes

Changes to quality limits (FFA/PV thresholds) or Governor hard-blocks require:

1. Regulatory citation (CFR, EU Directive, FSL, etc.)
2. Peer review by someone with food-safety domain expertise
3. Test case demonstrating the new limit
4. ADR if the change is significant (e.g., adding a new jurisdiction)

### Major decisions

Architectural changes, new operations, new rollout phases require ADR (architecture
decision record) in `docs/adr/`. See existing ADRs for format.

## Community roles

### Contributors

- Submit PRs and issues
- No formal approval needed; work on any feature
- Expect response within 5 business days

### Reviewers

- Comment on PRs (testing, design feedback)
- No merge authority without maintainer status

### Maintainers

- Merge approved PRs
- Release new versions
- Respond to security issues
- Maintain docs and roadmap

## Voting (disputes)

If consensus breaks down on a PR or feature request:

1. Maintainers seek input from broader cloud-itonami collective
2. Vote (simple majority) on the disputed decision
3. Result is binding; losing side can fork under AGPL

## Transparency

- All ADRs are public in `docs/adr/`
- Meeting notes (if any) are posted to the repo
- Major decisions are announced in CHANGELOG
- Roadmap (Phase 0-4) is public

## Release process

1. Increment version in `blueprint.edn` (`:itonami.blueprint/version`)
2. Update CHANGELOG
3. Tag release: `git tag v<X.Y.Z>`
4. Push tag: `git push origin v<X.Y.Z>`
5. GitHub Actions auto-publishes to Clojars (TBD)

## Reporting violations

If a contributor violates the Code of Conduct:

1. Email maintainers at <jun784@gmail.com>
2. Include specific details and impact
3. Expect response within 48 hours
4. Resolution may include issue closure, ban, or mediation

## Forks

AGPL-3.0-or-later permits forks at any time. Forkers must:

- Retain the AGPL license in all copies
- Disclose all modifications
- Make source available to end users
- Credit the original authors

Forks are encouraged for:
- Customizing quality limits to your jurisdiction
- Adding domain-specific operations
- Integrating with your fleet infrastructure

Rejoin the original repo with PRs when you have improvements the community
can benefit from.

## Future governance evolution

As the collective grows, governance may evolve:

- Adding a steering committee
- Formalizing dispute resolution
- Creating workgroups (food-safety, robotics, etc.)

Changes to governance require consensus-seeking and an ADR.

---

**Last updated**: 2026-07-14

**Next governance review**: 2026-10-14 (quarterly)
