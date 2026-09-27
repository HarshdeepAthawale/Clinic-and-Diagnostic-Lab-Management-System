# Tech Specifications

## 1. Stack Overview

Java (Spring Boot) REST backend, a Next.js frontend written in JavaScript, and PostgreSQL (via Supabase) as the database.

### Backend (`backend/`)

| Layer | Technology | Why |
|---|---|---|
| **Framework** | Java 21 + Spring Boot 4.1 | Industry-standard Java framework for REST APIs; handles routing, dependency injection, security |
| **API style** | REST + JSON under `/api` | The single contract between frontend and backend — see [[API]] |
| **Database** | PostgreSQL (hosted on Supabase) | Relational — fits the domain, since patients, appointments, tests, and results are naturally connected via foreign keys. Supabase gives a managed Postgres instance with a dashboard, no local DB server setup needed |
| **DB Migrations** | Flyway | Versioned plain-SQL migration files inside the backend; applied automatically on application startup. The single source of truth for the schema — see ADR-010 in [[Decisions]] |
| **ORM (runtime)** | Spring Data JPA + Hibernate | Maps Java entities to the Flyway-created tables. Runs with `ddl-auto=validate` — Hibernate never creates or alters tables, it only fails startup if entities and schema disagree |
| **Authentication** | Spring Security + JWT | Login handling; ensures each role (Patient/Doctor/Pathologist/Receptionist/Lab Technician/Admin) can only access what it's allowed to. JWT delivered in an httpOnly cookie — see ADR-009 in [[Decisions]] |
| **Validation** | Jakarta Bean Validation (`@Valid`) | Every request DTO is validated server-side regardless of frontend checks |
| **PDF generation** | OpenHTMLtoPDF (on Apache PDFBox) + Thymeleaf XHTML templates | Prescriptions now; lab reports and invoices later. Generated on request behind the record's access checks, never stored (ADR-021) |
| **Background tasks** | Spring `@Async` / `@Scheduled` | Non-blocking work: email reminders, background report generation |
| **Notifications** | JavaMailSender (email) / Twilio or similar (SMS, optional) | Appointment reminders, "your report is ready" alerts. **SMS provider not finalized** — see [[OpenQuestions]] |
| **API docs** | springdoc-openapi (Swagger UI) | Auto-generated, always-current endpoint reference for frontend developers |
| **Build tool** | Maven | Dependency management and build |
| **Testing** | JUnit 5 + Mockito, Spring Boot Test / MockMvc | Unit tests and HTTP-level integration tests |

### Frontend (`frontend/`)

