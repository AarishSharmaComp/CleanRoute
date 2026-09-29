# CleanRoute Project Handoff

This is the master orientation document for an AI or developer taking over this repository. Recheck Git state before relying on stateful details. Read `CURRENT_STATE.md` for the shortest snapshot, then use the more focused references in this directory.

## Project identity

**CleanRoute** is a pollution-aware journey-planning demo. It compares route alternatives using travel-time estimates and pollution exposure estimates derived from stored environmental observations and baseline forecasts. It exists to demonstrate an end-to-end flow from environmental data collection and scoring through route alternatives, user accounts, a dashboard, and owner-scoped saved data.

The project is a **local-development / deterministic-demo application**, not a production air-quality or navigation service. Environmental data defaults to deterministic mock observations, with an opt-in Open-Meteo current air-quality adapter. Weather and traffic remain mock providers, forecasts remain a historical baseline, and route alternatives remain provider-dependent. Place search is proxied through the backend geocoding provider abstraction.

### Technology

- Backend: Java 21, Spring Boot 3.5.6, Maven, Spring MVC, Spring Security, Bean Validation, Spring Data JPA, JDBC, Flyway.
- Database: PostgreSQL; Compose currently specifies `postgres:17-alpine`.
- Frontend: React 19, TypeScript 5.9, Vite 7, Vitest, React Leaflet / Leaflet, Recharts.
- Map display: OpenStreetMap tiles and attribution through Leaflet.
- Optional place search in the current working tree: frontend `LocationSearchProvider` interface with Photon as the default adapter; no geocoder key is used.
- Runtime: one Spring Boot modular monolith, one frontend service, and one PostgreSQL service in Compose.

## Current implementation status

### Implemented and present

- Health endpoint checks the database connection.
- Registration, login, BCrypt password hashing, stateless JWT authentication, user profiles/preferences, and owner-scoped saved places/routes/history.
- Three fixed geographic cells; provider-neutral environmental/pollution, weather, and traffic observation models; deterministic mock providers; optional Open-Meteo pollution observations; scheduled ingestion, validation, timeouts, failure isolation, and provider freshness storage.
- Current and historical AQI APIs with generated/provider labels, units, time-range limits, and stale status on current data.
- Comparative pollution scoring from observations, with pollutant normalization, missing-data reporting, AQI fallback, traffic/weather context, provenance, and caveats.
- A replaceable forecast interface and deterministic historical-average baseline; predicted records are persisted and explicitly labeled.
- A provider-neutral routing interface and deterministic mock routes; route exposure is calculated from forecasted passage-time samples against the fixed demo cells.
- FASTEST, CLEANEST, BALANCED, JOGGER, and CYCLIST backend ranking/suitability. Optional route metadata is used only when supplied.
- Authenticated dashboard aggregation, route calculation persistence/history, saved routes/places, and deduplicated in-app notifications.
- React dashboard, Leaflet map, current AQI and observed/forecast chart, route cards, account widgets, loading/error/empty displays.
- Current dirty frontend work replaces coordinate fields with a debounced place-search journey planner and adds `LocationSearchProvider`/Photon. See the working-tree note below.

### Deterministic/mock behavior

- Mock AQI, weather, and traffic providers generate values from timestamps and fixed cell IDs. Mock pollutant values may be null. Their values are labeled generated and must not be described as real observations.
- The historical forecast provider is a time-slot/day-of-week historical average with exponential recency weighting and fallbacks for sparse history. It is not a real weather/air-quality forecast or ML model.
- The `MockRoutingProvider` returns a direct path and two deterministic detours for alternatives. Geometry, distance, duration, and provider are demo outputs; these are not road-network navigation directions. `OSRMRoutingProvider` is an opt-in provider that returns one normalized OSRM road route without fabricating alternatives.
- Route exposure is an estimated comparative model. Mock mode uses historical forecasts and nearest fixed demo-cell selection; real Open-Meteo mode uses bounded coordinate/hour samples along the actual route. It is not validated health guidance.
- The backend Photon adapter calls the public service over the internet. Search results are external geocoder data; route geometry still comes from the mock routing provider.

### Incomplete, unavailable, or deferred

- Open-Meteo is the only real environmental integration. There are no real weather or traffic integrations. OSRM is an optional road-routing integration; none of these integrations provide global CleanRoute environmental coverage.
- Phase 13 connects real route geometry to bounded coordinate/time Open-Meteo samples. Exposure remains a CleanRoute model over model-grid concentrations, not direct sensor readings along roads.
- Geographic lookup is limited to three fixed deterministic demo cells. There is no arbitrary coordinate-to-area lookup service or PostGIS.
- Mock route paths do not provide green-area, cycling compatibility, or elevation metadata. JOGGER/CYCLIST scoring reports unavailable metadata and excludes it when absent. The RoutePath model can carry these optional values for a future provider.
- Per-route AQI is not a response field. Route cards must not assign the current cell AQI to a route.
- Photon is a public, best-effort geocoder. It needs internet access and may be rate-limited or unavailable. No backend geocoding endpoint exists.
- No advanced forecasting, ML, notifications delivery outside the in-app database provider, OSRM multi-route optimization, email/push, or later-phase implementation is present.
- Auth has validation and JWT expiry but no refresh-token or revocation endpoint. Rate limiting is not implemented.

