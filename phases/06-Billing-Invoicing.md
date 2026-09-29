# Phase 06 — Billing & Invoicing

**Depends on:** Phase 05 (Lab Test Catalog & Ordering) — needs both consultation fees and test charges to combine
**Feeds into:** Phase 10 (Demo Readiness) — billing must be exercisable in the full walkthrough
**Status:** Done (ADR-023)

## Goal

One invoice per visit, combining consultation and lab charges, with proper accountability for any discount applied.

## Scope

- [x] `Invoice` model: consultation fee + test charges total + discount (see `Schema.md` §4)
- [x] Invoice auto-generation once a consultation (and any linked lab order) exists — created when the consultation is finished; direct lab orders are billed at once
- [x] Payment status tracking: `UNPAID`, `PARTIALLY_PAID`, `PAID` (plus `VOID` when nothing is left to bill)
- [x] Discount application with mandatory logged staff ID (see `Rules.md` §3) — plus a reason and a front-desk cap
- [x] Receptionist-facing counter billing screen (take payment, view outstanding)
- [x] Patient-facing invoice view + payment status, with PDF

## Exit Criteria

After a consultation with an ordered test, exactly one invoice exists showing the combined total; a receptionist can mark it paid; a discount cannot be applied without recording who applied it.

## Related Docs

- `Docs/Schema.md` §4 — `Invoice`
- `Docs/Rules.md` §3 — billing rules
- `Docs/Appflow.md` §1, §2 — invoice as the tail end of the consultation flow
