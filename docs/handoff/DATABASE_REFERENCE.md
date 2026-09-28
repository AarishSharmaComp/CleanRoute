# CleanRoute Database Reference

PostgreSQL schema is built by Flyway migrations in `backend/src/main/resources/db/migration/`. Current migration order is V1 through V6. Existing migrations should be treated as immutable history; add a new version for later schema changes.

Types below use PostgreSQL names. `TIMESTAMP WITH TIME ZONE` corresponds to offset-aware instants. Nullable means SQL NULL is allowed. Indexes listed are declared by migrations; primary/unique constraints also create PostgreSQL indexes.

## V1 — Phase 2 user domain

### `app_user`

| Column | Type | Null? | Notes |
|---|---|---|---|
| `id` | UUID | No | Primary key. |
| `email` | VARCHAR(254) | No | Unique. |
| `password_hash` | VARCHAR(100) | No | BCrypt hash; never API output. |
| `display_name` | VARCHAR(100) | No | |
| `created_at`, `updated_at` | TIMESTAMP WITH TIME ZONE | No | Default current timestamp. |

### `user_preference`

| Column | Type | Null? | Notes |
|---|---|---|---|
| `user_id` | UUID | No | PK and FK to `app_user`, cascade delete. |
| `preferred_travel_mode` | VARCHAR(16) | No | Default `WALK`. |
| `route_preference` | VARCHAR(16) | No | Default `BALANCED`. |
| `notifications_enabled` | BOOLEAN | No | Default true. |
| `pollution_sensitivity` | INTEGER | No | Default 3; check 1–5. |
| `updated_at` | TIMESTAMP WITH TIME ZONE | No | Default current timestamp. |

### `saved_place`

`id UUID` PK; `user_id UUID` required FK to app_user cascade; `name VARCHAR(120)` required; `latitude`, `longitude DOUBLE PRECISION` required and range checked; `address VARCHAR(500)` nullable; `created_at TIMESTAMP WITH TIME ZONE` required/default. Index `(user_id, created_at DESC)`.

### `saved_route`

`id UUID` PK; `user_id UUID` required FK/cascade; origin/destination names `VARCHAR(200)` required; four coordinate columns `DOUBLE PRECISION` required and range checked by V3; `geometry_polyline TEXT` nullable; `travel_mode VARCHAR(16)` and `route_preference VARCHAR(16)` required; `pollution_score DOUBLE PRECISION`, `estimated_travel_time_seconds INTEGER`, `distance_meters DOUBLE PRECISION` nullable; `created_at TIMESTAMP WITH TIME ZONE` required/default. Index `(user_id, created_at DESC)`. There is no SQL enum/check for mode/preference in V1.

### `route_history`

`id UUID` PK; `user_id UUID` required FK/cascade; origin/destination names `VARCHAR(200)` required; mode/preference `VARCHAR(16)` required; pollution score, estimated seconds, distance nullable; created timestamp required/default. Index `(user_id, created_at DESC)`.

## V2 — Phase 3 environmental observations

### `geographic_cell`

`cell_id VARCHAR(80)` PK; `center_latitude`, `center_longitude DOUBLE PRECISION` required and range checked; `created_at TIMESTAMP WITH TIME ZONE` required/default. Application currently recognizes three fixed demo cells; there is no spatial extension/index.

### Observation tables

Each observation table has `id UUID` PK, `cell_id VARCHAR(80)` required FK to `geographic_cell`, `observed_at` and `ingested_at TIMESTAMP WITH TIME ZONE` required (`ingested_at` defaults current timestamp), `provider VARCHAR(100)` required, `generated BOOLEAN` required. Unique key is `(provider, cell_id, observed_at)`; query index `(cell_id, observed_at DESC)`.

| Table | Additional columns | Constraints |
|---|---|---|
| `pollution_observation` | `aqi INTEGER` nullable; `pm25`, `pm10`, `no2`, `so2`, `co`, `o3 DOUBLE PRECISION` nullable | AQI 0–500; each pollutant >=0. |
| `weather_observation` | `temperature_c DOUBLE PRECISION`, `humidity_percent DOUBLE PRECISION`, `wind_speed_mps DOUBLE PRECISION`, `wind_direction_degrees DOUBLE PRECISION`, `precipitation_mm DOUBLE PRECISION`, `weather_condition VARCHAR(40)`; all nullable | humidity 0–100, wind speed >=0, direction 0–360, precipitation >=0. |
| `traffic_observation` | `traffic_level VARCHAR(20)`, `congestion_factor DOUBLE PRECISION`, `average_speed_kph DOUBLE PRECISION`; nullable | congestion 1–5; speed >=0. |

