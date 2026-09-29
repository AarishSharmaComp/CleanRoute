import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis } from 'recharts'
import 'leaflet/dist/leaflet.css'
import { LocationSearchField, type LocationInput } from './location/LocationSearchField'
import { BackendLocationSearchProvider } from './location/locationSearch'
import type { LocationSearchResult } from './location/locationSearch'

const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const locationProvider = new BackendLocationSearchProvider()
const cells = [
  { id: 'demo-delhi-central', label: 'Central Delhi', latitude: 28.6139, longitude: 77.2090 },
  { id: 'demo-delhi-south', label: 'South Delhi', latitude: 28.5355, longitude: 77.2100 },
  { id: 'demo-delhi-north', label: 'North Delhi', latitude: 28.7041, longitude: 77.1025 },
]

function isInDemoCoverage(point: Point): boolean {
  const radius = 35000
  return cells.some(cell => {
    const radians = Math.PI / 180
    const dLat = (point.latitude - cell.latitude) * radians
    const dLon = (point.longitude - cell.longitude) * radians
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(cell.latitude * radians) * Math.cos(point.latitude * radians) * Math.sin(dLon / 2) ** 2
    return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) <= radius
  })
}

type Point = { latitude: number; longitude: number }
type Aqi = { cellId: string; observedAt: string; aqi: number | null; pm25: number | null; generated: boolean; stale?: boolean }
type Forecast = { dataType: string; forecast: { targetAt: string; aqi: number | null; pm25: number | null; quality: string; qualityScore: number; sourceGenerated: boolean } }
type Alternative = { alternativeId: string; rank: number; provider?: string; generated: boolean; geometry?: Point[] | null; distanceMeters: number | null; durationSeconds: number | null; expectedPollutionExposure: number | null; forecastQualityScore?: number | null; preferenceScore: number | null; scoreComponents?: Record<string, number | null> | null; reasons?: string[] | null }
type Calculation = { id: string; preference: string; alternatives: Alternative[]; generated?: boolean; limitation?: string }
type SavedRoute = { id: string; originName: string; destinationName: string; travelMode: string; routePreference: string }
type SavedPlace = { id: string; name: string; latitude: number; longitude: number; address: string | null }
type RouteHistory = { id: string; originName: string; destinationName: string; travelMode: string; routePreference: string; pollutionScore: number | null; estimatedTravelTimeSeconds: number | null; distanceMeters: number | null; createdAt: string }
type UserNotification = { id: string; kind: string; title: string; message: string; createdAt: string; readAt: string | null }
type Preferences = { preferredTravelMode: string; routePreference: string; notificationsEnabled: boolean; pollutionSensitivity: number }
type Dashboard = { savedPlaces: SavedPlace[]; savedRoutes: SavedRoute[]; routeHistory: RouteHistory[]; notifications: UserNotification[]; unreadNotificationCount: number }
type ChartPoint = { time: string; observedAqi?: number | null; forecastAqi?: number | null; generated?: boolean }

