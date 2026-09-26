# Phase 10 — Stretch Features & Demo Readiness

**Depends on:** Phase 09 (Inventory & Admin Analytics) — do not start this phase until Phases 01–09 are stable
**Feeds into:** nothing (final phase)

## Goal

Add depth if time allows, then make sure the whole system is demo-ready across every role.

## Scope

### Stretch features (pick 2–3 — see `OpenQuestions.md` for selection status)

Candidates that build cheaply on infrastructure already in place by Phase 09:

- [ ] Critical value alerts (built on existing result entry + reference-range logic)
- [ ] Trend graphs across visits (built on existing EMR + result history)
- [ ] QR-verified reports (built on existing sample QR ID generation)
- [ ] (Other candidates from `PRD.md` §7 if time allows: no-show prediction, test panel bundling, family account linking, prescription templates, reagent-to-test inventory mapping, full audit log on edits, digital consent tracking)

### Demo readiness

- [ ] End-to-end walkthrough rehearsal covering all six roles (see `Appflow.md`)
- [ ] Seed/demo data: patients, staff, samples at various lifecycle stages (including at least one rejected+redrawn sample), historical reports
- [ ] UI polish pass against `Design.md`
- [ ] Security review against `Security.md` checklist
- [ ] Test coverage review against `TestPlan.md` priority test cases

## Exit Criteria

The full walkthrough — register patient → book appointment → consult → prescribe → order test → collect → reject/redraw → test → verify → report → dispatch → invoice paid — runs cleanly with seeded data, for every role, with no known critical bugs.

## Related Docs

- `Docs/PRD.md` §7 — stretch feature list
- `Docs/NonGoals.md` — what is deliberately not being built even at this stage
- `Docs/TestPlan.md`, `Docs/Security.md` — final review checklists
- `Docs/Changelog.md` — record what actually shipped once this phase closes
