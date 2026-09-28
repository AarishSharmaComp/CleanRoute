import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis } from 'recharts'
import 'leaflet/dist/leaflet.css'

const API = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const cells = [
  { id: 'demo-delhi-central', label: 'Central Delhi', latitude: 28.6139, longitude: 77.2090 },
  { id: 'demo-delhi-south', label: 'South Delhi', latitude: 28.5355, longitude: 77.2100 },
  { id: 'demo-delhi-north', label: 'North Delhi', latitude: 28.7041, longitude: 77.1025 },
]

type Point = { latitude: number; longitude: number }
type Aqi = { cellId: string; observedAt: string; aqi: number | null; pm25: number | null; generated: boolean; stale?: boolean }
type Forecast = { dataType: string; forecast: { targetAt: string; aqi: number | null; pm25: number | null; quality: string; qualityScore: number; sourceGenerated: boolean } }
type Alternative = { alternativeId: string; rank: number; generated: boolean; geometry: Point[]; distanceMeters: number; durationSeconds: number; expectedPollutionExposure: number; preferenceScore: number; reasons: string[] }
type Calculation = { id: string; preference: string; alternatives: Alternative[] }
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
      throw new Error(body?.message || `Request failed (${response.status})`)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  })
}

function routeColor(exposure: number) {
  if (exposure < 25) return '#208354'
  if (exposure < 55) return '#d99a23'
  return '#c64f48'
}

function FitMap({ routes }: { routes: Alternative[] }) {
  const map = useMap()
  useEffect(() => {
    const points = routes.flatMap(route => route.geometry.map(point => [point.latitude, point.longitude] as [number, number]))
    if (points.length) map.fitBounds(points, { padding: [36, 36], maxZoom: 13 })
  }, [map, routes])
  return null
}

