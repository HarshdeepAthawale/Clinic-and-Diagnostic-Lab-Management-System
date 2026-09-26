# Deployment

Deployment target and process are not yet decided — see [[OpenQuestions]]. This doc captures what's known/assumed given the stack, to be filled in once a hosting decision is made.

## Environments

Not finalized. Given this is likely an academic/evaluation project rather than a production system, a single `dev`/demo environment may be sufficient — but that's an open question, not a decision. If a staging environment is added later, document it here.

## Build Process

Two independent builds (see [[TechSpecifications]] §2):

```bash
cd backend && mvn clean package            # Spring Boot fat JAR
cd frontend && npm ci && npm run build     # Next.js production build (run with `npm start`)
```

The frontend needs `BACKEND_URL` set at runtime so its `/api/*` rewrite reaches the deployed backend. Users only ever talk to the frontend origin.

**Deploy order:** backend (migrates the database on startup) → frontend, so the frontend never calls an endpoint that doesn't exist yet.

## Database

- Schema changes are applied by Flyway when the new backend version starts — no separate migration step. If a migration fails, the backend refuses to start and the previous version keeps serving (on hosts that support zero-downtime deploys).
- Seed/demo data is loaded only under the `dev`/demo profile, never in production.
- Supabase hosts Postgres regardless of where the app itself runs.

## Deployment Target — Open

Not yet chosen. Candidates to evaluate: frontend on Vercel (native Next.js host) with the backend JAR on Render/Railway/Fly.io; both on one VM/container host behind a reverse proxy; or a university/course-provided environment if this is an academic submission. Update this section once decided, and log the choice in [[Decisions]].

## Rollback

- **App rollback:** redeploy the previous backend JAR and/or frontend build. Roll back the frontend first if the backend rollback removes endpoints it uses.
- **Schema rollback:** Flyway Community has no automatic undo. Roll forward with a new corrective migration rather than attempting a destructive rollback against a live database with real data in it. Keep migrations backward-compatible (add columns before removing old ones) so rolling back the app JAR still works on the newer schema.

## Related

- [[Setup]] for local dev
- [[TechSpecifications]] for the stack this deployment plan targets
- [[Security]] for anything that must be locked down before any non-local deployment (e.g., JWT secret, DB credentials)