## Current Git and working-tree facts

- Branch: `main`.
- `HEAD`: `6cb2be3e236f7cd14489e3187cb30dc9974d0dca` (`Implement Phases 7-10 route intelligence and dashboard`).
- The local `origin/main` tracking ref was at the same commit when inspected. No remote fetch was performed during this handoff audit, so this is a statement about local refs, not a live GitHub check.
- Phase 1–10 history is preserved in six commits; Phase 7–10 were grouped in one commit. See `PHASE_HISTORY.md`.
- Git state changes over time; inspect `git status` before making changes. The current place-search frontend is committed.
- This handoff package is also uncommitted documentation work. No commit or push was performed.

## Complete phase history

The implementation plan defines Phases 1–10. It defines no Phase 11. The exact commit sequence is:

| Phase | Objective and implementation in current source | Main code / APIs / migration | Tests and limitations | Commit |
|---|---|---|---|---|
| 1 — Foundation | Spring Boot/Maven backend, React/Vite frontend, PostgreSQL Compose setup, health response. | `CleanRouteApplication`, `HealthController`, Compose and Dockerfiles; `GET /api/health`; no schema migration. | `HealthControllerTest`; health later hardened to check DB. Initial foundation. | `c7c5c0b` |
| 2 — User domain/auth | User, preference, saved-place, saved-route, history entities; register/login; BCrypt/JWT; authenticated profile and CRUD APIs. | `api`, `domain`, `security`, `repository`; `/api/auth/*`, `/api/users/me`, `/api/places`, `/api/routes/save`, `/saved`, `/history`; V1. | `Phase2ApiTest`; hashes are not returned by DTOs; user IDs derive from JWT principal. | `618e823` |
| 3 — Observations/providers | Provider-neutral AQI/weather/traffic/routing models and interfaces, fixed demo cells, deterministic mocks, seven-day-plus seed attempt and scheduled 15-minute ingestion, normalization, timeout/failure/backoff/freshness behavior. | `observation`; `/api/aqi/current`, `/api/aqi/history`; V2 and V3. | Provider, persistence, scheduler, timeout, failure-isolation, and routing tests. Exactly three fixed cells; no arbitrary geography or real provider integration. | `e9b25b5` |
| 4 — Pollution model/scoring | Pollutant normalization, missing-field handling, AQI fallback, interval/trip/context comparative score. | `pollution/PollutionEngine`, `PollutionScoreService`, `PollutionScoreController`; `GET /api/pollution/score`; no new migration. | Engine/service/API tests. Scores are a configurable comparative proxy, not health guidance or route ranking. | `364d87a` |
| 5 — Baseline forecasting | Replaceable `ForecastProvider`; historical weighted averages, quality, prediction persistence, observed/predicted distinction and history retrieval. | `HistoricalAverageForecastProvider`, `PollutionForecastService`; `/api/pollution/forecast` and `/history`; pollution_forecast added in V4. | Forecast provider/API tests; deterministic historical baseline, not ML/real-time external forecast. | `eb674be` |
| 6 — Routing and preference ranking | Provider-neutral route alternatives; estimated passage-time sampling and pollution exposure; FASTEST/CLEANEST/BALANCED rankings; owner-scoped calculation persistence/read. | `RoutingProvider`, `MockRoutingProvider`, `RouteService`, `RoutePlanningController`; `/api/routes/calculate`, `/api/routes/{id}`; route_calculation in V4 and route_history writes. | API/ranking tests. Three generated alternatives; fixed-cell forecasts, no street graph. | `eb674be` |
| 7 — Jogger/cyclist suitability | JOGGER/CYCLIST suitability with configurable exposure/traffic and jogger distance; optional green, cycling, elevation metadata. | `RouteService`, `RouteSuitabilityProperties`; adds preference constraint through V5. | Suitability/ranking/config tests. Mock does not provide optional green/cycling/elevation values. CYCLIST component weights are fixed in code; JOGGER pollution/traffic/distance weights configurable. | `6cb2be3` |
| 8 — Dashboard/visualizations | Responsive React dashboard, AQI/forecast panels, Leaflet map, route alternatives, account entry points, loading/error/empty states. | `frontend/src/App.tsx`, styles, Vite/Leaflet; frontend calls existing APIs. | Frontend integration tests and build. Public Photon search is external/best-effort; routing providers are documented under Phase 11. | `6cb2be3` |
| 9 — Saved data/dashboard/notifications | Owner-scoped dashboard aggregation and route history; deduplicated in-app alert creation/read state. | `DashboardService`, `NotificationService`, providers/repository; `/api/dashboard`, `/api/notifications`; V6. | Notification/API tests. Alerts are evaluated during dashboard reads and route calculation, stored in PostgreSQL; no email/push. | `6cb2be3` |
| 10 — Hardening/handoff | DB-aware health, CORS, validation/error handling/logging/configuration and docs/tests. | `HealthController`, `SecurityConfig`, `ApiExceptionHandler`, docs; no migration. | Health, API, and prior regression suites. Project docs exist; this package adds detailed handoff docs. | `6cb2be3` |