function api<T>(path: string, token?: string | null, init: RequestInit = {}): Promise<T> {
  return fetch(`${API}${path}`, {
    ...init,
    headers: { ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
  }).then(async response => {
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      if (response.status === 401) throw new Error('Your session has expired. Please sign in again.')
      throw new Error(body?.message || `Request failed (${response.status})`)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }).catch(error => {
    if (error instanceof TypeError) throw new Error('CleanRoute could not reach the backend. Check that it is running and try again.')
    throw error
  })
}

function validGeometry(route: Alternative): Point[] {
  if (!Array.isArray(route.geometry) || route.geometry.length < 2) return []
  return route.geometry.every(point => Number.isFinite(point.latitude) && point.latitude >= -90 && point.latitude <= 90
    && Number.isFinite(point.longitude) && point.longitude >= -180 && point.longitude <= 180) ? route.geometry : []
}

function FitMap({ route, origin, destination }: { route: Alternative | null; origin: Point | null; destination: Point | null }) {
  const map = useMap()
  useEffect(() => {
    const routePoints = route ? validGeometry(route).map(point => [point.latitude, point.longitude] as [number, number]) : []
    const endpointPoints = [origin, destination].filter((point): point is Point => point !== null).map(point => [point.latitude, point.longitude] as [number, number])
    const points = [...routePoints, ...endpointPoints]
    if (points.length > 1) map.fitBounds(points, { padding: [36, 36], maxZoom: 13 })
    else if (points.length === 1) map.setView(points[0], 12)
  }, [map, route, origin, destination])
  return null
}

const cellIcon = L.divIcon({ className: 'cell-marker', html: '<span></span>', iconSize: [16, 16], iconAnchor: [8, 8] })
const originIcon = L.divIcon({ className: 'journey-marker origin-marker', html: '<span>F</span>', iconSize: [28, 28], iconAnchor: [14, 14] })
const destinationIcon = L.divIcon({ className: 'journey-marker destination-marker', html: '<span>T</span>', iconSize: [28, 28], iconAnchor: [14, 14] })

const emptyLocation: LocationInput = { query: '', selected: null }
const rankingModes = [
  { value: 'FASTEST', icon: '⚡', title: 'Fastest', detail: 'Prioritizes travel duration.' },
  { value: 'CLEANEST', icon: '🌿', title: 'Cleanest', detail: 'Prioritizes lower pollution exposure.' },
  { value: 'BALANCED', icon: '⚖', title: 'Balanced', detail: 'Balances travel time and pollution exposure.' },
]
const travelModes = [
  { value: 'CAR', icon: '🚗', label: 'Driving' },
  { value: 'WALK', icon: '🚶', label: 'Walking' },
  { value: 'CYCLE', icon: '🚴', label: 'Cycling' },
]

function displayValue(value: number | null | undefined, digits = 0): string {
  return value == null || !Number.isFinite(value) ? 'Data unavailable' : value.toFixed(digits)
}

function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('cleanroute.token'))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [registering, setRegistering] = useState(false)
  const [current, setCurrent] = useState<Aqi | null>(null)
  const [chart, setChart] = useState<ChartPoint[]>([])
  const [forecasts, setForecasts] = useState<Forecast[]>([])
  const [savedRoutes, setSavedRoutes] = useState<SavedRoute[]>([])
  const [savedPlaces, setSavedPlaces] = useState<SavedPlace[]>([])
  const [routeHistory, setRouteHistory] = useState<RouteHistory[]>([])
  const [notifications, setNotifications] = useState<UserNotification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [preferences, setPreferences] = useState<Preferences>({ preferredTravelMode: 'WALK', routePreference: 'BALANCED', notificationsEnabled: true, pollutionSensitivity: 3 })
  const [calculation, setCalculation] = useState<Calculation | null>(null)
  const [origin, setOrigin] = useState<LocationInput>(emptyLocation)
  const [destination, setDestination] = useState<LocationInput>(emptyLocation)
  const [preference, setPreference] = useState('BALANCED')
  const [mode, setMode] = useState('WALK')
  const [selectedRouteId, setSelectedRouteId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadEnvironment = useCallback(async () => {
    setLoading(true)
    try {
      const cell = cells[0].id
      const now = new Date()
      const start = new Date(now.getTime() - 24 * 60 * 60 * 1000)
      const future = new Date(Math.ceil((now.getTime() + 1000) / 900000) * 900000)
      const [latest, history, predicted] = await Promise.all([
        api<Aqi>(`/api/aqi/current?cell=${cell}`),
        api<Aqi[]>(`/api/aqi/history?cell=${cell}&start=${encodeURIComponent(start.toISOString())}&end=${encodeURIComponent(now.toISOString())}&interval=15&limit=96`),
        api<Forecast[]>(`/api/pollution/forecast?cell=${cell}&from=${encodeURIComponent(future.toISOString())}&interval=15&count=8`),
      ])
      setCurrent(latest); setForecasts(predicted)
      const points: ChartPoint[] = history.map(row => ({ time: row.observedAt, observedAqi: row.aqi, generated: row.generated }))
      for (const row of predicted) points.push({ time: row.forecast.targetAt, forecastAqi: row.forecast.aqi, generated: row.forecast.sourceGenerated })
      setChart(points.sort((a, b) => a.time.localeCompare(b.time)))
      setError('')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not load environmental data') }
    finally { setLoading(false) }
  }, [])

  const loadAccountData = useCallback(async (applyPlannerDefaults = false) => {
    if (!token) { setSavedRoutes([]); setSavedPlaces([]); setRouteHistory([]); setNotifications([]); setUnreadCount(0); return }
    try {
      const [dashboard, profile] = await Promise.all([
        api<Dashboard>('/api/dashboard', token), api<{ preferences: Preferences }>('/api/users/me', token),
      ])
      setSavedRoutes(dashboard.savedRoutes); setSavedPlaces(dashboard.savedPlaces)
      setRouteHistory(dashboard.routeHistory); setNotifications(dashboard.notifications)
      setUnreadCount(dashboard.unreadNotificationCount); setPreferences(profile.preferences)
      if (applyPlannerDefaults) {
        setPreference(['FASTEST', 'CLEANEST', 'BALANCED'].includes(profile.preferences.routePreference) ? profile.preferences.routePreference : 'BALANCED')
        setMode(['CAR', 'WALK', 'CYCLE'].includes(profile.preferences.preferredTravelMode) ? profile.preferences.preferredTravelMode : 'WALK')
      }
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not load your account dashboard') }
  }, [token])

  useEffect(() => { void loadEnvironment() }, [loadEnvironment])
  useEffect(() => { void loadAccountData(true) }, [loadAccountData])

  async function authenticate(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const body = registering ? { email, password, displayName } : { email, password }
      const result = await api<{ token: string }>(`/api/auth/${registering ? 'register' : 'login'}`, null, { method: 'POST', body: JSON.stringify(body) })
      localStorage.setItem('cleanroute.token', result.token); setToken(result.token); setPassword(''); setNotice('Signed in successfully')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not sign in') }
    finally { setBusy(false) }
  }

  function signOut() {
    localStorage.removeItem('cleanroute.token'); setToken(null); setSavedRoutes([]); setCalculation(null); setSelectedRouteId(null); setNotice('Signed out')
  }

  async function calculateRoute(event: FormEvent) {
    event.preventDefault()
    const from = origin.selected
    const to = destination.selected
    if (!from || !to) { setError('Choose a starting point and destination from the search suggestions.'); return }
    if (!token) { setError('Sign in to calculate and save routes'); return }
    setBusy(true); setError(''); setNotice('')
    try {
      const result = await api<Calculation>('/api/routes/calculate', token, {
        method: 'POST', body: JSON.stringify({ origin: { latitude: from.latitude, longitude: from.longitude }, destination: { latitude: to.latitude, longitude: to.longitude }, mode, preference }),
      })
      setCalculation(result)
      setSelectedRouteId(result.alternatives[0]?.alternativeId ?? null)
      setNotice(result.alternatives.length ? 'Route alternatives are ready' : 'No routes were returned for these locations')
      void loadAccountData()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'CleanRoute could not calculate routes right now. Please try again.') }
    finally { setBusy(false) }
  }

  async function saveRoute(alternative: Alternative) {
    if (!token || !origin.selected || !destination.selected) return
    setBusy(true); setError(''); setNotice('')
    try {
      await api('/api/routes/save', token, { method: 'POST', body: JSON.stringify({
        originName: origin.selected.displayName, originLatitude: origin.selected.latitude, originLongitude: origin.selected.longitude,
        destinationName: destination.selected.displayName, destinationLatitude: destination.selected.latitude, destinationLongitude: destination.selected.longitude,
        geometryPolyline: JSON.stringify(alternative.geometry ?? []), travelMode: mode, routePreference: preference,
        pollutionScore: alternative.expectedPollutionExposure, estimatedTravelTimeSeconds: alternative.durationSeconds, distanceMeters: alternative.distanceMeters,
      }) })
      await loadAccountData(); setNotice('Route saved to your account')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save route') }
    finally { setBusy(false) }
  }

  async function removeSavedRoute(id: string) {
    if (!token) return
    try { await api(`/api/routes/saved/${id}`, token, { method: 'DELETE' }); await loadAccountData() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not remove route') }
  }

  async function savePlace(name: string, point: Point) {
    if (!token) return
    try {
      await api('/api/places', token, { method: 'POST', body: JSON.stringify({ name, latitude: point.latitude, longitude: point.longitude }) })
      await loadAccountData(); setNotice(`${name} saved to your places`)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save place') }
  }

  async function removeSavedPlace(id: string) {
    if (!token) return
    try { await api(`/api/places/${id}`, token, { method: 'DELETE' }); await loadAccountData() }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not remove place') }
  }

  async function savePreferences(event: FormEvent) {
    event.preventDefault()
    if (!token) return
    try {
      const updated = await api<{ preferences: Preferences }>('/api/users/me/preferences', token, { method: 'PUT', body: JSON.stringify(preferences) })
      setPreferences(updated.preferences)
      if (['FASTEST', 'CLEANEST', 'BALANCED'].includes(updated.preferences.routePreference)) setPreference(updated.preferences.routePreference)
      setMode(['CAR', 'WALK', 'CYCLE'].includes(updated.preferences.preferredTravelMode) ? updated.preferences.preferredTravelMode : 'WALK')
      setNotice('Preferences saved')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not save preferences') }
  }

  async function markNotificationRead(id: string) {
    if (!token) return
    try {
      await api(`/api/notifications/${id}/read`, token, { method: 'POST' })
      setNotifications(items => items.map(item => item.id === id ? { ...item, readAt: new Date().toISOString() } : item))
      setUnreadCount(count => Math.max(0, count - 1))
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not update notification') }
  }

  function useSavedPlace(place: SavedPlace, target: 'origin' | 'destination') {
    const result: LocationSearchResult = { name: place.name, displayName: place.address || place.name, context: place.address || '', latitude: place.latitude, longitude: place.longitude, supportedArea: isInDemoCoverage(place) }
    const value = { query: result.displayName, selected: result }
    if (target === 'origin') setOrigin(value); else setDestination(value)
  }

  const displayedRoutes = calculation?.alternatives ?? []
  const activeRoute = displayedRoutes.find(route => route.alternativeId === selectedRouteId) ?? displayedRoutes[0] ?? null
  const selectedOrigin = origin.selected
  const selectedDestination = destination.selected
  const environmentalCoverageAvailable = Boolean(selectedOrigin?.supportedArea && selectedDestination?.supportedArea)

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="CleanRoute home"><span className="brand-mark">C</span><span>CleanRoute</span></a>
        <div className="topbar-actions"><span className="demo-label">DEMO ENVIRONMENT · GENERATED DATA</span>{token && <button className="button quiet" onClick={signOut}>Sign out</button>}</div>
      </header>

      <section className="hero" id="top">
        <div><p className="eyebrow">POLLUTION-AWARE ROUTE PLANNING</p><h1>Choose a clearer way through the city.</h1><p>Compare route alternatives against the latest available air-quality observations and clearly labeled forecasts.</p></div>
        <div className="hero-chip"><span className="status-dot"/> Mock providers active</div>
      </section>

      {(error || notice) && <div className={error ? 'banner error-banner' : 'banner success-banner'} role={error ? 'alert' : 'status'}>{error || notice}<button onClick={() => { setError(''); setNotice('') }} aria-label="Dismiss">×</button></div>}

      <section className="dashboard-grid conditions-grid" aria-label="Current conditions">
        <article className="panel current-panel">
          <div className="panel-heading"><div><p className="eyebrow">CURRENT AIR QUALITY</p><h2>Central Delhi</h2></div><span className={current?.stale ? 'pill stale' : 'pill'}>{current?.stale ? 'Stale' : 'Latest'}</span></div>
          {loading ? <p className="placeholder">Loading latest observation…</p> : current ? <div className="aqi-reading"><strong>{current.aqi ?? '—'}</strong><span>AQI index</span><small>{current.pm25 == null ? 'PM2.5 unavailable' : `PM2.5 ${current.pm25.toFixed(1)} µg/m³`}</small></div> : <p className="placeholder">No current observation is available.</p>}
          <p className="provenance">{current?.generated ? 'Generated demo observation' : current ? 'Provider observation' : 'Data source unavailable'}{current?.observedAt ? ` · ${new Date(current.observedAt).toLocaleString()}` : ''}</p>
        </article>
        <article className="panel forecast-panel">
          <div className="panel-heading"><div><p className="eyebrow">NEXT 2 HOURS</p><h2>Observed and forecast AQI</h2></div><button className="button quiet" onClick={() => void loadEnvironment()} disabled={loading}>Refresh</button></div>
          <div className="chart-wrap">{loading ? <p className="placeholder">Loading observations and forecast…</p> : chart.length ? <ResponsiveContainer width="100%" height="100%"><LineChart data={chart} margin={{ top: 8, right: 12, left: -15, bottom: 5 }}>
            <CartesianGrid stroke="#e9eee8" strokeDasharray="3 3"/><XAxis dataKey="time" tickFormatter={value => new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} minTickGap={28}/><YAxis width={42}/><ChartTooltip labelFormatter={value => new Date(String(value)).toLocaleString()}/><Legend/>
            <Line type="monotone" dataKey="observedAqi" name="Observed AQI" stroke="#2f7d57" strokeWidth={2} dot={false} connectNulls={false}/><Line type="monotone" dataKey="forecastAqi" name="Predicted AQI" stroke="#d18a24" strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3 }} connectNulls={false}/>
          </LineChart></ResponsiveContainer> : <p className="empty-state">No chart data is available for this cell.</p>}</div>
          <div className="chart-legend-note"><span><i className="legend-observed"/> Observed</span><span><i className="legend-predicted"/> Predicted</span><small>{forecasts.length ? `${forecasts.length} predictions · ${forecasts[0].forecast.quality.toLowerCase()} quality` : 'Forecast baseline'} · not validated health guidance</small></div>
          <p className="provenance">Current AQI and forecast are city-cell context, not a per-route AQI measurement.</p>
        </article>
      </section>

      <section className="planning-grid" aria-label="Route planning">
        <article className="panel planner-panel">
          <div className="panel-heading"><div><p className="eyebrow">PLAN A JOURNEY</p><h2>From here to there</h2></div><span className="pill">{token ? 'Ready to plan' : 'Sign-in required'}</span></div>
          <form className="route-form journey-form" onSubmit={calculateRoute}>
            <div className="journey-locations">
              <LocationSearchField label="From" placeholder="Search starting point" value={origin} provider={locationProvider} onChange={setOrigin}/>
              <button className="swap-button" type="button" aria-label="Swap origin and destination" onClick={() => { setOrigin(destination); setDestination(origin) }}>⇅</button>
              <LocationSearchField label="To" placeholder="Search destination" value={destination} provider={locationProvider} onChange={setDestination}/>
            </div>
            {(selectedOrigin || selectedDestination) && !environmentalCoverageAvailable && <p className="provenance" role="status">Environmental coverage is available only near the three Delhi demo cells. Global place search works, but pollution exposure for this journey is unavailable.</p>}
            <fieldset className="choice-field"><legend>Travel mode</legend><div className="segmented-control" role="group" aria-label="Travel mode">
              {travelModes.map(item => <button key={item.value} type="button" className={mode === item.value ? 'segment active' : 'segment'} aria-pressed={mode === item.value} onClick={() => setMode(item.value)}><span>{item.icon}</span>{item.label}</button>)}
            </div></fieldset>
            <fieldset className="choice-field"><legend>Route preference</legend><div className="segmented-control preference-segments" role="group" aria-label="Route preference">
              {rankingModes.map(item => <button key={item.value} type="button" className={preference === item.value ? 'segment active' : 'segment'} aria-pressed={preference === item.value} onClick={() => setPreference(item.value)}><span>{item.icon}</span>{item.title}</button>)}
            </div><p className="choice-description">{rankingModes.find(item => item.value === preference)?.detail}</p></fieldset>
            <div className="place-actions planner-place-actions">
              <button className="button quiet" type="button" disabled={!token || !selectedOrigin || busy} onClick={() => selectedOrigin && void savePlace(selectedOrigin.name, selectedOrigin)}>Save origin place</button>
              <button className="button quiet" type="button" disabled={!token || !selectedDestination || busy} onClick={() => selectedDestination && void savePlace(selectedDestination.name, selectedDestination)}>Save destination</button>
            </div>
            <button className="button primary full find-routes" disabled={busy || !selectedOrigin || !selectedDestination}>{busy ? 'Calculating pollution-aware routes…' : 'Find clean routes'}</button>
            {!selectedOrigin || !selectedDestination ? <p className="muted small">Choose both locations from the search suggestions before calculating.</p> : null}
          </form>
          {!token && <form className="auth-form sign-in-inline" onSubmit={authenticate}>
            <p className="muted">Sign in to calculate, save, and review routes. Environmental observations remain available without an account.</p>
            {registering && <label>Display name<input value={displayName} onChange={event => setDisplayName(event.target.value)} required maxLength={100}/></label>}
            <label>Email<input type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254}/></label>
            <label>Password<input type="password" autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} required minLength={8} maxLength={72}/></label>
            <button className="button primary full" disabled={busy}>{busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}</button>
            <button className="text-button" type="button" onClick={() => setRegistering(!registering)}>{registering ? 'Already have an account? Sign in' : 'New to CleanRoute? Create account'}</button>
          </form>}
        </article>

        <article className="panel map-panel">
          <div className="panel-heading map-heading"><div><p className="eyebrow">CITY MAP</p><h2>Journey and air-quality context</h2></div><span className="map-credit">© OpenStreetMap contributors</span></div>
          <div className="map-wrap"><MapContainer center={[28.6139, 77.2090]} zoom={11} scrollWheelZoom className="leaflet-map">
            <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/>
            {cells.map(cell => <Marker key={cell.id} position={[cell.latitude, cell.longitude]} icon={cellIcon}><Tooltip>{cell.label} · fixed demo cell</Tooltip></Marker>)}
            {selectedOrigin && <Marker position={[selectedOrigin.latitude, selectedOrigin.longitude]} icon={originIcon}><Tooltip>From · {selectedOrigin.displayName}</Tooltip></Marker>}
            {selectedDestination && <Marker position={[selectedDestination.latitude, selectedDestination.longitude]} icon={destinationIcon}><Tooltip>To · {selectedDestination.displayName}</Tooltip></Marker>}
            {displayedRoutes.map(route => {
              const geometry = validGeometry(route)
              if (!geometry.length) return null
              const selected = route.alternativeId === activeRoute?.alternativeId
               return <Polyline key={route.alternativeId} positions={geometry.map(point => [point.latitude, point.longitude] as [number, number])}
                 pathOptions={{ color: selected ? '#207b50' : '#849b8a', weight: selected ? 7 : 4, opacity: selected ? .95 : .32 }}><Tooltip>{environmentalCoverageAvailable ? `${route.alternativeId} · modeled exposure ${displayValue(route.expectedPollutionExposure, 1)}` : `${route.alternativeId} · environmental coverage unavailable`}</Tooltip></Polyline>
            })}
            <FitMap route={activeRoute} origin={selectedOrigin} destination={selectedDestination}/>
          </MapContainer>{displayedRoutes.length > 0 && !displayedRoutes.some(route => validGeometry(route).length) && <div className="map-fallback" role="status">Route geometry is unavailable. Route details are shown below.</div>}</div>
          <div className="map-legend"><span><i className="legend-clean"/> Selected route</span><span><i className="legend-other"/> Other alternatives</span><span><i className="legend-cell"/> Fixed demo AQI cells</span></div>
          <p className="provenance">Route lines use geometry returned by the routing provider. Demo routes are deterministic generated data, not Google Maps directions.</p>
        </article>
      </section>

      <section className="results-section" aria-label="Route alternatives">
        <div className="section-title"><div><p className="eyebrow">COMPARISON</p><h2>{calculation ? `${calculation.preference} alternatives` : 'Route alternatives'}</h2></div><span className="muted">{calculation ? (calculation.generated ? 'Generated route alternatives · backend ranking order' : 'Provider routes · backend ranking order') : 'Compare travel time with modeled pollution exposure'}</span></div>
        {displayedRoutes.length ? <div className="route-cards">{displayedRoutes.map(route => {
          const components = Object.entries(route.scoreComponents ?? {})
          const geometryAvailable = validGeometry(route).length > 0
          return <article className={`route-card ${route.rank === 1 ? 'top-route' : ''} ${activeRoute?.alternativeId === route.alternativeId ? 'selected-route-card' : ''}`} key={route.alternativeId}>
            <div className="route-card-top"><span className="route-rank">{route.rank}</span><div><h3>{route.alternativeId.replaceAll('-', ' ')}</h3><span className="route-provider">{calculation?.preference} · {route.generated ? 'Generated demo route' : route.provider || 'Provider route'}</span></div><strong className="route-score">{displayValue(route.preferenceScore)}<small>preference score</small></strong></div>
             <div className="route-stats"><span><b>{route.distanceMeters == null ? 'Data unavailable' : `${(route.distanceMeters / 1000).toFixed(1)} km`}</b> distance</span><span><b>{route.durationSeconds == null ? 'Data unavailable' : `${Math.round(route.durationSeconds / 60)} min`}</b> duration</span><span><b>{environmentalCoverageAvailable ? displayValue(route.expectedPollutionExposure, 1) : 'Data unavailable'}</b> {environmentalCoverageAvailable ? 'modeled exposure' : 'environmental coverage'}</span></div>
             <dl className="route-details"><div><dt>Route AQI</dt><dd>Data unavailable</dd></div><div><dt>Forecast quality</dt><dd>{environmentalCoverageAvailable && route.forecastQualityScore != null ? `${route.forecastQualityScore}/100` : 'Data unavailable'}</dd></div><div><dt>Geometry</dt><dd>{geometryAvailable ? 'Available' : 'Data unavailable'}</dd></div></dl>
            {components.length > 0 && <div className="score-components"><strong>Score components</strong><ul>{components.map(([name, value]) => <li key={name}><span>{name.replaceAll('_', ' ')}</span><b>{displayValue(value, 1)}</b></li>)}</ul></div>}
             {route.reasons?.length && environmentalCoverageAvailable ? <div className="route-explanation"><strong>Why this route?</strong><p>{route.reasons.join(' ')}</p></div> : <p className="route-explanation">{environmentalCoverageAvailable ? 'Data unavailable: no route explanation was returned.' : 'Environmental coverage is unavailable outside the supported Delhi demo area.'}</p>}
            <div className="route-card-actions"><button className="button outline" onClick={() => setSelectedRouteId(route.alternativeId)} aria-pressed={activeRoute?.alternativeId === route.alternativeId}>Show on map</button><button className="button outline" onClick={() => void saveRoute(route)} disabled={!token || busy || !selectedOrigin || !selectedDestination}>Save route</button></div>
          </article>
        })}</div> : <div className="empty-panel"><strong>Plan a cleaner journey</strong><p>Enter your starting point and destination to compare routes using travel time and pollution exposure.</p>{calculation && !displayedRoutes.length && <span>No route alternatives were returned. Try different locations or check again later.</span>}</div>}
        {calculation?.limitation && <p className="provenance">{calculation.limitation}</p>}
      </section>

      {token && <section className="account-grid" aria-label="Account dashboard">
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">YOUR PLACES</p><h2>Saved places</h2></div><span className="pill">{savedPlaces.length}</span></div>
          {savedPlaces.length ? <ul className="saved-list">{savedPlaces.map(place => <li key={place.id}><div><strong>{place.name}</strong><span>{place.address || `${place.latitude.toFixed(4)}, ${place.longitude.toFixed(4)}`}</span><div className="place-actions"><button className="text-button" onClick={() => useSavedPlace(place, 'origin')}>Use as origin</button><button className="text-button" onClick={() => useSavedPlace(place, 'destination')}>Use as destination</button></div></div><button className="text-button danger-text" onClick={() => void removeSavedPlace(place.id)}>Remove</button></li>)}</ul> : <p className="empty-state">Save an origin or destination to reuse it.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">YOUR ROUTES</p><h2>Saved routes</h2></div><span className="pill">{savedRoutes.length} saved</span></div>
          {savedRoutes.length ? <ul className="saved-list">{savedRoutes.map(route => <li key={route.id}><div><strong>{route.originName} → {route.destinationName}</strong><span>{route.travelMode} · {route.routePreference}</span></div><button className="text-button danger-text" onClick={() => void removeSavedRoute(route.id)}>Remove</button></li>)}</ul> : <p className="empty-state">Saved routes will show up here.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">RECENT CALCULATIONS</p><h2>Route history</h2></div><span className="pill">{routeHistory.length}</span></div>
          {routeHistory.length ? <ul className="saved-list">{routeHistory.slice(0, 10).map(route => <li key={route.id}><div><strong>{route.originName} → {route.destinationName}</strong><span>{route.travelMode} · {route.routePreference} · {route.distanceMeters == null ? 'Data unavailable' : `${(route.distanceMeters / 1000).toFixed(1)} km`}</span></div></li>)}</ul> : <p className="empty-state">Calculated routes will appear here.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">ALERTS</p><h2>Notifications</h2></div><span className="pill">{unreadCount} unread</span></div>
          {notifications.length ? <ul className="saved-list">{notifications.map(item => <li key={item.id}><div><strong>{item.title}</strong><span>{item.message}</span></div>{!item.readAt && <button className="text-button" onClick={() => void markNotificationRead(item.id)}>Mark read</button>}</li>)}</ul> : <p className="empty-state">No alerts yet. Forecast and route checks can create in-app alerts.</p>}
        </article>
        <article className="panel saved-panel preferences-panel"><div className="panel-heading"><div><p className="eyebrow">ACCOUNT SETTINGS</p><h2>Preferences</h2></div></div>
          <form className="route-form" onSubmit={savePreferences}>
            <label>Preferred route ranking<select value={preferences.routePreference} onChange={event => setPreferences({ ...preferences, routePreference: event.target.value })}>{['FASTEST', 'CLEANEST', 'BALANCED', 'JOGGER', 'CYCLIST'].map(value => <option key={value}>{value}</option>)}</select></label>
            <label>Preferred travel mode<select value={preferences.preferredTravelMode} onChange={event => setPreferences({ ...preferences, preferredTravelMode: event.target.value })}>{['WALK', 'CAR', 'CYCLE', 'JOG'].map(value => <option key={value}>{value}</option>)}</select></label>
            <label className="check-label"><input type="checkbox" checked={preferences.notificationsEnabled} onChange={event => setPreferences({ ...preferences, notificationsEnabled: event.target.checked })}/> Enable in-app notifications</label>
            <label>Pollution sensitivity · {preferences.pollutionSensitivity}<input type="range" min="1" max="5" value={preferences.pollutionSensitivity} onChange={event => setPreferences({ ...preferences, pollutionSensitivity: Number(event.target.value) })}/></label>
            <button className="button primary" type="submit">Save preferences</button>
          </form>
        </article>
      </section>}

      <footer className="app-footer"><span>CleanRoute</span><span>Generated demo data is not a real-time air-quality report.</span></footer>
    </main>
  )
}

export default App
