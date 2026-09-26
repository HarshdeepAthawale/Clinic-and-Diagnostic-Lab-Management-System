# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/): grouped by version/date, entries tagged as **Added**, **Changed**, **Fixed**, or **Breaking**. Nothing has shipped yet — this file starts tracking once the first module is functional.

## [Unreleased]

Phase 01 is complete except creating the shared Supabase project. See [[Tracker]] for current status.

### Added — Phase 01 (Foundation & Access Control)
- Backend: Spring Boot 4.1 / Java 21 REST API; Flyway `V1__identity_and_roles.sql` (users, patients, doctors, pathologists, staff); JWT in httpOnly cookie; `X-CSRF-Protection` header filter; register / login / logout / me; role-gated dashboard endpoints; dev-only demo seed.
- Frontend: Next.js 16 + Mantine 9 "Calm Precision" design system (tokens, light/dark, fonts); split-screen login and two-step patient registration; staff shell with Ctrl+K palette, shortcuts and collapsible sidebar; mobile-first patient shell; per-role dashboards; animated sample journey preview.
- Tests: 33 backend (Testcontainers Postgres) and 16 frontend (Vitest).

### Changed (planning)
- Frontend switched from Vaadin (Java) to Next.js (JavaScript); backend stays Java Spring Boot and now exposes a REST API. See ADR-008 and ADR-009 in [[Decisions]].
- Database migrations switched from Prisma Migrate to Flyway (ADR-010).
- Pathologist is now a separate sixth role (ADR-011).
- Pathologists can return a result for retest (ADR-012).
- Lab technicians can reject a sample during testing if it is used up or degraded (ADR-013).
- CSRF protection via a required custom header (ADR-014).
- Doctors see basic details for all patients, the full record only with a care relationship; all record opens are logged (ADR-015).
- Full design spec "Calm Precision" in Design.md (ADR-016).