Detailed chronology: `PHASE_HISTORY.md`.

## System architecture

```text
User
  ↓
React / TypeScript / Vite (browser)
  ↓ HTTP JSON + optional Bearer JWT
Spring Boot modular monolith
  ├─ REST controllers / validation / exception mapping
  ├─ auth/security, observation, pollution, routing, dashboard, notification services
  ├─ provider interfaces and deterministic implementations
  └─ JPA repositories + JDBC repositories
  ↓
PostgreSQL (Flyway schema and persisted domain data)
```

The frontend receives generated outputs from backend services; it does not implement route scoring or change the backend's ranking. The optional current frontend geocoder is a separate frontend adapter: `LocationSearchProvider → Photon`, returning human-readable result context and coordinates. It is not a routing provider.

### Responsibilities by area

- **Authentication**: Spring Security filter chain, `JwtAuthFilter`, `JwtService`; public auth routes mint a signed token; controllers use its UUID principal for all owner-scoped reads/writes.
- **Observations**: `ObservationIngestionScheduler` calls `EnvironmentalDataProvider`, `WeatherProvider`, and `TrafficProvider` per fixed cell, normalizes provider responses, then writes with JDBC repositories. `ProviderFreshnessRepository` stores attempt/success/failure state. `AQIProvider` remains a deprecated compatibility subtype for existing callers/tests.
- **Pollution scoring**: `PollutionEngine` normalizes available measurements; `PollutionScoreService` combines pollutant burden and provided trip context with available traffic/weather. It scores one cell/time and does not rank routes.
- **Forecasting**: `PollutionForecastService` bounds requests and asks a `ForecastProvider`; the historical average provider aggregates to one cell/time sample, predicts per pollutant where data exist, and writes `pollution_forecast`.
- **Route calculation**: `RouteService` calls the configured `RoutingProvider`, evaluates each path with forecast samples and traffic context, applies the requested rank/suitability mode, persists JSON result and route history in one transaction.
- **Dashboard**: `DashboardService` combines Central demo-cell latest/history/forecast values with owner-scoped places, saved routes, history and notifications.
- **Notifications**: `NotificationService` evaluates high-forecast and cleaner-alternative rules; `NotificationProvider` currently binds to a JDBC-backed PostgreSQL provider. Database uniqueness prevents duplicate user/event keys.
- **Health**: `/api/health` runs `SELECT 1`; returns 200 `UP` when DB responds and 503 `DOWN` otherwise.

Detailed request and component diagrams are in `ARCHITECTURE_DETAILED.md`.

## Backend architecture

Base package `com.cleanroute`:

- `api/`: auth, profile/preferences, saved places/routes, route history, health, global exception responses.
- `security/`: JWT parsing/creation/filter and stateless Security/CORS configuration.
- `domain/`: JPA entities/enums for users, preferences, saved data, and route history.
- `repository/`: Spring Data repositories for Phase 2 user-owned entities.
- `observation/`: `api`, `config`, `domain`, `provider`, `repository`, `service`; environmental API/model/provider/scheduler/normalizer/timeout code.
- `pollution/`: `api`, `config`, `domain`, `provider`, `repository`, `service`; score and forecast APIs, models, config, provider abstraction and persistence.
- `route/`: `api`, `config`, `domain`, `repository`, `service`; route calculate/retrieve endpoints, response/request models, suitability settings, route payload storage and ranking pipeline.
- `dashboard/`: dashboard response models, aggregation service and controller.
- `notification/`: API/config/domain/provider/repository/service for alerts.

Controllers depend on services/repositories appropriate to each feature. Provider-facing logic depends on interfaces. JDBC is deliberately used for observations, forecasts, route calculation payloads and notifications; Spring Data JPA handles the user-domain entities. Hibernate schema mode is `validate`; Flyway owns schema creation/upgrades.

## Frontend architecture

