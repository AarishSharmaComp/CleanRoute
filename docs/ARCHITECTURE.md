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

- Store observations as timestamped measurements associated with a geographic grid cell (initially a configurable latitude/longitude grid cell identifier and center coordinate). This avoids treating an entire city as one sensor reading and leaves a migration path to PostGIS geometry/geography.
- Store pollutant values, source/provider, observed time, ingestion time, and quality/freshness metadata. Weather and traffic observations use corresponding timestamped records.
- Store forecast rows separately from observations, with target timestamp, generated timestamp, model/version, predicted values, and quality indicator. API responses clearly label observed versus predicted values.
- Normalize forecast and demo-series timestamps to 15-minute interval boundaries. Seed at least seven days of generated history for demo mode; retain more in production according to configuration.
- Add indexes for location/grid cell and timestamp; use a composite `(grid_cell_id, observed_at)` index for observation and forecast lookups. PostgreSQL spatial indexing can be introduced with PostGIS when actual geographic search needs arise.

## Scoring and forecasting

- `PollutionEngine` converts available measurements into a documented normalized pollution burden, handling absent pollutants without fabricating values.
- `PollutionScoreService` combines exposure, route time/distance, traffic, weather, and mode using an explicit configurable formula. Display score direction and component explanations in the API. Scores are comparative estimates and must not be presented as validated medical guidance.
- `RouteScoringService` applies genuinely different preference weights for FASTEST, CLEANEST, BALANCED, JOGGER, and CYCLIST. Return component scores and recommendation reasons for explainability.
- `PollutionForecastService` implements a replaceable predictor interface. The initial predictor uses historical same-time/day averages with a weighted recent baseline and reports a quality/confidence indicator based on sample coverage and recency. It does not claim an ML model.
- Route exposure samples expected conditions along route segments at the estimated time of passage, using forecast data where available and a clearly marked fallback when unavailable.

## Persistence model

Initial relational entities: `User`, `UserPreference`, `SavedPlace`, `PollutionObservation`, `WeatherObservation`, `TrafficObservation`, `PollutionForecast`, `SavedRoute`, `RouteHistory`, and `Notification`. Route calculations may be persisted as route history; saved routes retain origin/destination, encoded geometry, mode, preference, score, duration, and creation time. Use DTOs at the API boundary rather than serializing entities.

Use Spring Security with BCrypt password hashing and stateless token authentication. Keep auth tokens and provider credentials out of source control. Validate and rate-limit authentication inputs; return consistent error responses. Demo data and optional demo user behavior must be documented and isolated from real account data.

## Provider and scheduler behavior

Scheduled ingestion is configurable, with a 15-minute default. Each provider adapter has bounded timeouts and translates rate limits, missing values, and provider errors to typed outcomes. A failed provider must not prevent other sources from recording data. Keep last-success/freshness state and log failures without logging secrets. Start with a single application scheduler; add a job queue only if workload requires it.

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

Docker Compose starts PostgreSQL, Spring Boot, and the frontend in demo mode. `.env.example` documents non-secret defaults and optional `AQI_API_KEY`, `WEATHER_API_KEY`, `TRAFFIC_API_KEY`, and `MAPS_API_KEY`; secrets are supplied through the local environment and ignored env files. Health checks gate service readiness. The application should remain usable without external API credentials.

## Quality attributes

- Unit-test scoring, ranking, forecast baseline, interval boundaries, and missing-data behavior.
- Integration-test API validation, persistence/migrations, and authentication flows.
- Use Flyway migrations, structured logs, and centralized error handling.
- Provide clear limitations for generated data, fallback estimates, forecast quality, route availability, and pollution score interpretation.
