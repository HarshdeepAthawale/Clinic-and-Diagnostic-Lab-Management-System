# Data Schema

Initial draft schema derived from the domain described in [[PRD]] and [[Appflow]]. This is the starting point for the Flyway SQL migrations and matching JPA entities (see ADR-010 in [[Decisions]]) — expect it to evolve once implementation starts; keep this file in sync with actual migrations (see [[Contributing]] PR checklist).

## 1. Identity & Roles

### `User`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| email | string, unique | login identifier |
| password_hash | string | |
| role | enum | `PATIENT`, `DOCTOR`, `PATHOLOGIST`, `RECEPTIONIST`, `LAB_TECHNICIAN`, `ADMIN` — one role per account (see [[Decisions]] ADR-011) |
| created_at | timestamp | |
| is_active | boolean | for admin-driven deactivation |

### `Patient`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| patient_code | string, unique | human-readable ID `PID-000123`, from a sequence (V2) |
| user_id | UUID (FK → User), nullable | null until the patient links a login (front desk registers walk-ins first) |
| full_name | string | |
| dob | date | |
| gender | string | |
| phone | string | |
| phone_digits | string, generated | digits of `phone`, indexed for search (V2) |
| address | string | |
| emergency_contact_name | string, nullable | (V2) |
| emergency_contact_phone | string, nullable | (V2) |
| blood_group | string, nullable | `A+` … `O-` (V2) |
| known_allergies | text | |
| medical_history | text, nullable | (V2) |
| claim_code_hash | string(64), unique, nullable | SHA-256 of the one-time registration code; cleared once used (ADR-018, V2) |
| claim_code_expires_at | timestamp, nullable | 30 days after issue (V2) |
| registered_by_user_id | UUID (FK → User), nullable | front-desk user who registered the record (V2) |
| created_at | timestamp | |
| updated_at | timestamp | (V2) |

### `Doctor`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → User) | |
| full_name | string | |
| specialization | string | |
| qualification | string, nullable | printed on prescriptions (V4) |
| registration_number | string, unique, nullable | medical council number, printed on prescriptions (V4) |

### `Pathologist`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → User), unique | user must have role `PATHOLOGIST` |
| full_name | string | printed on the report's verification stamp |
| qualification | string | e.g. MD Pathology — printed on the report |
| registration_number | string | medical council registration — printed on the report |
| signature_image_url | string, nullable | digital signature image for the report stamp |

### `Staff`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| user_id | UUID (FK → User) | |
| full_name | string | |
| staff_type | enum | `RECEPTIONIST`, `LAB_TECHNICIAN`, `ADMIN` |

## 2. Clinic Side

### `Appointment`
Created in V2 (Phase 02) because the doctor care-relationship check depends on it (ADR-015, ADR-018); extended in V3 (Phase 03, ADR-020).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| patient_id | UUID (FK → Patient) | |
| doctor_id | UUID (FK → Doctor) | |
| kind | enum | `SCHEDULED`, `WALK_IN` |
| scheduled_at | timestamp | slot start; for walk-ins, when the token was issued |
| duration_minutes | smallint | slot length at booking time |
| reason | string, nullable | |
| status | enum | `BOOKED`, `CHECKED_IN`, `IN_CONSULTATION`, `COMPLETED`, `NO_SHOW`, `CANCELLED` ([[Rules]] §1a) |
| queue_date / queue_number / queue_token | date / int / string, nullable | set together at check-in; `T-007`; unique per `queue_date` |
| booked_by_user_id | UUID (FK → User), nullable | who booked or issued the token |
| checked_in_at / started_at / completed_at / cancelled_at | timestamp, nullable | stamped by the matching status change |
| cancellation_reason | string, nullable | |
| reminder_sent_at | timestamp, nullable | reminder email sent (at most once) |
| created_at / updated_at | timestamp | |

Unique partial index `(doctor_id, scheduled_at)` where `kind = 'SCHEDULED' AND status <> 'CANCELLED'` — the database refuses double bookings.

