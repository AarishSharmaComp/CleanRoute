# CleanRoute Implementation Plan

The repository starts empty. Work will proceed in reviewable phases; Phase 1 begins only after approval of this plan.

## Phase 1 — Foundation

Create the `backend/` Spring Boot Maven application and `frontend/` React/TypeScript Vite application, repository `.gitignore`, `.env.example`, Docker Compose, and initial README. Add PostgreSQL configuration and Flyway-ready setup, a backend health endpoint, a frontend shell, and service health checks. Keep the services runnable locally without provider keys.

**Done when:** documented local startup works, health checks succeed, and no credentials are committed.

## Phase 2 — Schema, user accounts, and authentication

Add the initial relational schema and indexes through Flyway migrations. Implement registration/login, BCrypt password hashing, Spring Security token authentication, user preferences, and validated auth DTOs. Add focused unit and persistence/API tests.

**Done when:** a user can register and authenticate, passwords are only stored as hashes, and protected endpoints enforce authentication.

## Phase 3 — Provider adapters, demo data, and ingestion

Define AQI, weather, traffic, and routing provider interfaces and provider-neutral models. Implement deterministic mock adapters and demo seeding for at least seven days of 15-minute environmental history, with varying daily/weekly patterns. Add an independently failing, configurable scheduled ingestion pipeline with bounded provider timeouts, provider/cell/value/timestamp validation, persisted freshness state, and bounded rate-limit backoff. The mock routing provider returns a generated path only; route scoring and ranking remain out of scope.

**Done when:** the demo works with no API keys, scheduled ingestion persists explicitly labeled observations, one provider timeout/failure does not block other providers, rate limits are bounded/backed off, and provider freshness is persisted.

## Phase 4 — Pollution model and scoring

Implement interval-aware pollution assessments from stored cell observations. `PollutionEngine` normalizes available pollutant measurements against configurable reference values, excludes missing values, reports coverage, and uses AQI only as a documented fallback when all individual pollutants are absent. `PollutionScoreService` combines the assessment with supplied duration, distance, mode, and available traffic/weather context using configurable weights. Expose component explanations, provenance, score direction, and explicit limitations through a score API. Do not calculate or rank alternative routes in this phase.

**Done when:** component calculations, missing values, and score ranges are documented and covered by unit tests.

## Phase 5 — Baseline forecasting

Add historical pattern aggregation and a replaceable forecast-provider interface. Implement 15-minute predictions from time-of-day/day-of-week historical averages with recent weighting and coverage-based quality indicators. Expose current/history/forecast APIs and preserve observed/predicted distinctions.

**Done when:** forecasts can be requested for future intervals, timestamps align to 15-minute boundaries, and tests cover sparse history and quality reporting.

## Phase 6 — Routing and preference ranking

Implement `RouteService` and provider-neutral route alternatives. Add route segment sampling, expected pollution exposure by passage time, and different FASTEST, CLEANEST, and BALANCED ranking weights. Return explanatory score components and reasons.

**Done when:** mock alternatives produce distinguishable ranking under each preference and route results include geometry, duration, distance, exposure, and context.

## Phase 7 — Jogger and cyclist recommendations

Add configurable mode suitability rules: low pollution/traffic, green-area preference where mock/map metadata supports it, suitable distance for joggers, and cycling compatibility/elevation when available. Keep unsupported factors explicit rather than inventing values.

**Done when:** JOGGER and CYCLIST preferences change candidate ranking and explain the criteria used.

## Phase 8 — Dashboard and visualizations

Build the responsive dashboard with route search, Leaflet map, colored route alternatives, pollution legend, current AQI, observed/forecast chart, and route details. Integrate the REST API and show loading, empty, and error states.

**Done when:** the demo journey can be searched and compared on the map, and observed versus predicted chart values are clearly distinguishable.

## Phase 9 — Saved routes, history, and notifications

Add saved places/routes, route history, user preference persistence, dashboard aggregation, and internal notifications with read state. Implement baseline notification rules for unusually high forecast pollution and cleaner alternatives; keep delivery behind a notification interface for later email/push adapters.

**Done when:** authenticated users can save, retrieve, and review routes and notifications; demo behavior is documented.

## Phase 10 — Hardening and handoff

Complete focused unit, integration, and frontend checks; validate inputs and authorization boundaries; improve error handling, logging, health/readiness behavior, and startup documentation. Document architecture, schema, endpoints, environment variables, mock data, limitations, and provider integration points in README and docs.

**Done when:** the documented Docker Compose workflow is reproducible, quality checks pass, and known limitations and extension points are clear.

## Implementation principles

- Keep a single modular Spring application and add infrastructure only when needed.
- Prefer provider interfaces and deterministic demo data over mandatory third-party accounts.
- Keep observed values, generated predictions, and synthetic demo values explicitly labeled.
- Use transparent configurable scoring and forecasting baselines; do not present them as validated health models or advanced ML.
- Implement and review one phase at a time, preserving working code and keeping secrets out of the repository.