- `frontend/src/main.tsx`: React root, global CSS and `App`.
- `frontend/src/App.tsx`: current single-page app state and API orchestration for auth, environmental panels, route planning, saved data, dashboard and notifications.
- `frontend/src/location/locationSearch.ts`: **uncommitted in current tree** provider interface and Photon adapter; maps GeoJSON feature results into `{name, displayName, context, latitude, longitude}`.
- `frontend/src/location/LocationSearchField.tsx`: **uncommitted in current tree** controlled, 400 ms debounced location input, 3-character minimum, aborts stale requests and renders suggestion/loading/error/empty/selected states.
- `frontend/src/styles.css`: current green CleanRoute styling, responsive grids, controls, route cards, mobile layout.
- `frontend/src/App.test.tsx`: Vitest + Testing Library tests for dashboard/API state, location selection/swap/request payload, and Photon response normalization.
- `frontend/src/test-setup.ts`, `vitest.config.ts`, `vite.config.ts`, and `vite-env.d.ts`: test setup, DOM environment, build chunks, environment typings.

The app calls the backend base URL from `VITE_API_BASE_URL` (default `http://localhost:8080`) with `fetch`; authenticated calls attach `Authorization: Bearer <token>`. The JWT token is held in `localStorage` under `cleanroute.token`. The journey planner stores selected place coordinates and does not expose coordinate inputs in the main planner. Place search and environmental panels are public; the backend requires authentication to calculate/save/retrieve routes.

## Database architecture

PostgreSQL migrations currently create 13 tables:

`app_user`, `user_preference`, `saved_place`, `saved_route`, `route_history`, `geographic_cell`, `pollution_observation`, `weather_observation`, `traffic_observation`, `provider_freshness`, `pollution_forecast`, `route_calculation`, and `user_notification` (5 from V1, 4 from V2/V3, 2 from V4, 1 from V6).

Relationships:

- `user_preference.user_id` is the owner PK and FK to `app_user`; deleting a user cascades.
- Saved place, saved route, route history, route calculation and user notification rows reference `app_user` with `ON DELETE CASCADE`.
- Pollution, weather, traffic, freshness and forecast rows reference `geographic_cell`.
- Observation unique constraints are `(provider, cell_id, observed_at)`; forecasts are keyed by `(cell_id, target_at, model_version)`; notifications use `(user_id, dedup_key)`.
- Timestamps are SQL `TIMESTAMP WITH TIME ZONE`; Java uses `Instant`.
- Coordinates use latitude/longitude numeric columns with range checks; no spatial extension is used.

Migration order: V1 user domain; V2 observation domain and geographic cells; V3 provider freshness and saved-route coordinate checks; V4 forecasts and route calculations; V5 preference constraint extension for JOGGER/CYCLIST; V6 in-app notification table. Details and columns are in `DATABASE_REFERENCE.md`.

The scheduler's `ApplicationReadyEvent` seeding writes deterministic generated observations for the three fixed Delhi cells across a 7-day lookback at 15-minute intervals, including the current interval (673 timestamps per cell/provider when all calls succeed). Database uniqueness makes repeated same-provider/cell/time inserts idempotent by ignored duplicate key. This is generated data, not measured real-world air quality/weather/traffic.

## Authentication and security

- `POST /api/auth/register` validates email/display name/password, normalizes email to lowercase, BCrypt-hashes the password, inserts a default preference, and returns a Bearer JWT and non-sensitive user view. Registration returns 201. Duplicate email returns 409.
- `POST /api/auth/login` verifies BCrypt and returns token + user view; invalid email/password returns 401.
- `JwtService` signs claims with the required environment-backed HMAC secret. It rejects startup if the configured UTF-8 secret is fewer than 32 bytes. Default expiry is 86,400,000 ms (24 hours); `JWT_EXPIRATION_MS` can override.
- `JwtAuthFilter` reads `Authorization: Bearer ...`, validates signature and subject UUID; malformed/expired tokens clear the security context. Missing/invalid credentials on protected endpoints receive 401.
- Sessions are stateless; CSRF is disabled for these APIs. CORS is restricted to `CLEANROUTE_ALLOWED_ORIGINS`; credentials are not enabled.
- Public: `/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/aqi/**`, `/api/pollution/score`, `/api/pollution/forecast/**`, `/error`.
- All other app endpoints require authentication, including profile, places, saved routes, route calculate/read/history, dashboard, and notifications.
- Ownership is derived from the authenticated principal; repositories filter by user ID for route, place, history, dashboard and notification operations. No request DTO accepts a caller-specified owner ID.
- API DTOs do not expose password hashes. Never paste the actual `.env`, JWT, password, local token or provider credential into source/docs/logs.
- `.env` and `.env.*` are ignored; `.env.example` contains placeholders. `JWT_SECRET` is mandatory in Compose and must be a strong local-only value of at least 32 bytes. Do not reuse the sample value outside a disposable local setup.
- No account lockout/rate limiter, refresh-token endpoint, token revocation list, or separate authorization roles are implemented.