### `DoctorSchedule` (V3)
Weekly working hours; several blocks a day allowed.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| doctor_id | UUID (FK → Doctor) | |
| day_of_week | smallint | ISO, 1 = Monday |
| start_time / end_time | time | end after start |
| slot_minutes | smallint | 5–120 |

### `AppointmentEvent` (V3)
Append-only status history (DB trigger rejects `UPDATE`/`DELETE`).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| appointment_id | UUID (FK → Appointment) | |
| from_status | enum, nullable | null for the creating event |
| to_status | enum | |
| changed_by_user_id | UUID (FK → User) | |
| note | string, nullable | e.g. cancellation reason |
| created_at | timestamp | |

### `Consultation` (V4)
One per appointment. Read-only once `COMPLETED` — a trigger rejects updates and deletes (ADR-021).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| appointment_id | UUID (FK → Appointment), unique | |
| doctor_id | UUID (FK → Doctor) | the author |
| patient_id | UUID (FK → Patient) | |
| status | enum | `DRAFT`, `COMPLETED` |
| chief_complaint | string, nullable | |
| notes | text, nullable | doctor's working notes — never shown to the patient |
| diagnosis | string, nullable | required to complete (check constraint) |
| advice | text, nullable | |
| follow_up_date | date, nullable | |
| bp_systolic / bp_diastolic / pulse_bpm / spo2_percent | smallint, nullable | range-checked |
| temperature_c / weight_kg | numeric, nullable | range-checked |
| created_at / updated_at / completed_at | timestamp | |

### `Prescription` (V4)
At most one per consultation. Numbered and read-only once issued (trigger). PDFs are generated on request, not stored.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| consultation_id | UUID (FK → Consultation), unique | |
| prescription_code | string, unique, nullable | `RX-000123`, from `prescription_code_seq` when issued |
| issued_at | timestamp, nullable | set together with the code |
| created_at | timestamp | |

### `PrescriptionItem` (V4)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| prescription_id | UUID (FK → Prescription) | |
| position | smallint | 1, 2, 3 … unique per prescription |
| medicine | string | |
| dosage | string, nullable | e.g. `500 mg` |
| frequency | string | e.g. `1-0-1`, `SOS` |
| duration | string | e.g. `5 days` |
| instructions | string, nullable | e.g. `After food` |

### `Formulary` (V4)
Reference list of common generic medicines for autocomplete; doctors can still type any medicine.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| name | string | unique with `form` |
| form | string | Tablet, Syrup, Capsule, … |
| default_strength | string, nullable | pre-fills the dose |

## 3. Lab Side

### `LabTest` (catalog, V5)
What the lab offers. Seeded with 22 common tests; maintained by admins. Retired (`is_active = false`), never deleted (ADR-022).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| code | string, unique | e.g. `CBC`, `LIPID`; fixed once created |
| name | string | e.g. "Complete Blood Count" |
| category | string | Haematology, Biochemistry, … |
| sample_type | enum | `BLOOD`, `URINE`, `STOOL`, `SWAB` |
| required_tube_type | enum | `EDTA`, `PLAIN`, `SST`, `CITRATE`, `FLUORIDE`, `HEPARIN`, `URINE_CUP`, `STOOL_CUP`, `SWAB_TUBE` — see [[Rules]] tube-type rule |
| price | decimal | ≥ 0 |
| turnaround_hours | smallint | 1–720 |
| prep_instructions | string, nullable | shown to the patient, e.g. "Fast for 10–12 hours" |
| is_active | boolean | only active tests can be ordered |
| created_at / updated_at | timestamp | |

### `LabTestParameter` (V5)
What a test reports — replaces the single reference range first planned on `LabTest`.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| lab_test_id | UUID (FK → LabTest) | |
| position | smallint | unique per test |
| name | string | e.g. "Haemoglobin" |
| unit | string, nullable | e.g. `g/dL` |
| ref_low / ref_high | decimal, nullable | normal range; empty for qualitative results (Positive/Negative) |
| critical_low / critical_high | decimal, nullable | critical limits (Phase 08) |

