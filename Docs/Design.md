# Design

No high-fidelity mockups exist yet — this doc captures design principles and the screen inventory that should guide UI work in the Next.js frontend (see [[TechSpecifications]]). Treat it as a living doc; update it once real screens/wireframes exist (see [[OpenQuestions]] on whether a design tool will be used at all).

## 1. Design Principles

- **Per-role clarity, not one generic UI.** Each of the six roles gets a purpose-built dashboard/home view showing only what's relevant to them — not a shared screen with hidden/disabled controls.
- **The sample tracker is the hero interaction.** It should feel like tracking an online order: a horizontal/vertical progress indicator, plain-language stage labels, and a timestamp per stage — not a raw status enum dumped on screen.
- **Built from real components (Mantine).** Tables for lists (appointments, patients, inventory), forms with validation for data entry, charts for the admin dashboard (revenue, TAT, test volume), notifications for real-time-feeling alerts (e.g., sample rejected). Avoid bare/templated pages — this is explicitly part of what's being evaluated (see [[TechSpecifications]]).
- **Every page handles loading, empty and error states.** Data comes over the network — no blank screens while fetching, and a clear message when the API returns an error.
- **Responsive.** Staff screens target desktop/tablet; patient screens must work in a phone browser.
- **Sensitive data stays scoped.** UI must never expose controls or data outside what a role is permitted to see (ties directly to [[Rules]] and [[Security]] — a UI-level affordance for something the backend would reject is a design bug, not just a backend one).

## 2. Screen Inventory (by role)

### Patient
- Login / registration
- Book appointment
- My appointments (upcoming/past)
- Medical history / EMR view (read-only)
- Prescriptions (list + PDF download)
- Sample tracker (visual, per active sample)
- Reports (list + PDF download)
- Invoices / billing (view + pay)

### Doctor
- Login
- Today's schedule / calendar view
- Patient search (basic details for everyone; a badge on patients they can open)
- Patient record view (full EMR, only with a care relationship; otherwise a clear "no appointment with this patient" state — not an error page)
- Consultation notes entry
- Prescription writer
- Order lab test(s) from patient record
- Verified lab reports for their patients

### Receptionist
- Login
- Patient registration form
- Appointment queue / walk-in token issuance
- Billing / invoice counter view
- Sample-rejected notifications inbox

### Lab Technician
- Login
- Incoming orders queue (returned-for-retest samples pinned at the top with the pathologist's reason)
- Sample collection entry (tube type, body site, timestamp)
- Sample receipt check (accept/reject with reason)
- Result entry per analyzer/test, with a secondary **Reject sample** action (exhausted / degraded / other) behind a confirmation dialog
- My processed samples (history)

### Pathologist
- Login
- Verification queue (oldest first, out-of-range results flagged)
- Result review with two actions: **Verify** or **Return for retest** (reason picker + note). Shows value, reference range, sample details, patient's previous results for this test, and earlier attempts if this is a retest
- My verifications (history)
- Profile (qualification, registration number, signature image used on reports)

### Admin
- Login
- Dashboard (revenue, patient counts, most-ordered tests, staff performance, TAT charts)
- Staff account management
- Record access log (filter by patient, staff member, date)
- Inventory management (stock levels, low-stock alerts)

## 3. Sample Tracker UI Detail

Patient-facing stages shown (internal-only detail like analyzer ID is hidden from this view):

```
● Ordered → ● Collected → ● Received at Lab → ● In Testing → ● Verified → ● Report Ready
```

Each completed stage shows its timestamp; the current stage is highlighted; future stages are greyed out. If rejected, the tracker should clearly communicate "sample needs to be redrawn" rather than silently stalling. A return for retest is internal: the patient's tracker simply stays at "In Testing" — no alarming message, no reason shown.

## 4. Open Items

- No wireframes/mockups produced yet.
- No color/branding system decided (fine for a functional academic/demo project, but flag if visual polish becomes a scoring factor).
- Whether Figma or another design tool will be used before implementation is unresolved — see [[OpenQuestions]].