## Route calculation pipeline

1. `POST /api/routes/calculate` is protected. Spring Security authenticates the JWT; the controller supplies the principal UUID to `RouteService`.
2. Service validation requires origin/destination coordinates, mode and preference; coordinates must be finite and within latitude/longitude range. JOGGER requires `JOG`; CYCLIST requires `CYCLE`. Optional departure time must be future; if absent, the service chooses a future 15-minute boundary.
3. It reads the current fixed cells and calls `RoutingProvider.alternatives(RoutingRequest)`.
4. Provider output is validated for a usable path, 2–500 valid geometry points, positive finite distance, positive duration, and valid optional green/elevation metadata.
5. Every path leg is measured with haversine geometry length. Its midpoint is mapped to the nearest one of the three demo cells. An estimated passage fraction determines a 15-minute-aligned forecast target. Forecasts are cached within the calculation by cell+target.
6. `PollutionEngine` computes pollutant burden from forecast measurement values; path exposure is geometry-distance-weighted across legs. Forecast quality is the minimum across sampled legs. Traffic at each sample cell/time contributes when available. Route suitability does not use weather observations.
7. FASTEST ranks estimated duration; CLEANEST ranks pollution cleanliness; BALANCED combines normalized duration efficiency and cleanliness equally. JOGGER/CYCLIST use their implemented suitability formulas and available metadata; missing optional fields are excluded/explained.
8. Response includes rank, provider, generated flag, geometry, distance, duration, exposure, forecast quality, preference score, components, reasons, calculation metadata, and mock limitations. Backend rank order is the response order.
9. In one `@Transactional` service operation, result JSON and compact `route_history` row are inserted for the authenticated owner. The controller then checks whether a materially cleaner alternative should create an alert.

This is a deterministic mock route pipeline; it is not real road routing or a production clean-route recommendation system.

## Pollution/AQI pipeline

- The scheduler asks `AQIProvider`, `WeatherProvider`, and `TrafficProvider` for timestamped outputs, runs normalization, and persists each observation. Provider/cell/time identity must match; invalid/future/NaN/infinite/range-invalid data are rejected. Missing values stay null.
- Pollution observation contains AQI, PM2.5, PM10, NO2, SO2, CO, O3, provider, generated flag, observed and ingest timestamps. CleanRoute units are µg/m³ for PM2.5, PM10, NO2, SO2, and O3, mg/m³ for CO, and provider-native AQI index. Open-Meteo supplies the six pollutant measurements, converted to these units, but no compatible AQI is stored.
- Current AQI chooses the latest row for a cell and sets `stale=true` after 30 minutes. History requires 15-minute interval and a known cell; a known empty interval returns an empty array.
- `PollutionEngine` normalizes each available pollutant against configurable reference values into a 0–100 proxy; it averages present measurements only. If there are no individual measurements but provider AQI is present, AQI fallback is used. No value is changed to zero to fill missing measurements.
- `/api/pollution/score` accepts one stored aligned observation interval and user-supplied duration/distance/mode context. It applies mode, duration and distance factors, and available traffic/weather inputs with configured weights. Higher means greater modeled comparative burden; it is not route ranking or validated health guidance.
- Route exposure is a different calculation in `RouteService`: forecasted pollutant burden at sampled legs is distance-weighted. It does not copy current AQI or pollution score into the route response as AQI.

## Forecast system

`ForecastProvider` allows another implementation to be injected. The current `HistoricalAverageForecastProvider`:

- accepts only future targets aligned to 15-minute boundaries;
- filters same-cell historical rows at/before target and removes rows with no usable measurement;
- aggregates observations sharing a cell/time slot before pattern selection, so multiple providers in one interval do not make that interval multiple samples;
- prefers matching UTC weekday/time slot; falls back to same time-of-day or all available history when sparse;
- computes pollutant-wise exponential recency-weighted means (60-day decay), leaves missing fields null, reports sample count and sample/recency-based LOW/MEDIUM/HIGH quality;
- persists prediction data separately from observations, with model version/provider and `sourceGenerated` provenance.

`GET /api/pollution/forecast` generates and persists a bounded series (15-minute only; maximum 96 steps and 30 days ahead); `GET /api/pollution/forecast/history` retrieves stored predictions in a range of at most 90 days. API envelopes set `dataType=PREDICTED`, `observed=false`, `predicted=true`. This is a deterministic historical baseline, not an external forecast feed, physical dispersion simulation or ML model.

## Routing provider system