### `LabOrder` (V5)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| order_code | string, unique | `LO-000123`, from `lab_order_code_seq` |
| patient_id | UUID (FK → Patient) | taken from the consultation when ordered there |
| ordering_doctor_id | UUID (FK → Doctor) | |
| consultation_id | UUID (FK → Consultation), nullable | set when ordered during a consultation — see [[Appflow]] §1; at most one open order per consultation (partial unique index) |
| priority | enum | `ROUTINE`, `URGENT` |
| clinical_notes | string, nullable | the doctor's note for the lab — not shown to the patient |
| status | enum | `ORDERED`, `CANCELLED` (Phase 07 adds sample progress) |
| created_at / updated_at | timestamp | |
| cancelled_at / cancelled_by_user_id / cancellation_reason | nullable | |

### `LabOrderItem` (V5)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| lab_order_id | UUID (FK → LabOrder) | |
| lab_test_id | UUID (FK → LabTest) | a test is live at most once per order (partial unique index) |
| test_name | string | snapshot at order time |
| price_at_order | decimal | snapshot at order time — billing uses this |
| status | enum | `ORDERED`, `CANCELLED` |
| created_at / cancelled_at | timestamp | |

### `Sample`
One row per physical sample instance. A rejected sample is **not reused** — a redraw creates a new `Sample` row linked back to the same `LabOrderItem`, preserving the rejected one as a permanent record (see [[Rules]] §2).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_code | string, unique | format `LAB-YYYYMMDD-####` |
| lab_order_item_id | UUID (FK → LabOrderItem) | |
| status | enum | `ORDERED`, `COLLECTED`, `RECEIVED_AT_LAB`, `IN_TESTING`, `RESULT_ENTERED`, `VERIFIED`, `REPORT_GENERATED`, `DISPATCHED`, `REJECTED` |
| tube_type_used | string, nullable | set at collection |
| body_site | string, nullable | set at collection |
| created_at | timestamp | |

### `SampleStatusEvent`
Append-only log — this table **is** the chain of custody. Never updated or deleted, only inserted.

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample) | |
| status | enum | same values as `Sample.status` |
| actor_user_id | UUID (FK → User) | who performed this transition (technician, pathologist, etc.) |
| occurred_at | timestamp | |
| detail | text, nullable | e.g., rejection reason, retest reason, analyzer/machine ID used |

### `RejectionRecord`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample) | |
| rejected_at_stage | enum | `RECEIVED_AT_LAB`, `IN_TESTING` |
| reason | enum | `HEMOLYZED`, `CLOTTED`, `INSUFFICIENT_VOLUME` (receipt only); `SAMPLE_EXHAUSTED`, `SAMPLE_DEGRADED` (testing only); `OTHER` (either). Stage/reason pairing enforced by a check constraint |
| note | text, nullable | required when `reason = OTHER` |
| flagged_by_staff_id | UUID (FK → Staff) | |
| flagged_at | timestamp | |
| front_desk_notified_at | timestamp, nullable | |

### `TestResult`
A sample can have several results over time: each return for retest keeps the old row (status `RETURNED_FOR_RETEST`) and the retest adds a new one. At most one row per sample is `PENDING_VERIFICATION` or `VERIFIED` (enforce with a partial unique index).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample) | |
| status | enum | `PENDING_VERIFICATION`, `VERIFIED`, `RETURNED_FOR_RETEST` |
| attempt_number | int | 1 for the first result, +1 per retest |
| value | decimal / text | |
| entered_by_staff_id | UUID (FK → Staff) | lab technician |
| entered_at | timestamp | |
| is_within_reference_range | boolean | computed against `LabTest.reference_range_*` |
| verified_by_pathologist_id | UUID (FK → Pathologist), nullable | pathologist sign-off, see [[Rules]] verification gate |
| verified_at | timestamp, nullable | |
| returned_by_pathologist_id | UUID (FK → Pathologist), nullable | set when returned for retest |
| returned_at | timestamp, nullable | |
| return_reason | enum, nullable | `IMPLAUSIBLE_VALUE`, `INCONSISTENT_WITH_HISTORY`, `CRITICAL_VALUE_CONFIRMATION`, `QC_CONCERN`, `OTHER` |
| return_note | text, nullable | required when `return_reason = OTHER` |

