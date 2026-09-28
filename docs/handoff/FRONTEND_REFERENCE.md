# CleanRoute Frontend Reference

## Stack and source layout

The frontend is React 19 + TypeScript + Vite 7. Vitest and Testing Library are used for tests. There is a single-page application rather than a multi-route React Router application.

| Path | Role |
|---|---|
| `frontend/src/main.tsx` | React root and application bootstrap. |
| `frontend/src/App.tsx` | Main screen/state orchestration: account state, dashboard, journey planner, environmental panels, route result/map, saved data and notifications. |
| `frontend/src/location/locationSearch.ts` | Provider-neutral location search contract and Photon adapter in the current working tree. |
| `frontend/src/location/LocationSearchField.tsx` | Debounced location suggestion field, selection and status UI. |
| `frontend/src/styles.css` | Application layout, visual system and responsive styles. |
| `frontend/src/vite-env.d.ts` | Vite environment variable types. |
| `frontend/src/App.test.tsx` | Main application interaction/render tests. |
| `frontend/src/test-setup.ts` | Vitest setup. |

The application has a CleanRoute green visual theme, responsive dashboard/planner layout, cards, status messages, and chart/map presentation. Component boundaries remain concentrated in `App.tsx`.

## API and authentication flow

The frontend API client sends JSON to `VITE_API_BASE_URL` (default `http://localhost:8080`). It uses the authentication token returned by register/login from browser storage for protected requests. A 401 is treated as an authentication/session error; callers show user-readable errors rather than server stack traces. The token is not a credential to copy into docs or logs.

The frontend calls existing backend endpoints for auth, profile/preferences, saved places/routes, route history, calculation, dashboard, notifications, current AQI/history, and forecasts as needed. See [API_REFERENCE.md](API_REFERENCE.md) for contracts. Do not add frontend scoring or route reordering: backend response order and metrics are authoritative.

## Authentication/account UI

Users can register or log in through the public auth endpoints. The resulting token supports account/profile preferences and owner-scoped dashboard, saved data, route calculation/history, and notification calls. A signed-out state is available for public environmental queries and place search, while protected operations require login. Password values are only sent to auth endpoints and are not rendered back from the API.

## Dashboard and environmental UI

The dashboard presents backend aggregation: current AQI, observed series/history, forecast values/quality, saved places/routes, route history, notification list and unread state. Charts use returned data; observed and predicted data types are distinct. Missing values are represented as unavailable rather than invented. Loading, empty, and error messages exist for primary data flows. Dashboard APIs are authenticated and owner-scoped even though some public AQI/forecast APIs also exist.

## Journey planner

The current source tree has uncommitted journey-planner changes, which should be preserved unless the user explicitly asks to change them. The planner provides:

- From/To human-readable search fields and a swap action.
- Search suggestions only after a minimum query length and 400 ms debounce; an actual suggestion must be selected before coordinates are accepted.
- Internal selected-location values: name/display context and numeric latitude/longitude.
- Driving/Walking/Cycling translated to backend `CAR`/`WALK`/`CYCLE` values.
- FASTEST/CLEANEST/BALANCED preference selection.
- Authenticated POST `/api/routes/calculate` using selected coordinates and existing route contract.
- Backend response alternatives displayed without frontend re-ranking; actual response geometry is used by Leaflet.
- Route cards show fields supplied by backend, including distance, duration, exposure, preference score/components, reasons, provider/generated provenance. Absent values are unavailable. Per-route AQI is not inferred.

The UI is map-planning inspired but is not Google Maps and does not claim real road-network directions. `MockRoutingProvider` supplies deterministic demo geometry.

## Location search and map

`LocationSearchProvider` is the UI-facing abstraction. The current Photon adapter calls the public Photon endpoint from the browser; it does not require an API key. Requests are debounced and stale requests are aborted/ignored. Empty query, pending, no-results, and network-failure states are handled. Photon may be unavailable/rate-limited; no backend geocoder exists. Search coordinates are inputs to the existing mock route provider, not geometry from Photon.

The map uses React Leaflet/Leaflet and OpenStreetMap tiles/attribution. It renders start/end markers and route lines from response geometry and fits bounds where valid geometry is available. When geometry is unavailable or unusable, the UI presents a fallback instead of drawing an invented path. OSM tiles and Photon require network access.

## Notifications and account data

The notification UI uses authenticated list/read APIs. Read state changes through the owner-checked backend endpoint. The frontend does not generate notification rules. Saved place/route CRUD and preferences use backend APIs and display server errors in the UI.

## State, loading, error and empty behavior

- Authentication state/token is retained client-side for API calls.
- Search query, selected origin/destination, travel mode/preference, selected route, and route result are page state.
- Server-backed dashboard, AQI/forecast, saved data, history and notification data are fetched from the backend.
- Async location queries are debounced and canceled/guarded against stale results.
- Main requests expose loading/error states; no-result/empty states explain next steps.
- Backend failure messages are presented in human language; internal exception traces are not shown.

## Environment and commands

| Variable | Purpose |
|---|---|
| `VITE_API_BASE_URL` | Backend origin used by frontend API calls; defaults to `http://localhost:8080`. |
| `VITE_PHOTON_API_URL` | Current location search endpoint override; defaults to the public Photon API URL in the current dirty work. No key is embedded. |

Commands from repository root:

```sh
cd frontend
npm install
npm test -- --run
npm run build
npm run dev
```

Vite defaults to port 5173. Compose publishes the frontend on host port 5173. `npm run build` creates a production bundle; the Compose frontend currently runs the Vite dev server, not a production static server.
