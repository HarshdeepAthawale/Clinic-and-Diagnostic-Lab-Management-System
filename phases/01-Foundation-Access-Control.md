# Phase 01 — Foundation & Access Control

**Depends on:** nothing (first phase)
**Feeds into:** every later phase

## Goal

Stand up the project skeleton and the security spine everything else sits on. Nothing user-facing yet beyond empty, role-gated screens.

## Scope

- [ ] Spring Boot + Maven project scaffolding in `backend/` (REST controllers under `/api`, springdoc Swagger UI)
- [ ] Next.js (App Router, JavaScript) scaffolding in `frontend/` with Mantine, TanStack Query, ESLint/Prettier
- [ ] `next.config.js` rewrite of `/api/*` to `BACKEND_URL`; shared `lib/api.js` fetch wrapper
- [ ] Supabase Postgres project created
- [ ] Flyway added to the backend (`flyway-core` + `flyway-database-postgresql`), `ddl-auto=validate`, `dev` profile seed data — see ADR-010 in `Decisions.md`
- [ ] `V1__identity_and_roles.sql`: `User`, `Patient`, `Doctor`, `Pathologist`, `Staff` (see `Schema.md` §1) + matching JPA entities
- [ ] Testcontainers Postgres for integration tests
- [ ] Spring Security + JWT: login, role claim, protected endpoints; JWT in httpOnly cookie (ADR-009); `/auth/me`, `/auth/logout`
- [ ] CSRF: backend filter requiring `X-CSRF-Protection: 1` on all mutating `/api` requests, Spring's token CSRF disabled, CORS left closed; `lib/api.js` adds the header (ADR-014)
- [ ] Password hashing (BCrypt or equivalent)
- [ ] Login/register pages and `middleware.js` redirects in Next.js
- [ ] Role-based navigation shell in Next.js — one `layout.js` + empty dashboard per role (Patient, Doctor, Pathologist, Receptionist, Lab Technician, Admin)

## Exit Criteria

A user can register/log in through the Next.js UI, receive a JWT cookie with the correct role claim, and land on their role's (empty) dashboard. A user of one role cannot reach another role's pages, and calling another role's API endpoint directly returns 403.

## Related Docs

- `Docs/TechSpecifications.md` — stack and architecture this scaffolding follows
- `Docs/Schema.md` §1 — Identity & Roles tables
- `Docs/Security.md` — auth/RBAC requirements this phase must satisfy
- `Docs/Setup.md` — expected local dev commands
- `Docs/API.md` — Auth endpoints