const cellIcon = L.divIcon({ className: 'cell-marker', html: '<span></span>', iconSize: [16, 16], iconAnchor: [8, 8] })

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
  const [origin, setOrigin] = useState<Point>({ latitude: 28.6139, longitude: 77.2090 })
  const [destination, setDestination] = useState<Point>({ latitude: 28.7041, longitude: 77.1025 })
  const [preference, setPreference] = useState('BALANCED')
  const [mode, setMode] = useState('WALK')
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
      setCurrent(latest)
      setForecasts(predicted)
      const points: ChartPoint[] = history.map(row => ({ time: row.observedAt, observedAqi: row.aqi, generated: row.generated }))
      for (const row of predicted) points.push({ time: row.forecast.targetAt, forecastAqi: row.forecast.aqi, generated: row.forecast.sourceGenerated })
      setChart(points.sort((a, b) => a.time.localeCompare(b.time)))
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not load environmental data')
    } finally {
      setLoading(false)
    }
  }, [])

  const loadAccountData = useCallback(async () => {
    if (!token) { setSavedRoutes([]); setSavedPlaces([]); setRouteHistory([]); setNotifications([]); setUnreadCount(0); return }
    try {
      const [dashboard, profile] = await Promise.all([
        api<Dashboard>('/api/dashboard', token), api<{ preferences: Preferences }>('/api/users/me', token),
      ])
      setSavedRoutes(dashboard.savedRoutes); setSavedPlaces(dashboard.savedPlaces)
      setRouteHistory(dashboard.routeHistory); setNotifications(dashboard.notifications)
      setUnreadCount(dashboard.unreadNotificationCount); setPreferences(profile.preferences)
      setPreference(profile.preferences.routePreference); setMode(profile.preferences.preferredTravelMode)
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not load your account dashboard') }
  }, [token])

  useEffect(() => { void loadEnvironment() }, [loadEnvironment])
  useEffect(() => { void loadAccountData() }, [loadAccountData])

  const routePreferences = useMemo(() => ['FASTEST', 'CLEANEST', 'BALANCED', 'JOGGER', 'CYCLIST'], [])

  function choosePreference(value: string) {
    setPreference(value)
    if (value === 'JOGGER') setMode('JOG')
    else if (value === 'CYCLIST') setMode('CYCLE')
    else if (mode === 'JOG' || mode === 'CYCLE') setMode('WALK')
  }

  async function authenticate(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const body = registering ? { email, password, displayName } : { email, password }
      const result = await api<{ token: string }>(`/api/auth/${registering ? 'register' : 'login'}`, null,
        { method: 'POST', body: JSON.stringify(body) })
      localStorage.setItem('cleanroute.token', result.token); setToken(result.token); setPassword(''); setNotice('Signed in successfully')
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not sign in') }
    finally { setBusy(false) }
  }

  function signOut() {
    localStorage.removeItem('cleanroute.token'); setToken(null); setSavedRoutes([]); setCalculation(null); setNotice('Signed out')
  }

  async function calculateRoute(event: FormEvent) {
    event.preventDefault()
    if (!token) { setError('Sign in to calculate and save routes'); return }
    setBusy(true); setError(''); setNotice('')
    try {
      const result = await api<Calculation>('/api/routes/calculate', token,
        { method: 'POST', body: JSON.stringify({ origin, destination, mode, preference }) })
      setCalculation(result); setNotice('Route alternatives are ready'); void loadAccountData()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Could not calculate route') }
    finally { setBusy(false) }
  }

  async function saveRoute(alternative: Alternative) {
    if (!token) return
    setBusy(true); setError(''); setNotice('')
    try {
      await api('/api/routes/save', token, { method: 'POST', body: JSON.stringify({
        originName: 'Origin', originLatitude: origin.latitude, originLongitude: origin.longitude,
        destinationName: 'Destination', destinationLatitude: destination.latitude, destinationLongitude: destination.longitude,
        geometryPolyline: JSON.stringify(alternative.geometry), travelMode: mode, routePreference: preference,
        pollutionScore: alternative.expectedPollutionExposure, estimatedTravelTimeSeconds: alternative.durationSeconds,
        distanceMeters: alternative.distanceMeters,
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
      const updated = await api<{ preferences: Preferences }>('/api/users/me/preferences', token,
        { method: 'PUT', body: JSON.stringify(preferences) })
      setPreferences(updated.preferences); setNotice('Preferences saved')
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

  const displayedRoutes = calculation?.alternatives ?? []

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="CleanRoute home"><span className="brand-mark">C</span><span>CleanRoute</span></a>
        <div className="topbar-actions">
          <span className="demo-label">DEMO ENVIRONMENT · GENERATED DATA</span>
          {token && <button className="button quiet" onClick={signOut}>Sign out</button>}
        </div>
      </header>

      <section className="hero" id="top">
        <div><p className="eyebrow">POLLUTION-AWARE ROUTE PLANNING</p><h1>Choose a clearer way through the city.</h1>
          <p>Compare route alternatives against the latest available air-quality observations and clearly labeled forecasts.</p></div>
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
          <div className="chart-wrap">
            {loading ? <p className="placeholder">Loading observations and forecast…</p> : chart.length ? <ResponsiveContainer width="100%" height="100%"><LineChart data={chart} margin={{ top: 8, right: 12, left: -15, bottom: 5 }}>
              <CartesianGrid stroke="#e9eee8" strokeDasharray="3 3"/><XAxis dataKey="time" tickFormatter={value => new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} minTickGap={28}/><YAxis width={42}/><ChartTooltip labelFormatter={value => new Date(String(value)).toLocaleString()} /><Legend/>
              <Line type="monotone" dataKey="observedAqi" name="Observed AQI" stroke="#2f7d57" strokeWidth={2} dot={false} connectNulls={false}/>
              <Line type="monotone" dataKey="forecastAqi" name="Predicted AQI" stroke="#d18a24" strokeWidth={2} strokeDasharray="6 4" dot={{ r: 3 }} connectNulls={false}/>
            </LineChart></ResponsiveContainer> : <p className="empty-state">No chart data is available for this cell.</p>}
          </div>
          <div className="chart-legend-note"><span><i className="legend-observed"/> Observed</span><span><i className="legend-predicted"/> Predicted</span><small>{forecasts.length ? `${forecasts.length} predictions · ${forecasts[0].forecast.quality.toLowerCase()} quality` : 'Forecast baseline'} · not validated health guidance</small></div>
        </article>
      </section>

      <section className="planning-grid" aria-label="Route planning">
        <article className="panel planner-panel">
          <div className="panel-heading"><div><p className="eyebrow">PLAN A JOURNEY</p><h2>Route preferences</h2></div><span className="pill">Sign-in required</span></div>
          {!token ? <form className="auth-form" onSubmit={authenticate}>
            <p className="muted">Sign in to calculate, save, and review routes. Environmental observations remain available without an account.</p>
            {registering && <label>Display name<input value={displayName} onChange={event => setDisplayName(event.target.value)} required maxLength={100}/></label>}
            <label>Email<input type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254}/></label>
            <label>Password<input type="password" autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} required minLength={8} maxLength={72}/></label>
            <button className="button primary full" disabled={busy}>{busy ? 'Please wait…' : registering ? 'Create account' : 'Sign in'}</button>
            <button className="text-button" type="button" onClick={() => setRegistering(!registering)}>{registering ? 'Already have an account? Sign in' : 'New to CleanRoute? Create account'}</button>
          </form> : <form className="route-form" onSubmit={calculateRoute}>
            <fieldset><legend>Starting point</legend><div className="coordinate-pair"><label>Latitude<input type="number" step="any" min="-90" max="90" required value={origin.latitude} onChange={event => setOrigin({ ...origin, latitude: Number(event.target.value) })}/></label><label>Longitude<input type="number" step="any" min="-180" max="180" required value={origin.longitude} onChange={event => setOrigin({ ...origin, longitude: Number(event.target.value) })}/></label></div></fieldset>
            <fieldset><legend>Destination</legend><div className="coordinate-pair"><label>Latitude<input type="number" step="any" min="-90" max="90" required value={destination.latitude} onChange={event => setDestination({ ...destination, latitude: Number(event.target.value) })}/></label><label>Longitude<input type="number" step="any" min="-180" max="180" required value={destination.longitude} onChange={event => setDestination({ ...destination, longitude: Number(event.target.value) })}/></label></div></fieldset>
            <label>Preference<select value={preference} onChange={event => choosePreference(event.target.value)}>{routePreferences.map(value => <option key={value}>{value}</option>)}</select></label>
            <label>Travel mode<select value={mode} onChange={event => setMode(event.target.value)}>{['WALK', 'CAR', 'CYCLE', 'JOG'].map(value => <option key={value}>{value}</option>)}</select></label>
            <p className="muted small">JOGGER and CYCLIST use available traffic and pollution context. Green-area, compatibility, and elevation data are currently unavailable in the demo.</p>
            <div className="place-actions"><button className="button quiet" type="button" onClick={() => void savePlace('Route origin', origin)}>Save origin place</button><button className="button quiet" type="button" onClick={() => void savePlace('Route destination', destination)}>Save destination</button></div>
            <button className="button primary full" disabled={busy}>{busy ? 'Calculating…' : 'Compare routes'}</button>
          </form>}
        </article>

        <article className="panel map-panel">
          <div className="panel-heading map-heading"><div><p className="eyebrow">CITY MAP</p><h2>Air-quality cells and routes</h2></div><span className="map-credit">© OpenStreetMap contributors</span></div>
          <div className="map-wrap"><MapContainer center={[28.6139, 77.2090]} zoom={11} scrollWheelZoom className="leaflet-map">
            <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/>
            {cells.map(cell => <Marker key={cell.id} position={[cell.latitude, cell.longitude]} icon={cellIcon}><Tooltip>{cell.label} · fixed demo cell</Tooltip></Marker>)}
            {displayedRoutes.map(route => <Polyline key={route.alternativeId} positions={route.geometry.map(point => [point.latitude, point.longitude] as [number, number])} pathOptions={{ color: routeColor(route.expectedPollutionExposure), weight: route.rank === 1 ? 6 : 4, opacity: route.rank === 1 ? .9 : .55 }}><Tooltip>{route.alternativeId} · exposure {route.expectedPollutionExposure.toFixed(1)}</Tooltip></Polyline>)}
            {displayedRoutes.length > 0 && <FitMap routes={displayedRoutes}/>}
          </MapContainer></div>
          <div className="map-legend"><span><i className="legend-clean"/> Lower modeled exposure</span><span><i className="legend-moderate"/> Mid modeled exposure</span><span><i className="legend-high"/> Higher modeled exposure</span></div>
        </article>
      </section>

      <section className="results-section" aria-label="Route alternatives">
        <div className="section-title"><div><p className="eyebrow">COMPARISON</p><h2>{calculation ? `${calculation.preference} alternatives` : 'Route alternatives'}</h2></div><span className="muted">{calculation ? 'Generated mock routes · modeled exposure' : 'Calculate a route to compare options'}</span></div>
        {displayedRoutes.length ? <div className="route-cards">{displayedRoutes.map(route => <article className={`route-card ${route.rank === 1 ? 'top-route' : ''}`} key={route.alternativeId}>
          <div className="route-card-top"><span className="route-rank">{route.rank}</span><div><h3>{route.alternativeId.replaceAll('-', ' ')}</h3><span className="route-provider">{route.rank === 1 ? 'Top match' : 'Alternative'} · generated mock</span></div><strong className="route-score">{route.preferenceScore.toFixed(0)}<small>match</small></strong></div>
          <div className="route-stats"><span><b>{(route.distanceMeters / 1000).toFixed(1)}</b> km</span><span><b>{Math.round(route.durationSeconds / 60)}</b> min</span><span><b>{route.expectedPollutionExposure.toFixed(1)}</b> exposure</span></div>
          <p className="route-reason">{route.reasons[0]}</p><button className="button outline full" onClick={() => void saveRoute(route)} disabled={!token || busy}>Save this route</button>
        </article>)}</div> : <div className="empty-panel">Route alternatives will appear here after you sign in and submit a journey.</div>}
      </section>

      {token && <section className="account-grid" aria-label="Account dashboard">
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">YOUR PLACES</p><h2>Saved places</h2></div><span className="pill">{savedPlaces.length}</span></div>
          {savedPlaces.length ? <ul className="saved-list">{savedPlaces.map(place => <li key={place.id}><div><strong>{place.name}</strong><span>{place.latitude.toFixed(4)}, {place.longitude.toFixed(4)}</span><div className="place-actions"><button className="text-button" onClick={() => setOrigin({ latitude: place.latitude, longitude: place.longitude })}>Use as origin</button><button className="text-button" onClick={() => setDestination({ latitude: place.latitude, longitude: place.longitude })}>Use as destination</button></div></div><button className="text-button danger-text" onClick={() => void removeSavedPlace(place.id)}>Remove</button></li>)}</ul> : <p className="empty-state">Save an origin or destination to reuse it.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">YOUR ROUTES</p><h2>Saved routes</h2></div><span className="pill">{savedRoutes.length} saved</span></div>
          {savedRoutes.length ? <ul className="saved-list">{savedRoutes.map(route => <li key={route.id}><div><strong>{route.originName} → {route.destinationName}</strong><span>{route.travelMode} · {route.routePreference}</span></div><button className="text-button danger-text" onClick={() => void removeSavedRoute(route.id)}>Remove</button></li>)}</ul> : <p className="empty-state">Saved routes will show up here.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">RECENT CALCULATIONS</p><h2>Route history</h2></div><span className="pill">{routeHistory.length}</span></div>
          {routeHistory.length ? <ul className="saved-list">{routeHistory.slice(0, 10).map(route => <li key={route.id}><div><strong>{route.originName} → {route.destinationName}</strong><span>{route.travelMode} · {route.routePreference} · {route.distanceMeters == null ? '—' : `${(route.distanceMeters / 1000).toFixed(1)} km`}</span></div></li>)}</ul> : <p className="empty-state">Calculated routes will appear here.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">ALERTS</p><h2>Notifications</h2></div><span className="pill">{unreadCount} unread</span></div>
          {notifications.length ? <ul className="saved-list">{notifications.map(item => <li key={item.id}><div><strong>{item.title}</strong><span>{item.message}</span></div>{!item.readAt && <button className="text-button" onClick={() => void markNotificationRead(item.id)}>Mark read</button>}</li>)}</ul> : <p className="empty-state">No alerts yet. Forecast and route checks can create in-app alerts.</p>}
        </article>
        <article className="panel saved-panel preferences-panel"><div className="panel-heading"><div><p className="eyebrow">ACCOUNT SETTINGS</p><h2>Preferences</h2></div></div>
          <form className="route-form" onSubmit={savePreferences}>
            <label>Preferred route ranking<select value={preferences.routePreference} onChange={event => setPreferences({ ...preferences, routePreference: event.target.value })}>{routePreferences.map(value => <option key={value}>{value}</option>)}</select></label>
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
