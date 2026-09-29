# Open Questions

Unresolved design/product questions. Tracked here rather than dropped — resolve into [[Decisions]] (or [[PRD]] / [[Rules]] as appropriate) when answered, and remove from this list at that point.

## Tech / Architecture

- **SMS provider:** "Twilio or similar" — no specific provider chosen (see [[Decisions]] ADR-007). Email is built (appointment reminders, "report ready" notices); SMS is listed as a report dispatch channel but stays off until a provider is chosen. Also unclear if SMS is in scope for the initial build at all.
- **Deployment target:** not yet decided — now two deployables (Next.js frontend + Spring Boot backend). See [[Deployment]].

## Product / Domain

- **Which 2–3 stretch features** (from [[PRD]] section 7) will actually be built? Needs a decision once core + differentiator modules are stable — feeds [[ImplementationPlan]].
- **Payment handling:** billing is "record payment" by staff at the counter (cash, card or UPI) — built in Phase 06 (ADR-023). Whether a real online payment gateway is ever needed is still open; it stays out of scope (see [[NonGoals]]).
- **Staff accounts and reagent mapping:** admin staff management (`POST /admin/staff`, deactivate, assign role) and reagent-to-test stock decrement (ADR-026) were not part of Phase 09. Staff stays off the admin menu until built; decide in Phase 10 whether either is a stretch feature.
- **QR code scope:** decided in Phase 07 (ADR-024) — the label prints a QR of the sample code, and the bench's scan field accepts a typed code or a barcode scanner (which types the code). Camera scanning in the browser isn't built; add it only if the demo needs it.

## Ops

- **Environments:** how many environments are actually needed (local, staging, prod) given this is likely a course/evaluation project rather than a production deployment? Affects [[Deployment]] and [[Setup]].
- **Hosting for the app itself** (beyond Supabase for the DB) — not yet chosen.
