# CleanRoute API Reference

This reflects current backend controllers. JSON field names follow the Java record/DTO names. `JWT` means `Authorization: Bearer <access-token>`. Examples use placeholders and representative shapes; optional fields can be omitted where noted. All API paths use `/api`.

## Common behavior

- Spring validation and controller checks return 400 for malformed/missing/invalid inputs.
- Missing owned resources generally return 404, including another user's ID.
- Protected endpoints require a valid, unexpired bearer JWT. Ownership comes from its principal, never a request `userId`.
- Generic unexpected errors are mapped without exposing stack traces/secrets.
- Timestamps are ISO-8601 with offset/UTC semantics.

## Public endpoints

| Method/path | Auth | Parameters/body and validation | Response and behavior |
|---|---|---|---|
| `GET /api/health` | No | None | 200 with application/database status when `SELECT 1` succeeds; 503 if DB check fails. |
| `POST /api/auth/register` | No | JSON `email`, `password`, `displayName`; valid email <=254, password 8–72 characters, display name <=100. | 201 safe user/token DTO. Duplicate email returns 409. Password is BCrypt-hashed and hash is never returned. |
| `POST /api/auth/login` | No | JSON `email`, `password`. | 200 token + safe user fields; invalid credentials return 401. |
| `GET /api/places/search` | No | Required `q`, trimmed length 3–200. | Up to six normalized results: `name`, `displayName`, `context`, `latitude`, `longitude`, `supportedArea`; generic 503 for provider failure. |
| `GET /api/aqi/current` | No | Optional `cell` (defaults to central demo cell in the current controller). | Current pollution observation with timestamp, provider/generated provenance and stale indicator. Unknown cell or no current row returns 404. `stale` becomes true after the configured 30-minute age threshold. Provider may be deterministic mock data or Open-Meteo data according to backend configuration. |
| `GET /api/aqi/history` | No | Required `cell`, `start`, `end`; optional `intervalMinutes` (15 only), `limit` (default 100, max 1000), `offset` (default 0). Start/end ISO timestamps; range <=90 days; cell must exist. | Bounded historical observation list; known cell with no matching records returns 200 and empty list. Invalid range/interval returns 400; unknown cell 404. |
| `GET /api/pollution/score` | No | Required `cell`, `at`, `durationSeconds`, `distanceMeters`, `mode`; 15-minute-aligned timestamp within allowed historical window, duration 1–86400, distance >0 and <=200000, supported travel mode. | One interval comparative pollution score with available/missing measurements, components, context, and provenance. Missing observation/cell 404; insufficient usable measurements 422; invalid parameters 400. It does not rank routes. |
| `GET /api/pollution/forecast` | No | Required `cell`, `from`; optional `intervalMinutes` (15), `count` (default 4, max 96). Future aligned start; bounded to 30 days. | Predicted series; records identify predicted/generated provenance, provider/model, quality and sample count. Invalid inputs 400; unknown cell/resource 404. |
| `GET /api/pollution/forecast/history` | No | Required `cell`, `from`, `to`; bounded history (<=90 days), optional `limit` (1–1000). | Stored forecast history for that cell/time range. Invalid values 400; unknown cell 404; empty valid range yields empty records. |

### Public request examples

```http
POST /api/auth/register
Content-Type: application/json

{"email":"person@example.test","password":"<8-72 character password>","displayName":"Example User"}
```

```http
GET /api/aqi/history?cell=demo-delhi-central&start=2026-09-28T00:00:00Z&end=2026-09-28T06:00:00Z&intervalMinutes=15&limit=100
```

```http
GET /api/pollution/score?cell=demo-delhi-central&at=2026-09-28T06:00:00Z&durationSeconds=1800&distanceMeters=5000&mode=WALK
```

Forecast example:

```http
GET /api/pollution/forecast?cell=demo-delhi-central&from=2026-09-28T12:00:00Z&intervalMinutes=15&count=4
```

The forecast timestamp must satisfy the endpoint's future/alignment rules relative to request time; example times are illustrative.

## Authenticated endpoints

