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
  ├── scoring and recommendations
  ├── notifications and dashboard
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
- Add indexes for location/grid cell and timestamp; use a composite `(grid_cell_id, observed_at)` index for observation and forecast lookups. PostgreSQL spatial indexing can be introduced with PostGIS when actual geographic search needs arise.

## Scoring and forecasting

- Phase 4 `PollutionEngine` normalizes available pollutant concentrations against configurable reference values and averages only present measurements; if all individual concentrations are absent, it can fall back to provider AQI. It reports coverage and missing values without treating them as zero.
- Phase 4 `PollutionScoreService` combines the pollution burden adjusted by interval-specific duration, distance, and travel mode with available traffic and weather context using configurable weights. The authenticated-data boundary is unchanged; `/api/pollution/score` exposes an environmental estimate without personal route geometry. It reports direction, component scores, provenance, missing inputs, and caveats. This comparative demo estimate is not validated health guidance and does not rank or recommend routes.
- Forecast storage, aggregation, and prediction are not implemented in Phase 4; they remain Phase 5 work.
- `RouteScoringService` applies genuinely different preference weights for FASTEST, CLEANEST, BALANCED, JOGGER, and CYCLIST. Return component scores and recommendation reasons for explainability.
- `PollutionForecastService` implements a replaceable predictor interface. The initial predictor uses historical same-time/day averages with a weighted recent baseline and reports a quality/confidence indicator based on sample coverage and recency. It does not claim an ML model.
- Route exposure samples expected conditions along route segments at the estimated time of passage, using forecast data where available and a clearly marked fallback when unavailable.

## Persistence model

Initial relational entities: `User`, `UserPreference`, `SavedPlace`, `PollutionObservation`, `WeatherObservation`, `TrafficObservation`, `PollutionForecast`, `SavedRoute`, `RouteHistory`, and `Notification`. Route calculations may be persisted as route history; saved routes retain origin/destination, encoded geometry, mode, preference, score, duration, and creation time. Use DTOs at the API boundary rather than serializing entities.

Use Spring Security with BCrypt password hashing and stateless token authentication. Keep auth tokens and provider credentials out of source control. Validate and rate-limit authentication inputs; return consistent error responses. Demo data and optional demo user behavior must be documented and isolated from real account data.

## Provider and scheduler behavior

Scheduled ingestion is configurable, with a 15-minute default and a one-minute minimum. Each provider adapter runs in an isolated bounded worker lane with a configurable timeout (five-second default); timeouts interrupt/cancel calls, and bounded queues prevent worker growth. Typed timeout, temporary-failure, and rate-limit outcomes are isolated per provider/cell. Rate limits apply a bounded in-memory backoff (one-minute default, five-minute maximum) without a retry queue. Persist last-attempt, last-success, and last-failure state per provider/cell. Failures are logged without exception messages or secrets. Startup seeds seven days of deterministic generated observations across the fixed demo cells. Current AQI is marked stale after 30 minutes.

The `RoutingProvider` interface returns a provider-neutral generated path; the local mock returns a straight line between supplied coordinates. It does not rank alternatives or select routes based on pollution. Real routing integrations and route recommendations remain later-phase work.

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
- `GET /api/pollution/forecast?location=...&from=...&interval=15` — interval forecast; response labels prediction data and quality.
- `POST /api/routes/calculate`, `GET /api/routes/{id}` — calculate alternatives and retrieve route details.
- `POST /api/routes/save`, `GET /api/routes/saved` — save/list a user's routes.
- `GET /api/dashboard` — dashboard summary for the authenticated user or documented demo mode.
- `GET /api/notifications`, `POST /api/notifications/{id}/read` — internal notifications.

Use validated request/response DTOs, pagination for history, consistent error bodies, and explicit units and timestamps (ISO-8601 with timezone).

## Local operation and configuration

Docker Compose starts PostgreSQL, Spring Boot, and the frontend in demo mode. `.env.example` documents non-secret defaults and optional `AQI_API_KEY`, `WEATHER_API_KEY`, and `TRAFFIC_API_KEY`; mock providers run without credentials, and any future provider keys are supplied through the local environment and ignored env files. Health checks gate service readiness. The application should remain usable without external API credentials.

## Quality attributes

- Unit-test scoring, ranking, forecast baseline, interval boundaries, and missing-data behavior.
- Integration-test API validation, persistence/migrations, and authentication flows.
- Use Flyway migrations, structured logs, and centralized error handling.
- Provide clear limitations for generated data, fallback estimates, forecast quality, route availability, and pollution score interpretation.
