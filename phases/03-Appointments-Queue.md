# Phase 03 — Appointments & Queue Management

**Depends on:** Phase 02 (Patient Registration & EMR)
**Feeds into:** Phase 04 (Consultations & E-Prescriptions)

## Goal

Let patients get in front of a doctor — by booked slot or walk-in token — and give doctors a schedule to work from.

## Scope

- [ ] `Appointment` model + booking flow (Patient or Receptionist can create)
- [ ] Doctor calendar view (today's/upcoming schedule)
- [ ] Walk-in queue token issuance (Receptionist)
- [ ] Appointment status lifecycle: `BOOKED → CHECKED_IN → IN_CONSULTATION → COMPLETED` (plus `NO_SHOW`, `CANCELLED`)
- [ ] Automatic reminder before appointment (email via `@Scheduled` + `JavaMailSender`)

## Exit Criteria

A patient can book a slot with a specific doctor, or a receptionist can issue a walk-in token; the doctor sees it on their calendar/queue; a reminder email fires before a booked appointment.

## Related Docs

- `Docs/Schema.md` §2 — `Appointment` table
- `Docs/Appflow.md` — Patient, Doctor, Receptionist flows
- `Docs/TechSpecifications.md` — background task mechanism (`@Async`/`@Scheduled`)
