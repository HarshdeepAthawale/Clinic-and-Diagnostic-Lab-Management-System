# Contributing

## Branch Naming

- `feature/<short-description>` — new functionality (e.g., `feature/sample-lifecycle-state-machine`)
- `fix/<short-description>` — bug fixes
- `chore/<short-description>` — tooling, config, non-feature housekeeping
- `docs/<short-description>` — documentation-only changes

## Commit Conventions

Use [Conventional Commits](https://www.conventionalcommits.org/) style:

```
<type>(<optional scope>): <short summary>

<optional body — the why, not the what>
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`.

Example:
```
feat(sample-tracking): add rejection flow with front-desk notification

Sample rejection was previously a dead-end state with no follow-up.
```

## PR Checklist

Before requesting review:

- [ ] Tests pass locally (`mvn test` in `backend/`, `npm test` and `npm run lint` in `frontend/`)
- [ ] If an endpoint was added/changed: [[API]] updated
- [ ] Linked to the relevant item in [[Tracker]]
- [ ] If the DB schema changed: a new Flyway migration (`V<n>__<description>.sql`) is included, matching JPA entities are updated, and [[Schema]] is updated to match
- [ ] If a new decision was made (library choice, architecture change): logged in [[Decisions]]
- [ ] If scope changed: [[PRD]] and/or [[NonGoals]] updated
- [ ] No secrets or `.env` values committed

## Schema Changes

Schema changes go through Flyway (see [[Setup]] and ADR-010 in [[Decisions]]) — never hand-edit the database directly, and never edit a migration that has already been merged. Add a new `V<n>__<description>.sql` in `backend/src/main/resources/db/migration/`, update the JPA entities, and update [[Schema]] in the same PR so the doc never drifts from reality. If two branches claim the same version number, the later one to merge renumbers.
