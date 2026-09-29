# Phase 10 — Stretch Features & Demo Readiness

**Depends on:** Phase 09 (Inventory & Admin Analytics) — do not start this phase until Phases 01–09 are stable
**Feeds into:** nothing (final phase)

## Goal

Add depth if time allows, then make sure the whole system is demo-ready across every role.

## Scope

### Stretch features (pick 2–3 — see `OpenQuestions.md` for selection status)

Candidates that build cheaply on infrastructure already in place by Phase 09:

- [x] Critical value alerts (built on existing result entry + reference-range logic) — ADR-028
- [x] Trend graphs across visits (built on existing EMR + result history) — ADR-029
- [x] QR-verified reports — ADR-027 (a random code on the report, not the sample code)
- [ ] (Other candidates from `PRD.md` §7 if time allows: no-show prediction, test panel bundling, family account linking, prescription templates, reagent-to-test inventory mapping, full audit log on edits, digital consent tracking)

### Demo readiness

- [x] End-to-end walkthrough rehearsal covering all six roles (see `Appflow.md`) — `scripts/demo-walkthrough.mjs`
- [x] Seed/demo data: patients, staff, samples at various lifecycle stages (including at least one rejected+redrawn sample), historical reports — ADR-031
- [x] UI polish pass against `Design.md`
- [x] Security review against `Security.md` checklist — sign-in limits, API docs off by default (ADR-030)
- [x] Test coverage review against `TestPlan.md` priority test cases

## Exit Criteria

The full walkthrough — register patient → book appointment → consult → prescribe → order test → collect → reject/redraw → test → verify → report → dispatch → invoice paid — runs cleanly with seeded data, for every role, with no known critical bugs.

## Related Docs

- `Docs/PRD.md` §7 — stretch feature list
- `Docs/NonGoals.md` — what is deliberately not being built even at this stage
- `Docs/TestPlan.md`, `Docs/Security.md` — final review checklists
- `Docs/Changelog.md` — record what actually shipped once this phase closes
