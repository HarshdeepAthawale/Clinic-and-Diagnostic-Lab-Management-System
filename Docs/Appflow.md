# Application Flow

Per-role flows, plus the cross-cutting sample lifecycle flow that ties the clinic and lab sides together.

## 1. Cross-Cutting Flow: Consultation → Lab Order → Sample → Report → Billing

```
Patient books appointment
        │
        ▼
Doctor consults, writes notes/prescription, orders lab test(s)
        │
        ▼
Lab order auto-created (no re-entry) ── prep instructions attached (e.g., fasting)
        │
        ▼
Sample lifecycle begins (see §3) ──► Report Generated ──► Dispatched to patient
        │
        ▼
Invoice combines consultation fee + test charges ──► Paid/Unpaid tracked
```

This is the core integration point between the clinic side and the lab side — see [[Schema]] for how `Consultation`, `LabOrder`, and `Sample` link.

## 2. Per-Role Flows

### Patient
1. Register / log in.
2. Book an appointment with a doctor (or walk in and receive a queue token).
3. Attend consultation; doctor may prescribe and/or order tests.
4. If tests ordered: receive prep instructions, get sample collected.
5. Track sample status live (visual tracker, see §3).
6. Once report is ready: view/download PDF report, view visit history and past reports.
7. View and pay invoice.

### Doctor
1. Log in; see today's appointment schedule/calendar.
2. Open a patient's record from the schedule — full EMR visible (history, allergies, past visits) because the appointment creates a care relationship. Patients found by search without one show basic details only, with a note to have the front desk book them in.
3. Conduct consultation, write notes.
4. Write prescription (downloadable PDF, linked to this visit).
5. Order lab test(s) directly from the patient's record if needed — this creates the lab order automatically.
6. View verified lab reports for their patients.

### Receptionist / Front Desk
1. Log in.
2. Register new patients (creates the shared patient record used by both clinic and lab sides).
3. Manage appointment queue — assign slots, issue walk-in tokens.
4. Handle counter billing — generate/collect payment against invoices.
5. Receive automatic notification when a sample is rejected, to call the patient in for a redraw.

### Lab Technician
1. Log in; see incoming test orders queue.
2. Log sample collection: staff ID, timestamp, tube type, body site.
3. On receipt at lab: inspect sample, mark **Received** or **Rejected** (with reason).
4. Run test on analyzer; log which machine and technician.
5. Enter results.
6. Results move to pending pathologist verification — technician cannot release a report themselves.
7. If a pathologist returns a result, it reappears at the top of the queue with the reason; rerun the test on the same sample and enter the new result.
8. If the sample is used up or has degraded during testing, mark it **Rejected** (with reason) instead — the front desk is notified to call the patient for a redraw.

### Pathologist
1. Log in; see the verification queue — results in `Result Entered`, oldest first, out-of-range values flagged.
2. Open a result: value, reference range, sample details (collection/receipt times, tube type), and the patient's previous results for the same test.
3. Either **verify** and digitally sign off — this stamps the result with their name, qualification and registration number and unlocks report generation — or **return for retest** with a reason, which sends the sample back to the lab technician.
4. See their own verification history.

### Admin
1. Log in; view dashboard — daily patient count, revenue, most-ordered tests, staff performance, TAT analytics by test type.
2. Manage staff accounts (create/deactivate, assign roles — including Pathologist accounts with registration details).
3. Manage inventory — view stock levels, low-stock warnings, restock.

## 3. Sample Lifecycle Flow (Differentiator Detail)

State machine:

```
Ordered
   │
   ▼
Collected ───────────────────────────────────┐
   │                                          │
   ▼                                          │
Received at Lab ──(fails quality check)──► Rejected ──► notify front desk ──► redraw
   │                                          (permanent record kept, not deleted)
   ▼ (passes quality check)
In Testing ──(sample exhausted/degraded)──► Rejected ──► notify front desk ──► redraw
   │     ▲
   │     │ return for retest
   │     │ (pathologist + reason; same sample,
   ▼     │  old result kept, new result row)
Result Entered
   │
   ▼
Verified by Pathologist   ◄── hard gate: no report without this
   │
   ▼
Report Generated
   │
   ▼
Dispatched to Patient (email/SMS/download link) ──► receipt confirmation tracked
```

At every arrow, the system logs: timestamp + responsible staff member ID (chain of custody). Two paths don't continue forward: **rejection** terminates that sample instance and spawns a new collection cycle instead of reusing the same sample record; **return for retest** sends the same sample from `Result Entered` back to `In Testing`, keeping the original result on record.

Patient-facing view renders this as a horizontal/vertical progress tracker (Amazon-order-style), showing only patient-relevant stages and hiding internal technician/machine detail.

Full data captured per stage: [[Schema]]. Rejection criteria and verification gate as hard rules: [[Rules]].
