# CleanRoute Development Runbook

Commands assume repository root and Java/Maven, Node/npm, and Docker Compose as required by the workflow.

## First-time setup

```sh
git status --short
cp .env.example .env
```

Edit ignored `.env` and set required values using a unique development-only PostgreSQL password and strong development-only `JWT_SECRET`. JWT validation requires at least 32 UTF-8 bytes. Do not paste real credentials into tracked files, logs, or documentation. `.env` is ignored by Git; `.env.example` uses placeholders.

Inspect variable names/defaults in `.env.example`, `docker-compose.yml`, and `backend/src/main/resources/application.yml`. Example placeholders are not production secrets.

## Start PostgreSQL

```sh
docker compose up -d postgres
docker compose ps
docker compose logs -f postgres
```

For local backend development, PostgreSQL must be reachable on configured host port (default 5432). Flyway runs automatically when backend starts.

## Start backend

With DB running and backend environment variables configured:

```sh
cd backend
mvn spring-boot:run
```

Alternatively, start the Compose backend from repository root:

```sh
docker compose up -d backend
```

Backend default port is 8080. Verify:

```sh
curl -i http://localhost:8080/api/health
curl -i 'http://localhost:8080/api/aqi/current?cell=demo-delhi-central'
```

The health response includes database status. AQI is public and labeled with provenance.

## Start frontend

```sh
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Set `VITE_API_BASE_URL` to the browser-reachable backend origin if it is not the default. Place search uses backend geocoding configuration; set `GEOCODING_PROVIDER=mock` to avoid external Photon access.

Routing defaults to deterministic `ROUTING_PROVIDER=mock`. To opt into one OSRM road route, configure `ROUTING_PROVIDER=osrm` and optionally `OSRM_API_URL`, `ROUTING_CONNECT_TIMEOUT_MS`, and `ROUTING_READ_TIMEOUT_MS`. OSRM requires network access. It returns a single primary route; no alternate road routes are fabricated. Environmental exposure remains based on the three fixed Delhi demo cells and generated/historical demo forecasts.

## Start entire application with Docker Compose

From root, after `.env` configuration:

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs -f postgres backend frontend
```

Compose starts PostgreSQL, Spring Boot, and Vite with service dependencies/health checks. URLs: UI `http://localhost:5173`, API `http://localhost:8080`, database host port 5432. Stop while preserving DB data:

```sh
docker compose down
```

`docker compose down -v` removes the database volume and its data; use only when intentionally resetting local state.

## Verification commands

```sh
cd backend
mvn -q clean verify
cd ../frontend
npm test -- --run
npm run build
cd ..
docker compose config --quiet
git diff --check
git status --short
```

For protected workflows, register/login through `/api/auth/register` or `/api/auth/login`, then send `Authorization: Bearer <token>`. Do not put real tokens in documentation or source.

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| Compose reports missing `JWT_SECRET` | Compose requires the variable; `.env` may be absent/incomplete. | Copy `.env.example` to ignored `.env`; set a strong local-only secret meeting the 32 UTF-8 byte minimum. Never commit it. |
| Docker daemon/socket permission error | Docker Desktop/daemon is stopped or CLI cannot reach it. | Start local Docker engine, retry `docker compose ps`, check OS-specific Docker permissions. |
| Backend cannot connect to PostgreSQL | DB unhealthy, credentials/port mismatch, or host-run backend uses container DNS name. | Check `docker compose ps/logs postgres`; host-run backend should use `localhost` and mapped port, container backend uses Compose networking. Match `.env` credentials. |
| Port already allocated | Another process uses 5432, 8080, or 5173. | Stop conflict or use configured host-port overrides; update frontend API URL if backend port changes. |
| Frontend API requests fail | Wrong `VITE_API_BASE_URL`, backend down, or CORS origin disallowed. | Use browser-reachable backend URL; inspect backend health/logs and `CLEANROUTE_ALLOWED_ORIGINS`; restart Vite after env changes. |
| Protected endpoint returns 401 | Missing, expired, or invalid bearer token. | Log in again and pass `Authorization: Bearer <token>`; expiration defaults to 24 hours. |
| Flyway migration fails | DB incompatible/partially initialized, migration history changed, or DB role lacks privileges. | Inspect backend logs and `flyway_schema_history`; do not edit old migration history. Reproduce against a disposable fresh database. |
| Place suggestions fail | Public Photon unavailable, network blocked, or query too short. | Check network/browser console; type at least three characters and select an offered result. |

## Development workflow

1. Inspect status/history and read relevant handoff plus original architecture/plan docs.
2. Find actual controller/service/provider/DTO/schema contracts before editing.
3. Make the smallest scoped change and add behavior regression tests.
4. Run backend tests and frontend tests/build relevant to changes.
5. Run Compose validation and API smoke checks when infrastructure/contracts change.
6. Review full `git diff`, `git diff --check`, and status for unrelated changes/secrets.
7. Commit or push only after explicit user authorization.
