# Implementation Plan

High-level phase grouping derived from the priority order in [[PRD]] section 8. Each phase should be demoable/functional on its own before moving to the next.

**The authoritative, granular breakdown lives in `/phases/01-...` through `/phases/10-...`** — each has its own scope checklist, dependencies, and exit criteria. This doc is the summary/index; if the two ever disagree, `/phases` wins and this doc should be corrected to match.

## Grouping

| Group | Maps to `/phases` | Summary |
|---|---|---|
| Foundation | `01-Foundation-Access-Control` | Scaffolding, DB, auth, role shell |
| Core Modules ([[PRD]] §5) | `02` through `06` | Patient/EMR, Appointments, Consultations/Prescriptions, Lab Catalog/Ordering, Billing |
| Sample Lifecycle Tracking ([[PRD]] §6, the differentiator) | `07`, `08` | Collection → rejection/redraw, result entry → verification → report/dispatch |
| Analytics | `09-Inventory-Admin-Analytics` | Inventory, admin dashboard, TAT analytics |
| Stretch & Demo Prep ([[PRD]] §7) | `10-Stretch-Features-Demo-Readiness` | 2–3 stretch features + full rehearsal |

## Sequencing Notes

- Phases 02–06 build in order (each depends on the previous) — this alone, once done, is a complete demoable system with samples reduced to a simple status field.
- Phase 07 replaces that simple status field with the real state machine; Phase 08 completes it through verification and report dispatch.
- Phase 09 depends on completed sample lifecycles to compute TAT analytics meaningfully.
- Phase 10 must not start until 01–09 are stable — see [[OpenQuestions]] for which 2–3 stretch features are actually selected.

## Tracking

Day-to-day task status lives in [[Tracker]], seeded from the `/phases` files. Update [[Changelog]] as each phase's pieces actually ship.
