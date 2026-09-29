# CleanRoute Current State

Snapshot inspected for this handoff. Recheck Git and source before relying on stateful details.

## Current Git state

- Branch: `main`.
- HEAD: `6cb2be3e236f7cd14489e3187cb30dc9974d0dca` (`Implement Phases 7-10 route intelligence and dashboard`).
- Local `origin/main` tracking ref matched HEAD at inspection; remote was not fetched.
- Working tree is dirty from ongoing frontend journey-planner work plus this handoff package. Previously existing edits: `.env.example`, `README.md`, `docker-compose.yml`, `frontend/src/App.test.tsx`, `frontend/src/App.tsx`, `frontend/src/styles.css`, and untracked `frontend/src/location/`. Handoff docs are newly added and uncommitted.
- No commit or push was performed for this handoff.

## Current implementation

CleanRoute is a Java 21 / Spring Boot 3.5.6 modular monolith with PostgreSQL/Flyway and a React 19/TypeScript/Vite frontend. Phases 1–10 are present, and Phase 11 routing work is in progress. JWT authentication, owner-scoped accounts/data, generated environmental observations, historical forecasts, pollution scores, provider-neutral route alternatives/ranking, dashboard, and in-app notifications exist.

## What works (repository evidence and last local verification)

- `GET /api/health` includes a DB check.
- Registration/login, protected profile, preferences and user-owned resources.
- Three fixed demo cells with generated observation APIs and 15-minute scheduler.
- Pollution score and bounded forecast/current/history APIs.
- Authenticated route calculation/detail/save/history; mock geometry and backend ranking.
- Dashboard, notification list/read and owner isolation.
- React dashboard with backend-proxied human-readable place search, map, route cards and account flows.
- Last verification run for this documentation task: `mvn -q clean verify` passed (64 tests across 17 test classes, 0 failures/errors/skips); `npm test -- --run` passed (5 tests); `npm run build` passed. These are point-in-time results before final handoff markdown-only additions. Compose had been validated in prior work, not rerun after docs creation.

## Limitations

- AQI/weather/traffic observations and mock route alternatives are deterministic/generated demo data, not real readings or road directions. Optional OSRM routing can provide real road geometry, distance, and duration, but not real environmental data.
- Forecast is a weighted historical baseline, not ML or external forecast.
- Exactly three fixed Delhi demo cells; no arbitrary geographic lookup or PostGIS.
- No route-specific AQI response. Default route mock provides no green/cycling/elevation metadata.
- Photon-backed place search is public-internet-dependent and best effort; the browser calls the backend API.
- Global search results do not imply environmental data coverage; only locations near three fixed Delhi demo cells are marked supported.
- Compose frontend is Vite dev server. No production serving/release pipeline is documented as implemented.
- OSRM routing currently supplies only one primary route; multiple real alternatives are not requested or fabricated.

## Current frontend state

The committed frontend has human-readable origin/destination fields, debounced backend place suggestions, internal coordinate selection/swap, mode/preference controls, route API calls, Leaflet geometry display and backend result cards. Existing dashboard/account/AQI/forecast/notification functionality remains in `App.tsx`.

## Current backend state

Spring MVC controllers delegate into security, observation, pollution, route, geocoding, dashboard and notification services. Geocoding search is public and provider-neutral; Photon or deterministic mock implementations are selected by backend configuration. Environmental/routing/forecast/notification implementations remain mock, historical, or in-app. Protected ownership is principal-derived.

## Current database state

Flyway V1–V6 define 13 tables: app user, preferences, saved places/routes/history, geographic cells, pollution/weather/traffic observations, provider freshness, forecasts, route calculations, notifications. Full schema is in `DATABASE_REFERENCE.md`. A local running DB is environment-dependent; this audit did not claim its current runtime contents.

## Current API state

Public APIs: health, auth, AQI current/history, pollution score, forecast/current history. Authenticated APIs: user profile/preferences, places, saved routes/history, route calculation/detail, dashboard, notification list/read. Complete table and contracts are in `API_REFERENCE.md`.

## Current known TODOs / objectively incomplete items

- Real AQI, weather, traffic and road-routing provider adapters are absent.
- Geographic lookup remains fixed-cell demo only.
- Optional green-area/cycling/elevation metadata is not populated by the current route mock.
- Photon search can be unavailable externally. Global geocoder results outside the three fixed Delhi demo cells have no supported environmental coverage.
- Current handoff-time journey planner edits remain uncommitted.
- The plan has no subsequent Phase 11 item; no later phase should be inferred.

## Immediate next task

No objectively defined next implementation phase exists in the current plan. The outstanding concrete repository state is review/verification of the uncommitted frontend journey-planning edits and this uncommitted handoff documentation; do not commit without the user's instruction.
