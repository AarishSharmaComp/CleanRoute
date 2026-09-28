# CleanRoute Phase History

The current implementation plan defines Phases 1–10. Git history contains six phase commits; Phases 5/6 share a commit and Phases 7–10 share a commit. Hashes below are abbreviated from local history inspected during handoff.

| Phase | Goal and implemented scope | Main implementation / API / schema | Tests and limitations | Commit |
|---|---|---|---|---|
| 1 | Establish repository, Java 21 Spring Boot/Maven backend, React/TypeScript/Vite frontend, PostgreSQL Compose stack, health endpoint. | `CleanRouteApplication`, `HealthController`, Docker/Compose; `GET /api/health`. | Foundation tests. Health now checks DB. | `c7c5c0b` |
| 2 | User domain, authentication and account-owned data. | User/preferences/saved place/route/history; JWT/BCrypt; auth, `/users/me`, places and saved/history APIs; V1. | API/security tests; owner from JWT principal. | `618e823` |
| 3 | Environmental observation data foundation. | Fixed cells, AQI/weather/traffic and routing abstractions/mocks, normalizer, scheduler, timeout/failure/freshness; AQI current/history; V2–V3. | Provider, persistence, scheduler/API tests. Exactly three deterministic demo cells; no real environmental provider. | `e9b25b5` |
| 4 | Pollution normalization and comparative score. | `PollutionEngine`, `PollutionScoreService`, `/api/pollution/score`; no schema change. | Null/fallback/validation tests. Score is an interval proxy, not a route rank or health assessment. | `364d87a` |
| 5 | Initial persisted forecast. | `ForecastProvider`, historical weighted baseline, forecast/history APIs, `pollution_forecast` in V4. | Historical dedup, nulls, provenance, validation tests. Deterministic historical forecast, not ML/external forecast. | `eb674be` |
| 6 | Route alternatives and pollution-aware route calculations. | `RoutingProvider`, `MockRoutingProvider`, `RouteService`, calculation/detail APIs, `route_calculation` in V4 and route history persistence. | Route alternatives, ranking and owner isolation tests. Demo geometries, not road-network directions. | `eb674be` |
| 7 | Travel preference/suitability. | FASTEST/CLEANEST/BALANCED plus JOGGER/CYCLIST scoring, configuration, optional metadata; V5 preference check. | Config/ranking tests. Mock lacks green/cycling/elevation metadata. | `6cb2be3` |
| 8 | Dashboard and frontend visualization journey. | React account/dashboard, AQI/forecast, Leaflet map, route planner/cards, status states; existing APIs. | Frontend tests/build. Current uncommitted work adds Photon human-readable search; provider is public/best effort. | `6cb2be3` |
| 9 | Dashboard persistence and notifications. | User-scoped aggregation, route history, in-app notification rules/list/read/dedup; V6. | Owner, notification and API tests. No email/push delivery. | `6cb2be3` |
| 10 | Hardening. | DB-aware health, CORS, validation/error handling/config/docs/tests. | Regression tests. No Phase 11 is defined. | `6cb2be3` |

## Dependencies

Phase 1 supplies runtime foundation. Phase 2 supplies identity and ownership. Phase 3 supplies observations and provider contracts. Phase 4 scores observations. Phase 5 supplies forecasts used by Phase 6 route exposure. Phase 6 supplies alternatives that Phase 7 ranks by suitability and Phase 9 can use to form alerts. Phase 8 displays those backend capabilities. Phase 10 hardens the assembled application.

## Commit timeline

```text
c7c5c0b Phase 1
  ↓
618e823 Phase 2
  ↓
e9b25b5 Phase 3
  ↓
364d87a Phase 4
  ↓
eb674be Phase 5 + Phase 6
  ↓
6cb2be3 Phase 7 + Phase 8 + Phase 9 + Phase 10
```

At handoff inspection, `main` and its local `origin/main` tracking ref both pointed at `6cb2be3e236f7cd14489e3187cb30dc9974d0dca`. No remote fetch was made in this audit, so this does not assert current live server state.