| Layer | Technology | Why |
|---|---|---|
| **Framework** | Next.js 16 (App Router) + React 19 | File-based routing, layouts per role, production build/optimization built in |
| **Language** | JavaScript (ES2022+, `.js` / `.jsx`) — **not** TypeScript | Project requirement. Use JSDoc comments where a shape needs documenting |
| **UI components** | Mantine 9 (core, form, notifications), light mode only | Ready-made tables, forms with validation, date pickers, modals and toasts — avoids bare/templated screens (see [[Design]]) |
| **Charts** | Mantine Charts (built on Recharts) | Admin dashboard revenue/TAT/test-volume charts |
| **Command palette** | `@mantine/spotlight` | Staff `Ctrl/⌘+K` search-and-act palette (see [[Design]] §3.3) |
| **Animation** | `motion` (Framer Motion) | Sample-journey progress, queue reordering, panel transitions; respects reduced motion (see [[Design]] §2.5) |
| **Icons** | `@tabler/icons-react` | One consistent 1.5px-stroke icon set |
| **Fonts** | `next/font` — Outfit, IBM Plex Mono | Self-hosted, no layout shift (see [[Design]] §2.2) |
| **Data fetching** | TanStack Query | Caching, loading/error states, refetch-after-mutation for API calls |
| **HTTP client** | Native `fetch` wrapped in one `lib/api.js` module | Single place for base URL, `credentials: 'include'`, the `X-CSRF-Protection` header on mutations (ADR-014), and error-shape handling |
| **Route protection (UX only)** | Next.js `proxy.js` (Next 16's name for middleware) | Redirects unauthenticated users to `/login` and users to their own role's area. **Not a security boundary** — the backend enforces all access |
| **Testing** | Vitest + React Testing Library; Playwright for end-to-end flows | Component tests and full browser walkthroughs |
| **Lint/format** | ESLint (`next/core-web-vitals`) + Prettier | Consistent code style |

**Rule:** Next.js is a frontend only. It must not connect to the database, use any database client/ORM, or contain business logic in Route Handlers / Server Actions. All data goes through the Spring Boot API.

## 2. Architecture

Two deployables sharing one database-backed API:

```
 Browser
   │  (pages, JS)            (/api/* requests, JWT cookie)
   ▼                                  │
┌──────────────────────────┐         │
│  Next.js app (frontend/)  │         │
│  - App Router pages,      │         │
│    one area per role      │  rewrite /api/* ──────────┐
│  - proxy.js redirects     │                             │
└──────────────────────────┘                             ▼
                                   ┌─────────────────────────────────────┐
                                   │     Spring Boot REST API (backend/)  │
                                   │  ┌──────────────┐  ┌──────────────┐ │
                                   │  │ Controllers   │─>│ Service layer│ │
                                   │  │ (/api/...)    │  │ (rules, RBAC)│ │
                                   │  └──────┬───────┘  └──────┬───────┘ │
                                   │  ┌──────▼───────┐  ┌──────▼───────┐ │
                                   │  │Spring Security│  │ Spring Data  │ │
                                   │  │  + JWT        │  │ JPA/Hibernate│ │
                                   │  └──────────────┘  └──────┬───────┘ │
                                   └────────────────────────────┼─────────┘
                                                                ▼
                                                ┌──────────────────────────┐
                                                │  PostgreSQL (Supabase)    │
                                                └──────────────────────────┘
   (Flyway runs inside the Spring Boot app at startup, applying pending
    db/migration/V*.sql files before JPA validates the schema.)
```

Key points:

- **Same-origin API via rewrites.** `next.config.js` rewrites `/api/:path*` to the Spring Boot base URL (`BACKEND_URL`). The browser only ever talks to the Next.js origin, so the JWT cookie is first-party and no CORS configuration is needed in normal operation.
- **The backend is the only security boundary.** Every endpoint checks the role and patient scope server-side (see [[Security]]). The frontend hiding a button or redirecting a route is UX, not access control.
- **Schema is owned by the backend.** Flyway migrations (SQL) define the tables; JPA entities map to them; `ddl-auto=validate` catches drift at startup. Nothing else changes the schema.

## 3. Repository Layout

```
/
├── backend/        Spring Boot (Maven) — pom.xml, src/main/java/..., src/test/java/...
│                   src/main/resources/db/migration/   Flyway schema migrations (V*.sql)
│                   src/main/resources/db/seed/        dev/demo seed data (dev profile only)
├── frontend/       Next.js (JavaScript) — package.json, app/, components/, lib/
└── Docs/, phases/  planning docs
```

### Frontend route structure (App Router)

```
app/
├── login/                    public
├── register/                 public (patient self-registration)
├── patient/                  Patient area (layout.js = patient nav)
├── doctor/                   Doctor area
├── reception/                Receptionist area
├── lab/                      Lab Technician area
├── pathology/                Pathologist area
└── admin/                    Admin area
```

Each role folder has its own `layout.js` that wraps its pages in the shared top-navbar `WorkspaceShell` with that role's links, so every role gets a purpose-built shell (see [[Design]] §1). Screens listed in [[Design]] §2 map to pages under these folders.

## 4. Why Next.js (JavaScript) instead of Vaadin

Project requirement: backend in Java, frontend in JavaScript. See ADR-008 in [[Decisions]] (supersedes ADR-004). Consequences:

- A real REST API is now **required** — [[API]] is the contract both sides build against.
- Two builds and two deployables instead of one JAR (see [[Deployment]]).
- Frontend quality is still evaluated heavily: use real components (tables, forms, charts, notifications), not templated pages.

## 5. Non-Runtime Tooling

- **Node.js + npm** — required for the frontend only. See [[Setup]].
- **Maven** — backend build, dependency management, test running.

## 6. Related Docs

- Data model: [[Schema]]
- Environment/setup: [[Setup]]
- Deployment process: [[Deployment]]
- API contracts: [[API]]
- Security model: [[Security]]
