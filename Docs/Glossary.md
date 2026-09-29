# Glossary

| Term | Meaning |
|---|---|
| **TAT (Turnaround Time)** | Elapsed time for a test from sample order (or collection) to report ready, typically measured per test type |
| **NABL** | National Accreditation Board for Testing and Calibration Laboratories (India) — a real-world accreditation body whose audit-trail expectations informed this system's design; this project does not pursue actual NABL certification (see [[NonGoals]]) |
| **Phlebotomist** | Staff member trained to draw blood samples |
| **Hemolyzed sample** | A blood sample where red blood cells have ruptured, releasing hemoglobin — makes many test results unreliable and is grounds for rejection |
| **Clotted sample** | A sample that has coagulated when it shouldn't have (e.g., in an anticoagulant tube) — grounds for rejection |
| **Chain of custody** | An unbroken, timestamped record of every person who handled a sample and when — a regulatory requirement in real diagnostic labs |
| **EMR (Electronic Medical Record)** | A patient's digital history: past visits, diagnoses, known allergies |
| **RBAC (Role-Based Access Control)** | Access rules based on a user's role (Patient/Doctor/Receptionist/Lab Technician/Admin) rather than per-user grants |
| **JWT (JSON Web Token)** | A signed token used to authenticate requests after login, without server-side session state |
| **ORM (Object-Relational Mapping)** | Library that maps database rows to Java objects — here, JPA/Hibernate at runtime |
| **Next.js** | React-based JavaScript framework used for the frontend (App Router, file-based routes); here it holds no business logic or DB access (see [[Decisions]] ADR-008) |
| **REST API** | The JSON-over-HTTP interface Spring Boot exposes under `/api`; the only way the frontend reads/writes data (see [[API]]) |
| **CSRF (Cross-Site Request Forgery)** | An attack where another website makes the user's browser send a request using their login cookie; blocked here by requiring a custom header (see [[Decisions]] ADR-014) |
| **httpOnly cookie** | A cookie JavaScript cannot read; used to hold the JWT so XSS can't steal it (see [[Decisions]] ADR-009) |
| **Flyway** | Java database-migration tool; applies versioned SQL files (`V1__...sql`) to the database when the backend starts (see [[TechSpecifications]], [[Decisions]] ADR-010) |
| **Migration** | A versioned, never-edited-after-merge SQL file that changes the database schema one step forward |
| **Reference range** | The normal expected value range for a lab test result (e.g., normal blood sugar range); results are auto-checked against this |
| **Pathologist** | Separate system role: a medical specialist who reviews lab results and signs off on them before release (see [[Decisions]] ADR-011) |
| **Pathologist verification** | The required digital sign-off by a pathologist before any lab report can be released to a patient |
| **Return for retest** | A pathologist sending an entered result back to the lab to run the test again on the same sample, with a reason; the original result is kept (see [[Rules]] §2) |
| **Sample (tube)** | One physical tube or cup, covering all the tests of an order that need it; numbered `LAB-20260929-0007` and identified by the QR code on its label |
| **Collection** | Drawing the sample and recording the tube used, the body site and who did it |
| **Receipt check** | The lab's quality check when a collected sample arrives: accept it, or reject it |
| **Redraw** | A new sample taken after one was rejected; created automatically, pointing back at the rejected sample, which stays on record |
| **Tube mismatch** | A sample drawn into a different tube than its tests need; must be confirmed by the technician and is flagged throughout |
| **Chain of custody** | (see above) — for samples it is the append-only event log |
| **Attempt** | One go at entering a sample's results. A retest is a new attempt; earlier attempts stay on record |
| **Reference range / critical limit** | (see above) — the server flags each number as Normal, Low, High, Critical low or Critical high when it is entered |
| **Verification** | A pathologist's sign-off on a result; the only way to get a report. Whoever entered the result can't do it |
| **Return for retest** | The pathologist sends a doubtful result back; the same sample is tested again and the first attempt is kept |
| **Report** | The verified results as a document, with the pathologist's name, qualification and registration number; drawn as a PDF when asked for |
| **Dispatch** | The lab sending a verified report to the patient — by email or a download link — after which the patient can open it |
| **Receipt** | The moment a patient first opens their dispatched report |
| **Sample rejection** | Flagging a sample as unusable and requesting a redraw — either at receipt (hemolyzed/clotted/insufficient volume) or during testing (sample exhausted/degraded) |
| **Reagent** | A consumable chemical/substance used in running a lab test — tracked in inventory |
| **Critical result** | A verified report with a value at or beyond a critical limit. It stays pinned for the ordering doctor until they acknowledge it |
| **Acknowledge** | The ordering doctor confirming they have seen a critical result, with an optional note of what was done. Recorded once with who and when; never blocks sending the report |
| **Trend** | One test parameter across visits — needs at least two verified values; drawn with its normal range |
| **Report check (QR)** | The public page a report's QR code opens: confirms the clinic issued it, shows the report number, patient initials, tests and verifier — never results |
| **Verification code** | The random 32-character code behind a report's QR code; not related to the sample number |
| **Low stock** | An item whose level is below its low-stock threshold; a threshold of 0 means the item isn't watched. **Out of stock** is level 0 on a watched item |
| **Stock movement** | One recorded change to a level — opening, restock, used, wastage or correction — with who made it; the only way a level changes |
| **Turnaround (TAT)** | Time from collecting a sample to its report being ready; the insights show the median, the slowest 1 in 10 (90th percentile) and where the time goes |
| **Median** | The middle value: half the reports were faster, half slower. Used instead of an average because a few slow samples would distort it |
| **Token / queue number** | A number issued to walk-in patients to manage consultation order without a fixed appointment slot |
| **Care relationship** | A doctor has one with a patient if there is a non-cancelled appointment/walk-in token with that doctor, or a past consultation by them; required to open the full record (see [[Rules]] §1) |
| **Consultation** | A doctor visit event — the anchor record that appointments, prescriptions, and (often) lab orders attach to |
| **Formulary** | The clinic's list of common medicines (name, form, usual strength) used to suggest medicines while prescribing |
| **Lab order** | The tests a doctor ordered for a patient in one go, numbered `LO-000123`; ordered from a consultation (patient taken from it) or directly |
| **Test panel** | A group of tests ordered together with one tap, e.g. "Diabetes" = FBS + PPBS + HbA1c |
| **Patient preparation (prep)** | What the patient must do before the sample, e.g. fasting 10–12 hours; shown in the patient's app when the test is ordered |
| **Tube type** | The collection tube or container a test needs, recognised by cap colour — EDTA (lavender), SST (gold), Fluoride (grey)…; sterile cups for urine and stool |
| **Reference range / critical limit** | The normal low–high values for a test parameter, and the far-out values that must be flagged at once (Phase 08) |
| **Invoice** | The bill for one visit (consultation fee plus the tests ordered in it) or for a lab order placed outside a visit, numbered `INV-000123`; created by the system when the visit is finished |
| **Consultation fee** | The amount a doctor charges per visit; set per doctor and billed on the visit invoice |
| **Part payment** | Paying an invoice in more than one go; the invoice stays `PARTIALLY_PAID` until the balance is zero |
| **Front-desk discount cap** | The largest discount (a share of the bill, 20% by default) the receptionist may give without an admin |
| **Prescription number** | `RX-000123` — issued in sequence when a consultation is finished; the prescription is locked from then on |
| **Lab order** | A request for one or more tests, created either directly or automatically from a doctor's consultation notes |
