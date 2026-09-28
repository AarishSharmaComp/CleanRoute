# CleanRoute Architecture

## Goal and scope

CleanRoute is a pollution-aware route planning application. The MVP runs locally with generated demo observations and mock external providers. Its pollution scores and forecasts are transparent, configurable baselines; they are estimates, not scientifically validated health measurements.

## Repository layout

```text
cleanroute/
├── backend/                 # Java 21, Spring Boot, Maven modular monolith
│   ├── src/main/java/.../   # API, application services, domain, infrastructure
│   ├── src/main/resources/  # configuration and Flyway migrations
│   └── src/test/java/.../  # unit and integration tests
├── frontend/                # React, TypeScript, Vite
│   └── src/                 # app, features, shared API/UI/map components
├── docs/                    # architecture and implementation plan
├── docker-compose.yml       # frontend, backend, PostgreSQL for local demo
├── .env.example
├── .gitignore
└── README.md
```

## Runtime shape

```text
React + TypeScript (Leaflet map, chart library)
                  │ REST/JSON
                  ▼
Spring Boot modular monolith
  ├── auth and user profile
  ├── places and route planning
  ├── pollution observations and forecasts
  ├── scoring and suitability ranking
  ├── in-app notifications and dashboard
  └── provider adapters and scheduled ingestion
                  │
                  ▼
PostgreSQL (Flyway-managed schema; spatial-ready grid cells)
```

Redis is intentionally not part of the initial runtime. Add it only if measured cache or job coordination needs justify it. No microservices are needed for the MVP.

## Backend module boundaries

- **API:** REST controllers, request validation, response DTOs, exception mapping.
- **Application:** `RouteService`, `PollutionForecastService`, `RecommendationService`, `NotificationService`, and orchestration use cases.
- **Domain:** pollution scoring, route ranking, preference rules, time interval semantics, and provider-neutral models.
- **Infrastructure:** Spring Data JPA repositories, PostgreSQL/Flyway, security, schedulers, and external provider adapters.

Keep the business services independent of Spring and provider-specific SDKs where practical. Expose interfaces for `AQIProvider`, `WeatherProvider`, `TrafficProvider`, and `RoutingProvider`; use deterministic mock implementations by default. Future integrations are configured with environment variables, never committed credentials.

## Data and time model

- Store observations as timestamped measurements associated with a geographic cell. Phase 3 currently seeds three fixed demo cells with deterministic identifiers and latitude/longitude center coordinates; it does not yet support configurable or dynamic cell lookup or arbitrary coordinate-based area queries. The cell identifier and center-coordinate model leaves room to add configurable grid coverage later, and a migration path to PostGIS geometry/geography if spatial queries eventually require it.
- Store pollutant values, source/provider, observed time, ingestion time, generated-data identity, and per-provider/per-cell last-attempt, last-success, and last-failure freshness state. Weather and traffic observations use corresponding timestamped records.
- Normalize provider pollutant concentrations to µg/m³ for PM2.5, PM10, NO₂, SO₂, and O₃ and mg/m³ for CO. AQI is retained on the provider's index scale; responses expose these units. Reject provider timestamps more than two minutes ahead of the application clock to allow ordinary clock skew without accepting future observations.
- Store forecast rows separately from observations, with target timestamp, generated timestamp, model/version, predicted values, and quality indicator. API responses clearly label observed versus predicted values.
- Normalize forecast and demo-series timestamps to 15-minute interval boundaries. Seed at least seven days of generated history for demo mode; retain more in production according to configuration.
- Add indexes for geographic cell and timestamp; observation queries use `(cell_id, observed_at)` and forecast queries use `(cell_id, target_at)`. PostgreSQL spatial indexing can be introduced with PostGIS when actual geographic search needs arise.

## Scoring and forecasting

- Phase 4 `PollutionEngine` normalizes available pollutant concentrations against configurable reference values and averages only present measurements; if all individual concentrations are absent, it can fall back to provider AQI. It reports coverage and missing values without treating them as zero.
- Phase 4 `PollutionScoreService` combines the pollution burden adjusted by interval-specific duration, distance, and travel mode with available traffic and weather context using configurable weights. The authenticated-data boundary is unchanged; `/api/pollution/score` exposes an environmental estimate without personal route geometry. It reports direction, component scores, provenance, missing inputs, and caveats. This comparative demo estimate is not validated health guidance and does not rank or recommend routes.
- Phase 5 persists forecasts separately from observations in `pollution_forecast`. `PollutionForecastService` uses the replaceable `ForecastProvider`; the historical average provider matches same-time/day patterns where available, exponentially weights recent observations, falls back for sparse history, and reports sample-based quality. Responses explicitly mark values as predicted, never observed. This is a baseline, not an ML model.
- Phase 6 `RouteService` obtains provider-neutral alternatives, samples segment midpoints at estimated passage times, maps samples to the nearest of the three fixed demo cells, and calculates expected pollution exposure from persisted or newly generated forecasts. FASTEST ranks duration, CLEANEST ranks pollution cleanliness, and BALANCED equally combines normalized time efficiency and pollution cleanliness. Components and reasons are returned. JOGGER/CYCLIST suitability and route recommendations belong to Phase 7.
- Phase 7 adds configurable JOGGER/CYCLIST suitability using modeled pollution and available traffic; JOGGER also scores distance against a configurable target range. Optional route metadata can provide green-area coverage, cycling compatibility, and elevation; missing metadata is excluded from the score and explained in the response. JOGGER requires JOG mode and CYCLIST requires CYCLE mode.
- Phase 9 `DashboardService` combines observed/forecast data with owner-scoped places, saved routes, calculations, history, and notifications. In-app notification records are deduplicated per user and event. High forecast alerts are evaluated on authenticated dashboard reads; cleaner-alternative alerts are evaluated after route calculations. User notification preferences are honored. Notification delivery is replaceable through `NotificationProvider`; only in-app PostgreSQL delivery is implemented.

