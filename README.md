# CleanRoute

CleanRoute is a pollution-aware route planning platform. The repository includes the foundation, accounts, environmental observations, pollution scoring and baseline forecasting, mock route alternatives, suitability ranking, a connected dashboard, owner-scoped route history, and in-app notifications through Phase 10.

## Requirements

- Docker Desktop (or Docker Engine) with Docker Compose v2 for the container workflow
- For running services directly: Java 21, Maven 3.9+, Node.js 22+, npm, and PostgreSQL 16+

## Run everything with Docker Compose

1. Copy the template and set a local PostgreSQL password in `.env`:

   ```sh
   cp .env.example .env
   ```

   Edit `.env` and set `POSTGRES_PASSWORD` to a password of your choice. The JWT value in the template is only a development example; replace it with a random secret of at least 32 bytes for any shared environment. Keep `.env` private; it is ignored by Git.

   If host port `5432` is already in use, change `POSTGRES_PORT` in `.env` (for example, to `55432`). The backend connects to PostgreSQL over the Compose network, so this only changes the port exposed on your computer.

2. Build and start PostgreSQL, backend, and frontend:

   ```sh
   docker compose up --build
   ```

3. Open the frontend at <http://localhost:5173>. Check the backend at <http://localhost:8080/api/health>. Check service status with `docker compose ps`.

4. Stop services with `Ctrl+C`, or run `docker compose down`. To remove the local database volume as well, run `docker compose down -v`.

No external API keys are needed. The Compose database is local. Flyway applies migrations under `backend/src/main/resources/db/migration/`. On startup, deterministic generated AQI, weather, and traffic observations are seeded for seven days across three demo cells. These values are mock data and do not represent actual conditions.

## Run backend directly

Start PostgreSQL first. One option is to start only its container after creating `.env` as above:

```sh
docker compose up -d postgres
```

Then, from `backend/`, run (replace the password with the value you chose):

```sh
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/cleanroute \
SPRING_DATASOURCE_USERNAME=cleanroute \
SPRING_DATASOURCE_PASSWORD='your-local-password' \
JWT_SECRET='change-this-to-a-random-secret-of-at-least-32-bytes' \
mvn spring-boot:run
```

The backend listens on <http://localhost:8080>. `GET /api/health` returns a JSON status, service name, and timestamp.

Build and test it with:

```sh
cd backend
mvn test
mvn package
```

## Run frontend directly

From `frontend/`:

```sh
npm ci
npm run dev
```

Open <http://localhost:5173>. Build and test with:

```sh
npm test
npm run build
```

The frontend provides route planning, current conditions, observed/forecast AQI charting, a Leaflet map, saved places and routes, calculation history, preferences, and notifications. It calls the backend using `VITE_API_BASE_URL` (default `http://localhost:8080`).

## Configuration

See `.env.example` for Compose ports, PostgreSQL settings, `JWT_SECRET`, and observation settings. Set a non-empty `POSTGRES_PASSWORD` in `.env`. Backend settings can also use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `SERVER_PORT`, `CLEANROUTE_ALLOWED_ORIGINS`, `OBSERVATION_INGESTION_INTERVAL_MS` (default 900000; minimum 60000), `OBSERVATION_PROVIDER_TIMEOUT_MS` (default 5000), and `OBSERVATION_RATE_LIMIT_BACKOFF_MS` (default 60000; maximum 300000). Suitability and notification baselines are configured under `app.routes.suitability` and `app.notifications` in `application.yml`. Provider API keys remain optional placeholders; deterministic mocks are used locally.

## Current API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Backend liveness response |
| `POST` | `/api/auth/register` | Register a user and return a bearer token |
| `POST` | `/api/auth/login` | Authenticate and return a bearer token |
| `GET`, `PUT` | `/api/users/me` | Read/update the authenticated user's profile |
| `PUT` | `/api/users/me/preferences` | Read/update user preferences (returned by GET /me) |
| `POST`, `GET`, `DELETE` | `/api/places` | Create, list, and delete the current user's saved places |
| `POST` | `/api/routes/save` | Save a supplied route record; does not calculate routes |
| `GET`, `DELETE` | `/api/routes/saved` | List/delete the current user's saved routes |
| `GET` | `/api/routes/history` | Retrieve the current user's route history |
| `GET` | `/api/dashboard` | Authenticated dashboard data: environmental series, saved places/routes, calculation history, and notifications |
| `GET` | `/api/notifications?limit=50` | List the authenticated user's in-app notifications |
| `POST` | `/api/notifications/{id}/read` | Mark one owned notification as read |
| `GET` | `/api/aqi/current?cell=demo-delhi-central` | Latest AQI observation for a cell; includes provider, generated flag, stale flag, timestamp, and units |
| `GET` | `/api/aqi/history?cell=...&start=...&end=...&interval=15&limit=100&offset=0` | Historical AQI observations for a known cell; ISO-8601 timestamps, maximum 90-day range and 1,000 rows per page |
| `GET` | `/api/pollution/score?cell=...&at=...&durationSeconds=...&distanceMeters=...&mode=WALK` | Comparative score for one stored 15-minute observation interval and supplied trip context; returns component scores, missing inputs, provenance, and caveats |
| `GET` | `/api/pollution/forecast?cell=...&from=...&interval=15&count=4` | Generate and persist up to 96 future 15-minute pollution predictions from historical patterns, with quality and predicted-data labels |
| `GET` | `/api/pollution/forecast/history?cell=...&from=...&to=...&limit=100` | Retrieve persisted forecast rows for a cell and bounded time range |
| `POST` | `/api/routes/calculate` | Authenticated route calculation returning mock alternatives ranked by FASTEST, CLEANEST, or BALANCED preference |
| `GET` | `/api/routes/{id}` | Retrieve the authenticated user's saved route calculation |

