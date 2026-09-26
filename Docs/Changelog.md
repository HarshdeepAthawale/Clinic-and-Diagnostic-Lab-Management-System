# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/): grouped by version/date, entries tagged as **Added**, **Changed**, **Fixed**, or **Breaking**. Nothing has shipped yet — this file starts tracking once the first module is functional.

## [Unreleased]

Project is in planning/pre-implementation stage. See [[ImplementationPlan]] and [[Tracker]] for current status.

### Changed (planning)
- Frontend switched from Vaadin (Java) to Next.js (JavaScript); backend stays Java Spring Boot and now exposes a REST API. See ADR-008 and ADR-009 in [[Decisions]].
- Database migrations switched from Prisma Migrate to Flyway (ADR-010).
- Pathologist is now a separate sixth role (ADR-011).
- Pathologists can return a result for retest (ADR-012).
- Lab technicians can reject a sample during testing if it is used up or degraded (ADR-013).
- CSRF protection via a required custom header (ADR-014).
- Doctors see basic details for all patients, the full record only with a care relationship; all record opens are logged (ADR-015).
