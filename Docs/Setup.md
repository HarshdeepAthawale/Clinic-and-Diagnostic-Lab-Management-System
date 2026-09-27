# Setup

Local development setup, from a clean clone. Verified on Windows 11 with the versions below (Phase 01).

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 21 (e.g. Eclipse Temurin 21) | Spring Boot 4.1 baseline is 17; the project targets 21 |
| **Maven** | none to install | The repo ships the Maven Wrapper (`backend/mvnw`, `mvnw.cmd`), which downloads Maven 3.9.11 on first use |
| **Node.js + npm** | Node 20+ (tested on 24) | Runs the Next.js frontend (ADR-008) |
| **Docker Desktop** | any recent | Local Postgres (`docker-compose.yml`) and Testcontainers for backend tests |
| **Git** | any | |

A **Supabase** project is used for shared/deployed environments; local development uses the Docker Postgres.

## Environment Variables

| Variable | Purpose | Notes |
|---|---|---|
| `DATABASE_URL` | Postgres JDBC URL | backend. Defaults to the Docker database in the `dev` profile. For Supabase use the direct or session-pooler connection, not the transaction pooler (port 6543), which breaks migrations |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Database credentials | backend. Default to `cdlms` / `cdlms` in the `dev` profile |
| `JWT_SECRET` | Signing key for auth tokens, ≥ 32 bytes | backend. Has an insecure fallback **only** in the `dev` profile; required everywhere else. Generate with `openssl rand -base64 48` |
| `SPRING_PROFILES_ACTIVE` | `dev` locally | `dev` also loads the demo seed data |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` / `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | SMTP for appointment reminder emails | backend. The `dev` profile uses Mailpit from `docker-compose.yml` (inbox at http://localhost:8025). Without a mail host, reminders are only logged |
| `REMINDER_FROM` / `CLINIC_NAME` | Sender and clinic name on reminder emails | backend, optional |
| `SMS_PROVIDER_API_KEY` | SMS notifications, if implemented | provider not yet chosen — see [[OpenQuestions]] |
| `BACKEND_URL` | Where Next.js forwards `/api/*` | frontend, in `frontend/.env.local`; defaults to `http://localhost:8080`; server-side only, never `NEXT_PUBLIC_` |

Templates: `.env.example` (backend) and `frontend/.env.example`. Real values go in git-ignored `.env` / `frontend/.env.local`. Never commit secrets (see [[Contributing]]).

## First-Time Setup

```bash
git clone https://github.com/HarshdeepAthawale/Clinic-and-Diagnostic-Lab-Management-System.git
cd Clinic-and-Diagnostic-Lab-Management-System

# 1. Start the local database and the Mailpit mail catcher (http://localhost:8025)
docker compose up -d

# 2. Run the backend (http://localhost:8080). Flyway migrates the schema and loads demo data.
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. In a second terminal, run the frontend (http://localhost:3000)
cd frontend
npm install
npm run dev
```

Open http://localhost:3000. API docs (Swagger UI): http://localhost:8080/swagger-ui.html.

### Demo accounts (dev profile only)

One account per role is seeded by `backend/src/main/resources/db/seed/R__dev_seed.sql` — see that file for the emails and the shared demo password. In development the login page shows a chip per role that fills the form.

## Running Tests

```bash
cd backend && ./mvnw test        # unit + integration tests (needs Docker running for Testcontainers)
cd frontend && npm test          # Vitest unit/component tests
cd frontend && npm run lint      # ESLint (next/core-web-vitals)
cd frontend && npm run build     # production build check
```

Playwright end-to-end tests are added in a later phase (see [[TestPlan]]).

## Notes

- **Schema changes** = a new `V<n>__<description>.sql` in `backend/src/main/resources/db/migration/`; restart the backend to apply it (see [[Contributing]]).
- **Reset the local database:** `docker compose down -v && docker compose up -d` (deletes all local data). Never reset a shared or demo database; `spring.flyway.clean-disabled` stays `true`.
- Integration tests share one throwaway Postgres container per test run, so they always use the real migrations.
- **Windows: "Unable to establish loopback connection" on backend start.** Some Windows setups can't create the JDK's temporary Unix-domain socket. Point it at a writable folder:
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=C:\Temp"` (create `C:\Temp` first).
- ESLint is pinned to 9.x because `eslint-config-next` doesn't support ESLint 10 yet.
