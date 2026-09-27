# CleanRoute

CleanRoute is a pollution-aware route planning platform. This repository currently contains the Phase 1 foundation: a Spring Boot backend, React/TypeScript frontend, and PostgreSQL local environment. Route planning, authentication, pollution scoring, and forecasting are not implemented yet.

## Requirements

- Docker Desktop (or Docker Engine) with Docker Compose v2 for the container workflow
- For running services directly: Java 21, Maven 3.9+, Node.js 22+, npm, and PostgreSQL 16+

## Run everything with Docker Compose

1. Copy the template and set a local PostgreSQL password in `.env`:

   ```sh
   cp .env.example .env
   ```

   Edit `.env` and set `POSTGRES_PASSWORD` to a password of your choice. The template deliberately contains no password value. Keep `.env` private; it is ignored by Git.

   If host port `5432` is already in use, change `POSTGRES_PORT` in `.env` (for example, to `55432`). The backend connects to PostgreSQL over the Compose network, so this only changes the port exposed on your computer.

2. Build and start PostgreSQL, backend, and frontend:

   ```sh
   docker compose up --build
   ```

3. Open the frontend at <http://localhost:5173>. Check the backend at <http://localhost:8080/api/health>. Check service status with `docker compose ps`.

4. Stop services with `Ctrl+C`, or run `docker compose down`. To remove the local database volume as well, run `docker compose down -v`.

No external API keys are needed. The Compose database is local. Flyway is enabled and ready to apply migrations under `backend/src/main/resources/db/migration/`.

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

The frontend shell is self-contained and does not need a running backend to render.

## Configuration

See `.env.example` for Compose ports and PostgreSQL settings. Set a non-empty `POSTGRES_PASSWORD` in `.env`; no password is supplied in the repository. Backend settings can also use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `SERVER_PORT`. No provider credentials are needed in Phase 1.

## Current API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Backend liveness response |

## Project structure

```text
backend/       Spring Boot / Maven application
frontend/      React / TypeScript / Vite application
docs/          Architecture and phased implementation plan
docker-compose.yml
.env.example
```

## Phase 1 limitations

This phase intentionally includes no authentication, route calculation, pollution engine, forecast service, or real external API integrations. The database and Flyway integration are configured, but no business schema migrations exist yet. Redis is not required.
