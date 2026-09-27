# CleanRoute

CleanRoute is a pollution-aware route planning platform. The repository includes the Phase 1 foundation and Phase 2 account, preferences, and saved-user-data APIs. Route calculation, pollution scoring, and forecasting are not implemented yet.

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

The frontend shell is self-contained and does not need a running backend to render.

## Configuration

See `.env.example` for Compose ports, PostgreSQL settings, and `JWT_SECRET`. Set a non-empty `POSTGRES_PASSWORD` in `.env`. Backend settings can also use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, and `SERVER_PORT`. No provider credentials are needed.

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

Pass the login/register token as `Authorization: Bearer <token>` for user-specific endpoints. Registration requires `email`, `password` (8–72 characters), and `displayName`. Passwords are stored as BCrypt hashes. The Phase 2 schema is created only by Flyway migrations and Hibernate validates it at startup.

## Project structure

```text
backend/       Spring Boot / Maven application
frontend/      React / TypeScript / Vite application
docs/          Architecture and phased implementation plan
docker-compose.yml
.env.example
```

## Current limitations

Route calculation, pollution engine, forecasts, notifications, and external provider integrations remain future phases. Route history is modeled for later use and currently has retrieval only. Redis is not required.