NULL pollutant/measurement fields mean unavailable; they are not zeros.

## V3 — provider freshness and coordinate checks

### `provider_freshness`

Composite PK `(provider_id VARCHAR(100), cell_id VARCHAR(80))`; cell is required FK to geographic cell. `last_attempt_at TIMESTAMP WITH TIME ZONE` required; `last_success_at`, `last_failure_at` same type nullable; `last_failure_type VARCHAR(32)` nullable. Index `(cell_id, provider_id)`. This records per-provider/per-cell freshness/failure state; it is not a separate monitoring service.

V3 also adds four range checks to `saved_route`: origin latitude [-90,90], origin longitude [-180,180], destination latitude [-90,90], destination longitude [-180,180].

## V4 — Phase 5 forecasts and Phase 6 route calculations

### `pollution_forecast`

Composite PK `(cell_id, target_at, model_version)`; cell FK to geographic_cell. `target_at`, `generated_at TIMESTAMP WITH TIME ZONE` required. Nullable `aqi INTEGER` and six pollutant `DOUBLE PRECISION` fields (`pm25`, `pm10`, `no2`, `so2`, `co`, `o3`) have nonnegative/range checks. `quality_score INTEGER` required 0–100; `quality VARCHAR(12)` required and in LOW/MEDIUM/HIGH; `sample_count INTEGER` required >0; `provider VARCHAR(100)`, `model_version VARCHAR(80)` required; `source_generated BOOLEAN` required. Index `(cell_id, target_at)`.

### `route_calculation`

`id UUID` PK; `user_id UUID` required FK to app_user cascade; four required `DOUBLE PRECISION` origin/destination coordinates with geographic range checks; `travel_mode VARCHAR(16)` required; `preference VARCHAR(16)` required (V4 initially checks FASTEST/CLEANEST/BALANCED); `result_payload TEXT` required; `created_at TIMESTAMP WITH TIME ZONE` required/default. Index `(user_id, created_at DESC)`.

## V5 — Phase 7 route suitability preference values

Replaces the route-calculation preference check with `ck_route_calculation_preference`, allowing FASTEST, CLEANEST, BALANCED, JOGGER, CYCLIST. No table/column addition.

## V6 — Phase 9 notifications

### `user_notification`

`id UUID` PK; `user_id UUID` required FK/cascade; `kind VARCHAR(32)` required and limited to `HIGH_POLLUTION_FORECAST` or `CLEANER_ALTERNATIVE`; `dedup_key VARCHAR(160)`, `title VARCHAR(140)`, `message VARCHAR(500)` required; `created_at TIMESTAMP WITH TIME ZONE` required/default; `read_at TIMESTAMP WITH TIME ZONE` nullable. Unique `(user_id, dedup_key)`; indexes `(user_id, created_at DESC)` and `(user_id, read_at, created_at DESC)`.

## Relationships and integrity

- Each user has one preference row and can own many places, saved routes, route history rows, calculations, and notifications. User deletion cascades to those rows.
- Environment rows, provider freshness, and forecasts reference a cell. The cell is not owned by an application user.
- Observation uniqueness prevents duplicate provider/cell/timestamp rows; forecast PK prevents duplicate cell/target/model; notification uniqueness deduplicates per user.
- Saved route and route calculation coordinates have range constraints. No PostGIS geometry column exists; route geometry in calculation payload is application JSON/text.
- Application ownership checks remain necessary even with foreign keys; FKs do not enforce that a caller owns a resource.

## Seed and generated data

The scheduler initializes the fixed demo cells and attempts seven days of deterministic generated AQI/weather/traffic history, then ingests at a configurable interval (default 15 minutes). The mock data is marked `generated=true`. Forecast rows carry `source_generated` plus provider/model identifiers and are predicted data. Values must not be presented as actual environmental readings. No real provider keys are currently required by the mock implementations.

## Migration inventory

| Version | File | Purpose |
|---|---|---|
| V1 | `V1__phase2_user_domain.sql` | Users, preferences, saved places/routes, history. |
| V2 | `V2__phase3_environmental_observations.sql` | Cells and pollution/weather/traffic observations. |
| V3 | `V3__phase3_provider_freshness_and_route_coordinates.sql` | Freshness state and saved-route coordinate checks. |
| V4 | `V4__phase5_forecasts_and_phase6_route_calculations.sql` | Forecast and route calculation persistence. |
| V5 | `V5__phase7_route_suitability_preferences.sql` | Expand route preference constraint. |
| V6 | `V6__phase9_user_notifications.sql` | In-app notifications. |

Fresh-database reproducibility should be checked with Flyway on PostgreSQL; do not edit released V1–V6 migrations for ordinary changes.