- `RoutingProvider` provides `providerId()`, `route(request)` and default `alternatives(request)`; route ranking belongs to application service logic.
- `MockRoutingProvider` is the current Spring component. Its single-route method is a straight line; its alternatives method returns direct, north-detour and south-detour geometry deterministically.
- `RoutePath` includes geometry, distance, estimated duration, provider, generated flag, alternative ID, and optional green-area/cycling/elevation fields.
- Distances are computed in the mock from haversine geometry; durations are estimated with mode-specific assumed speeds and detour factors. They are not external map service results.
- No external routing provider is configured or integrated. Keep future adapter code behind `RoutingProvider` and never label mock paths as Google directions.

## Frontend journey planning

The CleanRoute-styled planner:

1. From/To search inputs accept place names; a 400 ms debounce calls the public backend `/api/places/search` endpoint after three characters, displays primary/context names, aborts outdated requests, and requires selection before coordinates are valid.
2. Swap exchanges full selected-location state. Coordinates are used internally and sent to `/api/routes/calculate` only after selection.
3. Planner offers Driving/Walking/Cycling mapped to `CAR`/`WALK`/`CYCLE` and FASTEST/CLEANEST/BALANCED.
4. Route calculation still requires JWT; guests can inspect/search locations but cannot calculate or save. Saved places/routes, dashboard, preferences, notification widgets continue to use existing contracts.
5. Map uses OSM tiles; origin/destination markers, selected route emphasis, alternatives, and fixed AQI cell markers are rendered. Only geometry returned by backend is used; missing/invalid geometry triggers a fallback message.
6. Cards display backend rank order, distance, duration, modeled exposure, preference score/components, provider/generated provenance, reasons, and data-unavailable labels for absent values. Per-route AQI remains unavailable.

The geocoding provider is separate from `RoutingProvider`. The backend selects Photon (default) or the deterministic mock; the frontend knows only the normalized CleanRoute API DTO. Search needs internet when Photon is selected. Global search does not imply global environmental coverage; route exposure is shown unavailable when either endpoint is outside the 35 km coverage radius around the three fixed Delhi demo cells.

## Dashboard and notifications

`GET /api/dashboard` is authenticated and uses the principal UUID. It returns current plus up to 96 recent observation points and up to 8 generated forecast points for `demo-delhi-central`, then the owner's saved places, saved routes, entire route history, up to configured notification result limit (default 50), and unread count. The frontend renders this alongside route planner state and account widgets.

Notification rules currently implemented:

- **HIGH_POLLUTION_FORECAST**: when dashboard generation evaluates each upcoming forecast, compare AQI and/or PM2.5 against configured thresholds modified by user sensitivity; if either is over threshold and notifications are enabled, insert a notification.
- **CLEANER_ALTERNATIVE**: after route calculation, compare the top-ranked route exposure with the minimum alternative exposure; insert when improvement reaches configured threshold and notifications are enabled.
- `NotificationProvider` is an interface; current `InAppNotificationProvider` stores in PostgreSQL. A unique `(user_id, dedup_key)` constraint plus duplicate-key handling deduplicates repeated requests.
- `/api/notifications` lists up to 100 items for the authenticated owner. `POST /api/notifications/{id}/read` marks any owned notification read. Non-owned/missing IDs return 404; ownership predicate is in SQL.
- No push/email delivery exists. Forecast alerts are evaluated on dashboard reads, not by an independent notification scheduler.

## Health and errors

`GET /api/health` runs a JDBC `SELECT 1`. It returns an ISO instant with `status=UP`, `database=UP`, `service=cleanroute-backend` and HTTP 200, or analogous DOWN values and HTTP 503 on `DataAccessException`. The Docker backend healthcheck uses this endpoint. It does not report each provider's freshness or frontend status.

`ApiExceptionHandler` maps Bean Validation and malformed request data to 400, `ResponseStatusException` to the requested status and a JSON error/message, data-integrity conflict to 409, and unexpected exceptions to a generic 500 message. Authentication entry point returns 401 JSON. Do not expose stack traces or credentials in public responses/logging.

## Configuration

All examples below are placeholders/defaults read from tracked config. Never document local `.env` values.