### `Report`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| sample_id | UUID (FK → Sample) | |
| pdf_url | string | |
| generated_at | timestamp | only after `TestResult.verified_at` is set — enforced in service layer, not just UI |
| dispatched_channel | enum, nullable | `EMAIL`, `SMS`, `DOWNLOAD_LINK` |
| dispatched_at | timestamp, nullable | |
| receipt_confirmed_at | timestamp, nullable | |

## 4. Billing

### `Invoice`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| patient_id | UUID (FK → Patient) | |
| consultation_id | UUID (FK → Consultation), nullable | |
| lab_order_id | UUID (FK → LabOrder), nullable | |
| consultation_fee | decimal | |
| test_charges_total | decimal | |
| discount | decimal, default 0 | |
| discount_applied_by_staff_id | UUID (FK → Staff), nullable | required if discount > 0, per [[Rules]] |
| status | enum | `UNPAID`, `PARTIALLY_PAID`, `PAID` |
| created_at | timestamp | |

## 5. Inventory

### `InventoryItem`
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| name | string | e.g., "EDTA tube", "reagent X" |
| current_stock | integer | |
| low_stock_threshold | integer | |
| unit | string | |

### `ReagentTestMapping` (only if the reagent-mapping stretch feature is built)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| lab_test_id | UUID (FK → LabTest) | |
| inventory_item_id | UUID (FK → InventoryItem) | |
| quantity_used_per_test | integer | |

## 6. Audit

### `PatientAccessLog`
Append-only — inserted on every full-record read by a Doctor or Pathologist (see [[Rules]] §1, [[Security]] §6). Never updated or deleted: a `BEFORE UPDATE OR DELETE` trigger (`forbid_modification()`) raises an error (V2).

| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| patient_id | UUID (FK → Patient) | |
| user_id | UUID (FK → User) | who opened the record |
| resource | enum | `EMR`, `CONSULTATIONS`, `PRESCRIPTION`, `LAB_REPORT`, `LAB_HISTORY` |
| resource_id | UUID, nullable | e.g. which prescription/report |
| accessed_at | timestamp | |

### `AuditLog` (if full audit-on-edit stretch feature is built — otherwise `SampleStatusEvent` already covers the sample-specific audit trail)
| Field | Type | Notes |
|---|---|---|
| id | UUID (PK) | |
| entity_type | string | e.g., `Patient`, `TestResult`, `Report` |
| entity_id | UUID | |
| changed_by_user_id | UUID (FK → User) | |
| changed_at | timestamp | |
| change_summary | text / JSON diff | |

## 7. Relationship Summary

```
User ──1:1── Patient / Doctor / Staff (by role)

Patient ──1:N── Appointment ──1:1── Consultation ──1:N── Prescription
Consultation ──0:1── LabOrder ──1:N── LabOrderItem ──1:1── LabTest (catalog)
LabOrderItem ──1:N── Sample (N because a rejected sample spawns a new one)
Sample ──1:N── SampleStatusEvent (append-only chain of custody)
Sample ──0:1── RejectionRecord
Sample ──0:1── TestResult ──0:1── Report

Patient ──1:N── Invoice
```

This mirrors the state machine in [[Appflow]] §3 and the hard rules in [[Rules]] — `Sample.status` should never be mutated directly without a corresponding `SampleStatusEvent` row; treat the event log as the source of truth and `Sample.status` as a denormalized "current state" cache for query convenience.