## Persistence model

Initial relational entities: `User`, `UserPreference`, `SavedPlace`, `PollutionObservation`, `WeatherObservation`, `TrafficObservation`, `PollutionForecast`, `RouteCalculation`, `SavedRoute`, `RouteHistory`, and `Notification`. Phase 6 stores each calculation result in `route_calculation` scoped to its authenticated owner; existing saved-route and route-history records remain separate. Saved routes retain origin/destination, encoded geometry, mode, preference, score, duration, and creation time. Use DTOs at the API boundary rather than serializing entities.

V5 extends the accepted route calculation preferences for JOGGER/CYCLIST. V6 adds `user_notification` with an owner foreign key, per-user deduplication, read timestamp, and owner/time indexes. Route calculations append a compact owner-scoped row to the existing `route_history` table in the same transaction.

Use Spring Security with BCrypt password hashing and stateless token authentication. Keep auth tokens and provider credentials out of source control. Validate and rate-limit authentication inputs; return consistent error responses. Demo data and optional demo user behavior must be documented and isolated from real account data.

## Provider and scheduler behavior

Scheduled ingestion is configurable, with a 15-minute default and a one-minute minimum. Each provider adapter runs in an isolated bounded worker lane with a configurable timeout (five-second default); timeouts interrupt/cancel calls, and bounded queues prevent worker growth. Typed timeout, temporary-failure, and rate-limit outcomes are isolated per provider/cell. Rate limits apply a bounded in-memory backoff (one-minute default, five-minute maximum) without a retry queue. Persist last-attempt, last-success, and last-failure state per provider/cell. Failures are logged without exception messages or secrets. Startup seeds seven days of deterministic generated observations across the fixed demo cells. Current AQI is marked stale after 30 minutes.

The `RoutingProvider` interface returns provider-neutral paths; the local mock's single-path method returns a straight line, while its Phase 6 alternatives method returns a direct path and two deterministic detours. `RouteService` ranks those alternatives for FASTEST, CLEANEST, and BALANCED. Real routing integrations and Phase 7 suitability recommendations remain later-phase work.

## Frontend

- React + TypeScript + Vite, organized by feature (`routes`, `pollution`, `saved`, `notifications`, `auth`) with a shared API client and UI primitives.
- Leaflet with OpenStreetMap tiles for the interactive MVP map; route lines and markers use pollution-level colors with a legend. Respect tile-provider attribution and document that production tile/routing use may need a dedicated provider.
- Route search and alternatives expose travel mode, preference, time, distance, AQI/exposure, traffic/weather context, and recommendation reasons.
- Forecast chart distinguishes historical observed points from predicted points visually and in accessible labels.
- Dashboard includes current conditions, forecast, map, alternatives, saved routes, history, and notifications; responsive layout supports narrow screens.

## API outline

All application endpoints use `/api` and versioning can be added before a public release.

- `GET /api/health` — liveness/readiness summary.
- `POST /api/auth/register`, `POST /api/auth/login` — account creation and login.
- `GET /api/aqi/current`, `GET /api/aqi/history` — current and historical observations.
- `GET /api/pollution/forecast?cell=...&from=...&interval=15&count=4` — generate a bounded 15-minute forecast series; response labels prediction data and quality. `GET /api/pollution/forecast/history` retrieves persisted predictions.
- `POST /api/routes/calculate`, `GET /api/routes/{id}` — calculate alternatives for FASTEST, CLEANEST, BALANCED, JOGGER, or CYCLIST and retrieve route details.
- `POST /api/routes/save`, `GET /api/routes/saved` — save/list a user's routes.
- `GET /api/dashboard` — authenticated observations, forecasts, saved places/routes, calculation history, and notifications.
- `GET /api/notifications?limit=50`, `POST /api/notifications/{id}/read` — owner-scoped internal notifications.

Use validated request/response DTOs, pagination for history, consistent error bodies, and explicit units and timestamps (ISO-8601 with timezone).

## Local operation and configuration

Docker Compose starts PostgreSQL, Spring Boot, and the frontend in demo mode. `.env.example` documents non-secret defaults and optional `AQI_API_KEY`, `WEATHER_API_KEY`, and `TRAFFIC_API_KEY`; mock providers run without credentials, and any future provider keys are supplied through the local environment and ignored env files. Health checks gate service readiness. The application should remain usable without external API credentials.

`GET /api/health` checks database connectivity and returns HTTP 503 with `status: DOWN` when PostgreSQL is unavailable. Local browser origins are restricted to the configured `CLEANROUTE_ALLOWED_ORIGINS` list (localhost ports 5173 by default); bearer-token APIs remain stateless.

## Quality attributes

- Unit-test scoring, ranking, forecast baseline, interval boundaries, and missing-data behavior.
- Integration-test API validation, persistence/migrations, and authentication flows.
- Use Flyway migrations, structured logs, and centralized error handling.
- Provide clear limitations for generated data, fallback estimates, forecast quality, route availability, and pollution score interpretation.
