# AI Agent Guide

You are taking over the CleanRoute repository from another AI coding agent.

The repository may contain pre-existing user changes. Never treat a dirty file as disposable.

## Before changing anything

1. Read `docs/handoff/PROJECT_HANDOFF.md`.
2. Read `docs/handoff/ARCHITECTURE_DETAILED.md`.
3. Read `docs/handoff/API_REFERENCE.md`.
4. Read `docs/handoff/DATABASE_REFERENCE.md`.
5. Read `docs/handoff/FRONTEND_REFERENCE.md`.
6. Read `docs/handoff/DEVELOPMENT_RUNBOOK.md`.
7. Inspect `git status` and preserve existing edits/untracked files.
8. Inspect recent Git history and branch/tracking state.
9. Inspect relevant source files and original `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION_PLAN.md`, and README.
10. Run relevant baseline tests before modifying behavior where practical.

`CURRENT_STATE.md` is a snapshot from this handoff and can become stale; verify it against live Git/source before relying on it.

## Rules for future AI agents

- Do not invent APIs, response fields, database columns, providers, or behavior.
- Do not bypass backend scoring/ranking by calculating or sorting routes in the frontend.
- Do not expose credentials, tokens, passwords, or secret values in source, docs, test output, or commits.
- Preserve owner scoping; use the authenticated principal, never client-supplied user IDs.
- Preserve authentication and public/protected endpoint boundaries.
- Preserve provider abstractions; do not claim deterministic mocks are real observations/routes.
- Do not implement later-phase or undocumented functionality as incidental cleanup.
- Add regression tests for behavior changed; test behavior, not class existence.
- Run relevant backend tests, frontend tests/build, and `git diff --check`.
- Inspect migrations before schema changes; append a new Flyway migration instead of editing applied history.
- Keep frontend/backend contracts synchronized and verify HTTP status/error behavior.
- Do not commit or push unless the user explicitly asks.
- Do not overwrite, reset, or remove unrelated or pre-existing user work.

## Safe feature process

1. Identify the exact requirement and phase/scope.
2. Find the existing abstraction and code path that owns it.
3. Read the API contract and actual request/response models.
4. Evaluate database impact, ownership, nullability, timestamps, constraints, and query paths.
5. Evaluate frontend states/API integration if relevant.
6. Implement the smallest compatible change.
7. Add regression coverage for success, invalid input, missing data, boundaries, and ownership as applicable.
8. Run backend tests.
9. Run frontend tests and production build when frontend is touched or contracts may be impacted.
10. Run integration/Compose/API smoke checks when practical.
11. Review full diff, secrets, `git diff --check`, status, and scope.
12. Report exact outcomes and distinguish verified behavior from limitations.

## Important architectural decisions

- Backend is a Spring Boot modular monolith; React/TypeScript communicates through REST; PostgreSQL/Flyway is persistent storage.
- Java provider interfaces exist for AQI, weather, traffic, routing, forecasts, and notifications. Current implementations are deterministic/mock or in-app.
- Geographic scope is three fixed demo cells, not arbitrary lookup/PostGIS.
- Pollution measurements are nullable. Never convert missing pollutants to zero.
- Forecast is a historical weighted baseline, not ML or a real forecast feed.
- Route alternatives are deterministic demo paths. Use returned geometry and backend ranking.
- Route requests/calculations and saved data are owner-scoped. Notification read is owner-scoped.
- Public geocoding is a separate frontend `LocationSearchProvider` concern in current working tree; it is not backend routing.
- Database migrations V1–V6 are ordered history.
- Mock/generated/provenance fields are part of the contract; preserve them end to end.

## Things not to break

- JWT principal determines ownership for protected APIs.
- Password hashes never leave backend DTOs.
- Missing/invalid required inputs return client errors, not accidental 500s.
- Known cell with valid empty history returns empty data; unknown cell is distinct.
- One provider failure must not stop unrelated ingestion.
- Observation/forecast timestamps are timezone-aware; 15-minute alignment applies where documented.
- Mock/generated records remain explicitly labeled.
- Backend response ordering is authoritative for routes.
- Existing worktree edits must survive feature implementation and final reporting.
- Current plan defines no Phase 11; do not invent later scope.