| Method/path | Auth | Parameters/body and validation | Response and ownership |
|---|---|---|---|
| `GET /api/users/me` | JWT | None | Current principal's safe user profile and preferences. |
| `PUT /api/users/me` | JWT | Validated profile fields supported by DTO. | Updated current user's safe profile. No caller-supplied owner ID is trusted. |
| `PUT /api/users/me/preferences` | JWT | Supported travel mode/preference, notification flag, sensitivity 1–5. | Updated preference DTO. |
| `POST /api/places` | JWT | JSON place name, latitude/longitude within geographic bounds, optional address. | Created owner-scoped place (201). |
| `GET /api/places` | JWT | None | Only caller's places. |
| `DELETE /api/places/{id}` | JWT | UUID path ID. | Deletes caller's resource; missing/non-owned ID returns 404. |
| `POST /api/routes/save` | JWT | Validated saved route names/coordinates/mode/preference and optional metrics/geometry fields accepted by DTO. | Created saved route (201), owned by principal. |
| `GET /api/routes/saved` | JWT | None | Caller-owned saved routes only. |
| `DELETE /api/routes/saved/{id}` | JWT | UUID path ID. | Deletes caller-owned route only; otherwise 404. |
| `GET /api/routes/history` | JWT | None | Caller-owned route history, newest first. |
| `POST /api/routes/calculate` | JWT | JSON `origin` and `destination` with latitude/longitude, `mode`, `preference`; optional departure time. Supported modes include CAR/WALK/CYCLE/JOG; preferences FASTEST/CLEANEST/BALANCED/JOGGER/CYCLIST. Coordinates must be in range; departure must satisfy future rules. | 200 route calculation envelope; stores owner-scoped calculation/history. Backend order is the ranking source of truth. Invalid request 400. |
| `GET /api/routes/{id}` | JWT | UUID path ID. | Calculation only for owner; another user's/missing ID 404. |
| `GET /api/dashboard` | JWT | None | Aggregated dashboard for principal: central-cell environmental history/forecast plus caller-owned places/routes/history and notifications/unread count. |
| `GET /api/notifications` | JWT | None | Caller-owned notification list (bounded); includes read state. |
| `POST /api/notifications/{id}/read` | JWT | UUID path ID. | Marks any notification owned by caller as read, including older rows beyond list limit. Non-owned/missing ID 404. |

### Authenticated request examples

```http
GET /api/dashboard
Authorization: Bearer <access-token>
```

```http
POST /api/routes/calculate
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "origin":{"latitude":28.6139,"longitude":77.2090},
  "destination":{"latitude":28.5355,"longitude":77.2100},
  "mode":"WALK",
  "preference":"CLEANEST"
}
```

Route response shape (values are illustrative, not measurements):

```json
{
  "id":"<calculation-uuid>",
  "departureAt":"<ISO-8601 timestamp>",
  "mode":"WALK",
  "preference":"CLEANEST",
  "alternatives":[{
    "alternativeId":"<provider alternative id>",
    "rank":1,
    "provider":"mock-routing",
    "generated":true,
    "geometry":[{"latitude":28.61,"longitude":77.20}],
    "distanceMeters":1234.0,
    "durationSeconds":900,
    "expectedPollutionExposure":null,
    "forecastQualityScore":null,
    "preferenceScore":null,
    "scoreComponents":{},
    "reasons":[]
  }],
  "generated":true,
  "limitation":"<response limitation text>"
}
```

The example intentionally shows optional numeric values as `null`; clients must render unavailable data as unavailable. Do not treat this sample as a guaranteed route output. Exact DTO definitions in `route/domain/RoutePlanningModels.java` are authoritative. Phase 13 route alternatives may additionally contain `environmentalCoverage` (`fixed-cell`, `complete`, `partial`, or `unavailable`), `observationSource`, `sampledPointCount`, `availableSampleCount`, and `unavailableSampleCount`; these are additive provenance and coverage fields.

## Endpoint groups not present

There is no weather/traffic query endpoint, arbitrary geographic-cell creation/search API, route provider credentials endpoint, notification delivery endpoint, or separate forecast-job API. Place search is proxied through the backend provider abstraction; Photon-specific response structures are not exposed by the API.

## Environmental provenance

Pollution API records preserve `provider` and `generated`. `mock-demo-aqi`/`mock` observations are deterministic generated demo values. `open-meteo-air-quality` observations are external model-backed values with `generated=false`; the current Open-Meteo adapter provides PM10, PM2.5, CO, NO2, SO2, and O3, with CleanRoute units of µg/m³ except CO in mg/m³. AQI is null for these records because the provider's documented European and U.S. AQI values are not treated as compatible with the existing provider-native AQI field. Missing measurements remain null. The application still stores observations only for its three fixed Delhi cells; selecting Open-Meteo does not create arbitrary coordinate coverage.
