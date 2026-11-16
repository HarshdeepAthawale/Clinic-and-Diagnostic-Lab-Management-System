# CDLMS: handoff for Codex

Read this first. It says what the project is, what state `main` is in, what the owner decided, how to run and verify it, and what is left.

## 1. Decision you must respect

**The frontend stays the Next.js (JavaScript) app that is already on `main`.** Do not migrate the UI to Thymeleaf, JSP, JavaFX or TypeScript on `main`, and do not describe the project as having a Java frontend in any document.

- An earlier experiment replaced the UI with Spring MVC + Thymeleaf. It is **not** wanted on `main`. It was archived, untouched, on the local branch `thymeleaf-ui` (one commit, not pushed; its own notes are in `HANDOFF_THYMELEAF.md` on that branch). Leave it alone unless the owner asks for it back.
- Reports and docs must describe what actually exists: Java 21 / Spring Boot backend, JavaScript / Next.js frontend. Never claim something is built when it is not.

## 2. What the project is

Clinic and Diagnostic Lab Management System: one web app for a clinic and its lab, from registration and consultation to sample tracking, pathologist verification, PDF report and billing. It is the UCS553 (Enterprise Web Application) mini project at Thapar Institute, guided by Dr. Chinu Dhir.

Group (cover page order): Lavish Jindal 1024031058, Yashasvi Gupta 1024031050, Harshdeep Athawale 1024031062, Girikshit Gupta 1024031056.

Repo: https://github.com/HarshdeepAthawale/Clinic-and-Diagnostic-Lab-Management-System (branch `main`).

## 3. State of `main`

All 10 planned phases are built and pushed. Status board: `Docs/Tracker.md`. What shipped: `Docs/Changelog.md`. Overview with diagrams: `README.md`.

| Area | Facts |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security (JWT in an httpOnly cookie), JPA, Flyway (V1 to V12), PostgreSQL 17. About 146 source files |
| Frontend | Next.js 16 (App Router), React 19, Mantine 9, TanStack Query, plain JavaScript. Light mode only |
| Roles | patient, doctor, pathologist, receptionist, lab technician, admin |
| Tests | 259 backend (JUnit 5, MockMvc, Testcontainers on real Postgres), 119 frontend (Vitest). All passed at last run |
| Docs | 32 ADRs in `Docs/Decisions.md`; schema, API, rules, security, design, test plan in `Docs/` |

Features: registration and record, appointments and queue, consultations and prescriptions, lab catalog and orders, sample lifecycle (collect, receive, reject and redraw, test), result entry with server-side range flags, pathologist verify or return for retest, report PDF with QR code and public check page, dispatch, billing, inventory with low-stock alerts, critical-value alerts, trends across visits, admin insights (turnaround from the sample event log), staff accounts and change password, sign-in rate limiting.

Not built (listed in `Docs/OpenQuestions.md`): forgot-password, reagent-to-test stock use, escalation of unacknowledged critical results, SMS, pathologist signature image, a Supabase project and any deployment, automated browser (Playwright) tests.

## 4. How to run it (Windows)

Needs JDK 21 (Temurin), Node 20+, Docker Desktop.

```bash
docker compose up -d                       # Postgres + Mailpit (start Docker Desktop first)

cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=<a short writable folder>"
                                           # http://localhost:8080 ; dev profile loads demo accounts

cd frontend
npm install
npm run dev                                # http://localhost:3000 ; /api is proxied to :8080
```

Checks: `cd backend && ./mvnw test` (needs Docker), `cd frontend && npm test && npm run lint`.

Demo data, through the real API as each role (about a minute, local only, resumable): `node scripts/demo-walkthrough.mjs` (`--force` to start over; state in `scripts/.demo-state.json`, delete it after resetting the DB).

Demo logins, password `Demo@12345`: `patient@`, `doctor@`, `doctor2@`, `pathologist@`, `reception@`, `lab@`, `admin@` + `demo.cdlms.dev`.

Gotchas:
- Without the `jdk.net.unixdomain.tmpdir` flag, Tomcat fails with "Unable to establish loopback connection".
- If port 8080 is busy, find the Java process with `Get-NetTCPConnection -LocalPort 8080` and stop it.
- Docker Desktop stops after a reboot; start it and wait for `docker info` before the backend or any test.
- Bash heredocs containing an apostrophe fail in this environment; write files with an editor tool instead.
- `springdoc` (Swagger) is off unless `API_DOCS_ENABLED` is set; the dev profile turns it on.

## 5. Architecture in one paragraph

Browser to Next.js, which proxies `/api` to Spring Boot. Request path: security filter chain (JWT cookie, `X-CSRF-Protection: 1` header on every change, rate limits) to controllers (role check on each endpoint) to services (business rules, ownership, atomic updates) to repositories (JPA plus JDBC queries) to PostgreSQL. The database enforces the rules that must never break: no report without a verified result (trigger), legal sample moves only (trigger), append-only history tables, stock never below zero, one-time critical acknowledgement. Diagrams: `Docs/diagrams/*.svg` (architecture, patient journey, sample lifecycle, data model, security layers, use case, class diagram).

## 6. The report

The mini project report (format given by the professor: cover, abstract, TOC, Chapter 1 Introduction, Chapter 2 System Design, Chapter 3 Implementation, Chapter 4 Results and Screenshots, References) was written for the Next.js frontend, 40 pages:

- `C:\Users\Harshdeep Athawale\Downloads\CDLMS_Mini_Project_Report.docx` and `.pdf`
- Format file: `C:\Users\Harshdeep Athawale\Downloads\Java Project Report Format.docx`
- Screenshots (34, from localhost, one per role): `C:\Users\Harshdeep Athawale\Downloads\CDLMS_Report_Screenshots\`

Also in Downloads is `CDLMS_Mini_Project_Report_Thymeleaf.docx`, produced for the abandoned Thymeleaf experiment. **Do not submit it**; it describes a UI that is not on `main`.

If the report is edited: keep the professor's section structure; every figure and listing must come from the real code or the running app; numbers must match the repo (259 and 119 tests, 12 migrations, 32 ADRs); in Word, update fields so the table of contents shows real page numbers; check pages visually after export.

## 7. Open risk for the owner to resolve

The professor said the frontend should be in Java. The submitted frontend is JavaScript. The owner has decided to keep the existing version for now, so do not "fix" this silently. If the professor insists, the working Thymeleaf version on branch `thymeleaf-ui` is the fallback (it compiled, passed all 259 backend tests, and all 21 role routes returned 200 when last checked); it would then need merging, a README and report update, and screenshots retaken.

## 8. Working agreements

- Many small, single-purpose commits pushed straight to `main`, conventional-commit subjects (`feat(scope): ...`, `fix(...)`, `docs(...)`, `test(...)`). One commit per documentation file. The owner wants a high commit count.
- Every commit carries both co-author trailers:
  `Co-authored-by: Girikshit Gupta <180283737+ggpt1008@users.noreply.github.com>`
  `Co-authored-by: lavish197 <237558339+lavish197@users.noreply.github.com>`
- No mock data anywhere; dashboards show real records only. UI: Outfit and IBM Plex Mono, warm palette, one accent `#b42318`, light mode only.
- Update the relevant docs (Changelog, Tracker, ADR, schema/API/rules/security) in the same change as the code. Keep migrations append-only: add `V13__...`, never edit an applied one.
- Run backend tests, frontend tests and lint before saying something is done, and report failures plainly.
- Do not commit local render artifacts (`.codex-docx-render/` is untracked scratch output).