Route calculations accept FASTEST, CLEANEST, BALANCED, JOGGER, and CYCLIST preferences. JOGGER requires JOG mode; CYCLIST requires CYCLE mode. Suitability weights and thresholds are configurable under `app.routes.suitability`. The mock has no green-space, cycling-compatibility, or elevation metadata, so those factors are reported as unavailable and excluded. Route results and saved data are scoped to the signed-in user.

In-app notifications are generated when an authenticated dashboard request evaluates a high forecast or when a route calculation includes a materially cleaner alternative. The thresholds are configurable under `app.notifications`; user notification preferences are respected. Delivery is behind a provider interface and currently stores notifications in PostgreSQL only.

Pass the login/register token as `Authorization: Bearer <token>` for user-specific endpoints. Registration requires `email`, `password` (8–72 characters), and `displayName`. Passwords are stored as BCrypt hashes. The Phase 2 schema is created only by Flyway migrations and Hibernate validates it at startup.

AQI history returns 404 for an unknown cell and an empty list for a known cell with no observations. AQI concentrations use canonical `µg/m³` for PM2.5, PM10, NO₂, SO₂, and O₃, and `mg/m³` for CO; AQI remains on the provider's index scale. The current demo cell IDs are `demo-delhi-central`, `demo-delhi-south`, and `demo-delhi-north`.

The Phase 4 pollution score is a 0–100 comparative demo estimate; higher values indicate greater modeled burden. Available pollutant concentrations are normalized against configurable reference values and averaged. Provider AQI is used only when all individual pollutant concentrations are missing. Route duration, distance, and travel mode adjust the exposure component; available traffic and weather context are combined with configurable weights, with missing context excluded. Defaults are in `application.yml` under `app.pollution.scoring`. These reference values are model baselines, not regulatory limits or validated health guidance. The endpoint scores a single stored observation interval and does not calculate or rank routes.

Phase 5 forecasts use an interchangeable forecast-provider interface. The default historical baseline matches the 15-minute time slot and weekday where available, uses exponentially recency-weighted means, falls back to the same time-of-day or available history when sparse, and reports sample-based quality. Forecasts are separate persisted records and responses identify them as `PREDICTED` (`observed: false`, `predicted: true`). Missing pollutant measurements remain null. This is not an advanced or validated forecasting model.

Phase 6 route calculations use the provider-neutral routing interface; its deterministic mock returns direct and two detour alternatives. Segment midpoints are assigned to the nearest of the three fixed demo cells and forecast for estimated passage intervals. FASTEST ranks duration, CLEANEST ranks distance-weighted expected pollution exposure, and BALANCED combines duration efficiency and pollution cleanliness equally. Responses include geometry, duration, distance, exposure, component scores, reasons, and generated-data provenance. Calculation requests and results belong to the authenticated user. JOGGER/CYCLIST preference suitability rules and the map/dashboard UI remain later phases.

## Project structure

```text
backend/       Spring Boot / Maven application
frontend/      React / TypeScript / Vite application
docs/          Architecture and phased implementation plan
docker-compose.yml
.env.example
```

## Current limitations

The historical forecast, pollution scores, suitability ranks, and notifications are transparent demo baselines, not validated health guidance. Route paths are deterministic generated mock alternatives, and their pollution context uses nearest-cell matching against three fixed demo cells rather than dynamic geographic lookup. Green-area, cycling-compatibility, and elevation metadata are not present in the mock routing data. The dashboard uses OpenStreetMap tiles; internet access is required for those tiles. Real external provider integrations remain deferred. Provider freshness is stored per provider and cell; current AQI is marked stale after 30 minutes. Redis is not required.