| Variable | Purpose | Required | Example/placeholder | Where used |
|---|---|---:|---|---|
| `POSTGRES_DB` | Compose database name | Compose requires non-empty | `cleanroute` | postgres service, backend JDBC URL |
| `POSTGRES_USER` | Compose database user | Compose requires non-empty | `cleanroute` | postgres/backend |
| `POSTGRES_PASSWORD` | Local DB password | Yes for Compose | `choose-a-local-password` | Compose DB/backend; not tracked |
| `POSTGRES_PORT` | Host-side PostgreSQL port | No | `5432` | Compose published port |
| `BACKEND_PORT` | Host-side HTTP backend port | No | `8080` | Compose published port |
| `FRONTEND_PORT` | Host-side Vite port | No | `5173` | Compose published port |
| `JWT_SECRET` | HMAC JWT signing key, UTF-8 >=32 bytes | Yes; backend fails startup otherwise | `<random-development-secret-at-least-32-bytes>` | Compose and `JwtService`; use a private local value |
| `JWT_EXPIRATION_MS` | JWT lifetime | No | `86400000` | `application.yml` / `JwtService` |
| `SPRING_DATASOURCE_URL` | JDBC URL for direct backend run | No in Compose; required if custom | `jdbc:postgresql://localhost:5432/cleanroute` | Spring datasource |
| `SPRING_DATASOURCE_USERNAME` | Direct datasource username | No in Compose; required if custom | `cleanroute` | Spring datasource |
| `SPRING_DATASOURCE_PASSWORD` | Direct datasource password | No in Compose; required if custom | `<local-password>` | Spring datasource |
| `SERVER_PORT` | Backend listen port | No | `8080` | Spring server |
| `CLEANROUTE_ALLOWED_ORIGINS` | Comma-separated allowed browser origins | No | `http://localhost:5173,http://127.0.0.1:5173` | CORS config |
| `OBSERVATION_INGESTION_INTERVAL_MS` | Scheduler fixed delay (minimum 60000) | No | `900000` | Observation config/scheduler |
| `OBSERVATION_INGESTION_INITIAL_DELAY_MS` | First scheduled poll delay | No | `900000` | Observation scheduler |
| `OBSERVATION_PROVIDER_TIMEOUT_MS` | Provider call wait limit (100–60000) | No | `5000` | Provider call executor |
| `OBSERVATION_RATE_LIMIT_BACKOFF_MS` | Max in-memory rate-limit backoff (1000–300000) | No | `60000` | Ingestion scheduler |
| `AQI_API_KEY`, `WEATHER_API_KEY`, `TRAFFIC_API_KEY` | Reserved credentials for future providers | No; unused by mocks | empty placeholder | Bound config only; no real integrations |
| `VITE_API_BASE_URL` | Browser-to-backend origin | No | `http://localhost:8080` | Frontend API helper; Compose frontend env |
| `GEOCODING_PROVIDER` | Backend geocoder selection | No | `photon` (`mock` also supported) | `GeocodingService` |
| `PHOTON_API_URL` | Photon endpoint | No | `https://photon.komoot.io/api/` | `PhotonGeocodingProvider` |
| `GEOCODING_CONNECT_TIMEOUT_MS` / `GEOCODING_READ_TIMEOUT_MS` | Connection/request timeout | No | `2000` / `5000` | Backend Photon HTTP client |
| `GEOCODING_COVERAGE_RADIUS_METERS` | Approximate radius around fixed demo cells | No | `35000` | `supportedArea` response marker |

Additional scoring/suitability/notification thresholds are in `backend/src/main/resources/application.yml`, not environment variables by default. Refer to that file before documenting new tunables.

## How to run

### Prerequisites

Docker Desktop/Engine with Compose v2; or Java 21, Maven 3.9+, Node.js 22+, npm and a PostgreSQL instance. The backend requires a JWT secret of at least 32 UTF-8 bytes even when run directly.

### Docker Compose (recommended)

From repository root:

```sh
cp .env.example .env
# Edit .env locally: set POSTGRES_PASSWORD and replace JWT_SECRET with a random >=32-byte development secret.
docker compose config --quiet
docker compose build
docker compose up
```

In another terminal: `docker compose ps`; open `http://localhost:5173`; check `http://localhost:8080/api/health`. Stop with Ctrl+C, or `docker compose down`. `docker compose down -v` removes the local database volume and all stored local data.

### Backend direct

Start PostgreSQL first (for example, with `docker compose up -d postgres` after valid `.env`). From `backend/`, export the local datasource values and `JWT_SECRET`, then run:

```sh
mvn spring-boot:run
```

For explicit env assignments use the names and placeholders in the configuration table; do not paste an actual password/key into shared logs or docs. Run backend checks with:

```sh
mvn -q clean verify
```

### Frontend direct

From `frontend/`:

```sh
npm ci
npm run dev
npm test -- --run
npm run build
```

Open `http://localhost:5173`; backend must be reachable at `VITE_API_BASE_URL` and CORS must include that origin. Geocoder settings are configured on the backend, not in Vite.

## Ports and networking

| Service | Container/default listen | Host publish default | Network details |
|---|---:|---:|---|
| PostgreSQL | 5432 | `${POSTGRES_PORT:-5432}` | Backend connects by Compose service hostname `postgres:5432`; local host port can differ. |
| Spring Boot backend | 8080 | `${BACKEND_PORT:-8080}` | Compose waits for PostgreSQL health. |
| React/Vite frontend | 5173 | `${FRONTEND_PORT:-5173}` | Compose waits for backend health. Browser talks to host-published API URL. |

## API overview

