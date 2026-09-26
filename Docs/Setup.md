# Setup

Local development setup, from a clean clone. Update this file as soon as the actual project scaffolding exists — the commands below are the expected shape given the stack in [[TechSpecifications]], not yet verified against a real repo.

## Prerequisites

- **JDK** — version TBD, likely 17+ (Spring Boot 3.x baseline). Pin an exact version once the project is scaffolded.
- **Maven** — for build/dependency management.
- **Node.js (LTS, 20+) + npm** — runs the Next.js frontend. See ADR-008 in [[Decisions]].
- **Supabase account/project** — hosts the PostgreSQL database.
- **Git**

## Environment Variables

| Variable | Purpose | Notes |
|---|---|---|
| `DATABASE_URL` | Postgres JDBC connection string (Supabase) | backend; used by Spring's datasource for both Flyway and JPA. Use Supabase's direct or session-pooler connection, not the transaction pooler (port 6543), which breaks migrations |
| `JWT_SECRET` | Signing key for auth tokens | backend; never commit a real value |
| `SPRING_PROFILES_ACTIVE` | e.g. `dev`, `prod` | |
| `SMTP_HOST` / `SMTP_USER` / `SMTP_PASS` | JavaMailSender config for email reminders/notifications | |
| `SMS_PROVIDER_API_KEY` | For SMS notifications, if implemented | provider not yet chosen — see [[OpenQuestions]] |
| `BACKEND_URL` | Where Next.js forwards `/api/*` (e.g. `http://localhost:8080`) | frontend, in `frontend/.env.local`; server-side only, not `NEXT_PUBLIC_` |

Keep backend values in a git-ignored `.env` / `application-local.yml` and frontend values in a git-ignored `frontend/.env.local`. Never commit secrets (see [[Contributing]] PR checklist).

## First-Time Setup

```bash
git clone <repo-url>
cd clinic-lab-management-system

# 1. Configure environment
cp .env.example .env   # fill in DATABASE_URL, JWT_SECRET, etc.

# 2. Run the Java backend (http://localhost:8080)
#    Flyway applies any pending migrations automatically on startup.
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # dev profile also loads seed data

# 3. In a second terminal, run the Next.js frontend (http://localhost:3000)
cd frontend
npm install
npm run dev
```

Open http://localhost:3000. API docs (Swagger UI) are at http://localhost:8080/swagger-ui.html.

## Running Tests

```bash
cd backend && mvn test               # backend unit + integration tests
cd frontend && npm test              # Vitest component tests
cd frontend && npx playwright test   # end-to-end (needs both apps running)
```

## Notes

- Schema changes = a new `V<n>__<description>.sql` file in `backend/src/main/resources/db/migration/`; restart the backend to apply it (see [[Contributing]]).
- To reset a local/dev database: `mvn flyway:clean flyway:migrate` (Flyway Maven plugin). `clean` drops everything — never run it against a shared or demo database; keep `spring.flyway.clean-disabled=true` outside local dev.
- Integration tests run migrations against a throwaway Postgres (Testcontainers), so tests always use the real schema.
- Actual commands above should be verified and corrected once `backend/pom.xml` and `frontend/package.json` exist in the repo — this doc currently describes the intended setup, not a confirmed one.
