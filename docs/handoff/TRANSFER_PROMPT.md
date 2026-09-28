# CleanRoute AI Transfer Prompt

You are taking over an existing CleanRoute repository from another AI coding agent.

Before making ANY code changes, read all documentation inside:

docs/handoff/

Read these files in this order:

1. CURRENT_STATE.md
2. PROJECT_HANDOFF.md
3. AI_AGENT_GUIDE.md
4. ARCHITECTURE_DETAILED.md
5. API_REFERENCE.md
6. DATABASE_REFERENCE.md
7. FRONTEND_REFERENCE.md
8. DEVELOPMENT_RUNBOOK.md
9. PHASE_HISTORY.md

Then inspect the actual repository and verify the documentation against the source code.

## Project

CleanRoute is a pollution-aware route planning application.

The system combines route alternatives with environmental information such as AQI/pollution and produces route results based on environmental exposure and route preferences.

The repository contains a React frontend, Spring Boot backend, PostgreSQL database, authentication, environmental observation/forecast functionality, route calculation, route scoring, dashboard functionality, and notifications.

## Current implementation

Phases 1–10 have been implemented.

Do NOT assume that every planned external integration is real.

Some parts of the current implementation intentionally use deterministic/mock providers and historical/deterministic data.

The documentation is the source of truth for the exact current implementation.

## Architecture

The general architecture is:

React frontend
        ↓
Spring Boot REST API
        ↓
Backend services
        ↓
Repositories
        ↓
PostgreSQL

The backend contains functionality for:

- authentication
- users
- environmental observations
- AQI
- pollution scoring
- forecasting
- route calculation
- route alternatives
- route suitability
- dashboard
- notifications
- health/readiness
- persistence
- validation
- error handling

The frontend communicates with the backend through the documented REST APIs.

## Critical rules

DO NOT:

- invent APIs
- invent database tables
- invent database columns
- invent external providers
- claim mock data is real-world live data
- expose credentials
- expose JWT secrets
- bypass authentication
- bypass owner-scoped authorization
- move business scoring logic into the frontend without understanding the existing architecture
- break the existing provider abstraction
- accidentally implement future phases
- remove existing tests
- change migrations casually
- commit or push unless explicitly requested

Always inspect existing code before changing it.

## Development workflow

Before modifying anything:

1. Check git status.
2. Read the relevant handoff documentation.
3. Inspect the existing implementation.
4. Identify the existing abstraction/interface.
5. Determine whether a database change is required.
6. Determine whether an API contract changes.
7. Determine whether the frontend contract changes.
8. Make the smallest compatible change.
9. Add/update regression tests.
10. Run backend tests.
11. Run frontend tests.
12. Run frontend production build.
13. Run git diff --check.
14. Review git diff.
15. Report exactly what changed.

## Verification

Backend:

```bash
cd backend
mvn -q clean verify