The complete contract, validation, examples and ownership behavior are in `API_REFERENCE.md`. There are no weather or traffic read APIs, no route-provider API credentials endpoint, and no arbitrary-cell search endpoint.

## Example workflows

1. Register with `POST /api/auth/register`, or login with `POST /api/auth/login`; keep returned token private.
2. Send `Authorization: Bearer <token>` to owner-scoped APIs.
3. Read `/api/dashboard`, `/api/users/me`, or `/api/places` for current user's data.
4. Read public `GET /api/aqi/current?cell=demo-delhi-central` or bounded `/api/aqi/history` for environmental observations.
5. Request public `/api/pollution/forecast` with a future 15-minute-aligned `from` timestamp; read persisted forecast history if needed.
6. Use `POST /api/routes/calculate` with authenticated selected coordinates/mode/preference; result includes configured-provider routes and is saved under that user. Mock returns three deterministic demo alternatives; OSRM returns one real road route.
7. Read `/api/routes/history` or `/api/routes/{id}` with the same token. A different user receives not-found for another owner's calculation.
8. Read `/api/notifications?limit=50` and mark one owned record with `POST /api/notifications/{id}/read`.
9. Check readiness through `GET /api/health`.

## Tests and verified current commands

During this handoff inspection:

- `cd backend && mvn -q clean verify`: **passed**, 64 tests in 17 test classes, 0 failures/errors/skips.
- `cd frontend && npm test -- --run`: **passed**, 5 tests in 1 test file.
- `cd frontend && npm run build`: **passed** (TypeScript project build + Vite production bundle).
- `docker compose config --quiet`: passed during current frontend work's verification; rerun after local config edits.
- `git diff --check`: passed at the initial handoff inspection; rerun after documentation changes.

These results are snapshots, not a guarantee after another agent changes files. Frontend tests currently include a mocked Photon route-selection journey flow; live provider availability remains external.

## Known limitations and risks

- Three fixed demo cells and no dynamic geographic lookup, PostGIS or arbitrary cell creation.
- All built-in environment and route providers are mock/deterministic. Forecast history itself is based on those generated values in the demo environment.
- The forecast is an explainable historical baseline, not scientific or validated.
- Route geometries are direct/detour generated shapes, not road following. In the current frontend a Photon place coordinate can lie outside the three supported demo cells; RouteService still maps path-segment midpoints to the nearest demo cell.
- Route cards show expected pollution exposure and ranking score, not route-specific AQI. No weather context is integrated in route exposure ranking despite a separate phase-4 pollution score supporting weather context.
- Optional suitability metadata is absent from the default mock route, so green/cycling/elevation factors are excluded and explained as unavailable.
- Photon public endpoint and OSM tile services need internet and are subject to provider terms, service availability, attribution rules, and use limits. No SLA/key is provided.
- Security has no refresh/revoke, lockout, or rate-limiting flow. Use local development credentials only.
- Dashboard forecast alerts are evaluated on dashboard reads; no independent background notifications or outbound delivery.
- No PostGIS, cache/queue, microservice, ML service, Redis/Kafka/RabbitMQ/Kubernetes, cloud deployment, or Phase 11 implementation.
- Maven run in this environment reports Java 26 for tests; project POM and Docker image target Java 21. Maven verify passed here, but a separate local Java 21 run is appropriate for matching declared runtime.

## Future work

### Safe next work

- Finish/review and commit the already-present journey-planner changes only if requested; add any required documentation/tests and validate live geocoder error/availability behavior.
- Keep any new route/geocoder provider behind current provider interfaces; confirm provider contracts and attribution/rate policies before adding integrations.
- Add test cases or API documentation only after checking current code and migrations.
- Improve dynamic geographic coverage only with an explicit requirement and a design that replaces or intentionally extends the fixed-cell behavior.

### Deferred or not in the current plan

- Real AQI/weather/traffic/routing integrations and secrets for them.
- Advanced forecasting / ML, notifications beyond in-app, external notification delivery, route optimization beyond present rank modes, and any work not assigned to Phases 1–10.
- PostGIS, infrastructure services and phases after Phase 11. Do not infer these from optional schema fields or future-facing provider abstractions.

## Related handoff references

- `ARCHITECTURE_DETAILED.md`: dependency, request and sequence diagrams.
- `API_REFERENCE.md`: every actual REST mapping and contract.
- `DATABASE_REFERENCE.md`: table/column/index/migration inventory.
- `FRONTEND_REFERENCE.md`: UI, state and browser/backend interactions.
- `DEVELOPMENT_RUNBOOK.md`: setup and troubleshooting.
- `AI_AGENT_GUIDE.md`: instructions for future coding agents.
- `PHASE_HISTORY.md`: concise phase/commit timeline.
- `CURRENT_STATE.md`: fastest current snapshot.
- `TRANSFER_PROMPT.md`: copy/paste prompt for another CLI.
