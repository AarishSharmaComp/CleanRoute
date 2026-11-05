import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis } from 'recharts'
import 'leaflet/dist/leaflet.css'
import { LocationSearchField, type LocationInput } from './location/LocationSearchField'
import { BackendLocationSearchProvider } from './location/locationSearch'
import type { LocationSearchResult } from './location/locationSearch'
import { EnvironmentalStatus } from './components/EnvironmentalStatus'

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
type RouteComparison = { availableRoutes: number; rank: number; exposureDifferencePercent?: number | null; extraDurationSeconds?: number | null; extraDistanceMeters?: number | null }
type SelectionReason = { type: string; headline: string; summary: string; comparison?: RouteComparison | null; factors: string[] }
type Alternative = { alternativeId: string; rank: number; provider?: string; generated: boolean; geometry?: Point[] | null; distanceMeters: number | null; durationSeconds: number | null; expectedPollutionExposure: number | null; forecastQualityScore?: number | null; preferenceScore: number | null; scoreComponents?: Record<string, number | null> | null; reasons?: string[] | null; selectionReason?: SelectionReason | null; environmentalProvider?: string | null; environmentalCoverage?: string | null; environmentalCoveragePercent?: number | null; observationSource?: string | null; sampledPointCount?: number; availableSampleCount?: number; unavailableSampleCount?: number }
type RouteRequestSnapshot = { origin: LocationSearchResult; destination: LocationSearchResult; mode: string; preference: string; key: string }
type Calculation = { id: string; preference: string; alternatives: Alternative[]; generated?: boolean; limitation?: string; request: RouteRequestSnapshot }
type SavedRoute = { id: string; originName: string; destinationName: string; mode: string; preference: string }
type SavedPlace = { id: string; name: string; latitude: number; longitude: number; address: string | null }
type RouteHistory = { id: string; originName: string; destinationName: string; mode: string; preference: string; pollutionScore: number | null; durationSeconds: number | null; distanceMeters: number | null; createdAt: string }
type UserNotification = { id: string; kind: string; title: string; message: string; createdAt: string; readAt: string | null }
type Preferences = { preferredTravelMode: string; routePreference: string; notificationsEnabled: boolean; pollutionSensitivity: number }
type Dashboard = { savedPlaces: SavedPlace[]; savedRoutes: SavedRoute[]; routeHistory: RouteHistory[]; notifications: UserNotification[]; unreadNotificationCount: number }
type ChartPoint = { time: string; observedAqi?: number | null; forecastAqi?: number | null; generated?: boolean }

function routeRequestKey(origin: LocationSearchResult | null, destination: LocationSearchResult | null, mode: string, preference: string): string {
  if (!origin || !destination) return ''
  return [origin.latitude, origin.longitude, destination.latitude, destination.longitude, mode, preference].join('|')
}

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
  { value: 'JOGGER', icon: '🏃', title: 'Jogger', detail: 'Favors low exposure, calmer roads, and comfortable running routes.' },
  { value: 'CYCLIST', icon: '🚴', title: 'Cyclist', detail: 'Favors cycling-compatible routes with lower pollution and traffic.' },
]
const travelModes = [
  { value: 'CAR', icon: '🚗', label: 'Driving' },
  { value: 'WALK', icon: '🚶', label: 'Walking' },
  { value: 'CYCLE', icon: '🚴', label: 'Cycling' },
  { value: 'JOG', icon: '🏃', label: 'Jogging' },
]

function displayValue(value: number | null | undefined, digits = 0): string {
  return value == null || !Number.isFinite(value) ? 'Data unavailable' : value.toFixed(digits)
}

function routeCoverage(route: Alternative): string {
  return route.environmentalCoverage || 'fixed-cell'
}

function explanationTradeoffs(reason: SelectionReason | null | undefined): string[] {
  const comparison = reason?.comparison
  if (!comparison) return []
  const lines: string[] = []
  if (comparison.exposureDifferencePercent != null && Number.isFinite(comparison.exposureDifferencePercent)) {
    const percent = Math.abs(comparison.exposureDifferencePercent).toFixed(0)
    lines.push(comparison.exposureDifferencePercent < 0 ? `${percent}% lower modeled exposure than the comparison route.` : `${percent}% higher modeled exposure than the comparison route.`)
  }
  if (comparison.extraDurationSeconds != null && comparison.extraDurationSeconds !== 0) {
    const minutes = Math.abs(comparison.extraDurationSeconds / 60)
    lines.push(`${comparison.extraDurationSeconds > 0 ? '+' : '-'}${minutes < 1 ? `${Math.round(Math.abs(comparison.extraDurationSeconds))} sec` : `${Math.round(minutes)} min`} vs the comparison route.`)
  }
  if (comparison.extraDistanceMeters != null && Math.abs(comparison.extraDistanceMeters) >= 1) {
    lines.push(`${comparison.extraDistanceMeters > 0 ? '+' : '-'}${(Math.abs(comparison.extraDistanceMeters) / 1000).toFixed(1)} km vs the comparison route.`)
  }
  return lines
}

function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('cleanroute.token'))
  const [activePage, setActivePage] = useState<'overview' | 'planner' | 'activity'>('overview')
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
  const requestVersion = useRef(0)
  const currentPlannerKey = routeRequestKey(origin.selected, destination.selected, mode, preference)
  const currentPlannerKeyRef = useRef(currentPlannerKey)
  currentPlannerKeyRef.current = currentPlannerKey

  function invalidateCalculation() {
    requestVersion.current += 1
    setCalculation(null)
    setSelectedRouteId(null)
    setBusy(false)
  }

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
        const savedPreference = ['FASTEST', 'CLEANEST', 'BALANCED', 'JOGGER', 'CYCLIST'].includes(profile.preferences.routePreference) ? profile.preferences.routePreference : 'BALANCED'
        const savedMode = ['CAR', 'WALK', 'CYCLE', 'JOG'].includes(profile.preferences.preferredTravelMode) ? profile.preferences.preferredTravelMode : 'WALK'
        setPreference(savedPreference)
        setMode(savedPreference === 'JOGGER' ? 'JOG' : savedPreference === 'CYCLIST' ? 'CYCLE' : savedMode)
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
    localStorage.removeItem('cleanroute.token'); setToken(null); setSavedRoutes([]); invalidateCalculation(); setNotice('Signed out')
  }

  async function calculateRoute(event: FormEvent) {
    event.preventDefault()
    const from = origin.selected
    const to = destination.selected
    if (!from || !to) { setError('Choose a starting point and destination from the search suggestions.'); return }
    if (!token) { setError('Sign in to calculate and save routes'); return }
    const snapshot: RouteRequestSnapshot = { origin: from, destination: to, mode, preference,
      key: routeRequestKey(from, to, mode, preference) }
    const version = ++requestVersion.current
    setCalculation(null); setSelectedRouteId(null); setBusy(true); setError(''); setNotice('')
    try {
      const result = await api<Omit<Calculation, 'request'>>('/api/routes/calculate', token, {
        method: 'POST', body: JSON.stringify({ origin: { latitude: from.latitude, longitude: from.longitude }, destination: { latitude: to.latitude, longitude: to.longitude }, mode, preference }),
      })
      if (version !== requestVersion.current || snapshot.key !== currentPlannerKeyRef.current) return
      setCalculation({ ...result, request: snapshot })
      setSelectedRouteId(result.alternatives[0]?.alternativeId ?? null)
      setNotice(result.alternatives.length ? 'Route alternatives are ready' : 'No routes were returned for these locations')
      void loadAccountData()
    } catch (cause) {
      if (version === requestVersion.current) {
        setCalculation(null); setSelectedRouteId(null)
        setError(cause instanceof Error ? cause.message : 'CleanRoute could not calculate routes right now. Please try again.')
      }
    } finally { if (version === requestVersion.current) setBusy(false) }
  }

  async function saveRoute(alternative: Alternative) {
    if (!token || !calculation || calculation.request.key !== currentPlannerKeyRef.current) {
      setError('This route result is no longer current. Calculate the edited journey before saving it.')
      return
    }
    const request = calculation.request
    setBusy(true); setError(''); setNotice('')
    try {
      await api('/api/routes/save', token, { method: 'POST', body: JSON.stringify({
        originName: request.origin.displayName, originLatitude: request.origin.latitude, originLongitude: request.origin.longitude,
        destinationName: request.destination.displayName, destinationLatitude: request.destination.latitude, destinationLongitude: request.destination.longitude,
        geometryPolyline: JSON.stringify(alternative.geometry ?? []), travelMode: request.mode, routePreference: request.preference,
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
      const compatiblePreferences = { ...preferences, preferredTravelMode: preferences.routePreference === 'JOGGER' ? 'JOG' : preferences.routePreference === 'CYCLIST' ? 'CYCLE' : preferences.preferredTravelMode }
      const updated = await api<{ preferences: Preferences }>('/api/users/me/preferences', token, { method: 'PUT', body: JSON.stringify(compatiblePreferences) })
      setPreferences(updated.preferences)
      const savedPreference = ['FASTEST', 'CLEANEST', 'BALANCED', 'JOGGER', 'CYCLIST'].includes(updated.preferences.routePreference)
        ? updated.preferences.routePreference : 'BALANCED'
      const savedMode = ['CAR', 'WALK', 'CYCLE', 'JOG'].includes(updated.preferences.preferredTravelMode)
        ? updated.preferences.preferredTravelMode : 'WALK'
      invalidateCalculation()
      setPreference(savedPreference)
      setMode(savedPreference === 'JOGGER' ? 'JOG' : savedPreference === 'CYCLIST' ? 'CYCLE' : savedMode)
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
    invalidateCalculation()
    if (target === 'origin') setOrigin(value); else setDestination(value)
  }

  const displayedRoutes = calculation?.alternatives ?? []
  const activeRoute = displayedRoutes.find(route => route.alternativeId === selectedRouteId) ?? displayedRoutes[0] ?? null
  const selectedOrigin = origin.selected
  const selectedDestination = destination.selected
  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="CleanRoute home" onClick={event => { event.preventDefault(); setActivePage('overview') }}><span className="brand-mark"><span>c</span><i aria-hidden="true"/></span><span>CleanRoute</span></a>
        <nav className="topbar-nav" aria-label="Primary navigation">
          <button className={activePage === 'overview' ? 'nav-item active' : 'nav-item'} aria-current={activePage === 'overview' ? 'page' : undefined} onClick={() => setActivePage('overview')}>Overview</button>
          <button className={activePage === 'planner' ? 'nav-item active' : 'nav-item'} aria-current={activePage === 'planner' ? 'page' : undefined} onClick={() => setActivePage('planner')}>Plan a route</button>
          <button className={activePage === 'activity' ? 'nav-item active' : 'nav-item'} aria-current={activePage === 'activity' ? 'page' : undefined} onClick={() => setActivePage('activity')}>My activity{unreadCount > 0 && <span className="nav-count">{unreadCount}</span>}</button>
        </nav>
        <div className="topbar-actions"><span className="demo-label">AIR QUALITY · ROUTE INSIGHTS</span>{token ? <button className="button quiet" onClick={signOut}>Sign out</button> : <button className="button quiet" onClick={() => setActivePage('planner')}>Sign in</button>}</div>
      </header>

      {activePage === 'overview' && <section className="hero" id="top">
        <div className="hero-copy"><p className="eyebrow">A BETTER WAY TO GET THERE</p><h1>Take the route that lets you <em>breathe easier.</em></h1><p>See the air around your journey. Compare practical routes by travel time and modeled pollution exposure.</p><button className="button primary hero-action" onClick={() => setActivePage('planner')}>Plan a cleaner route <span aria-hidden="true">↗</span></button><div className="hero-trust"><span><i/> 15-minute air quality updates</span><span><i/> Clear forecast confidence</span></div></div>
        <div className="hero-art" aria-label="Illustration of a city park with a clean route winding through the neighborhood" role="img">
          <div className="art-sun"/><div className="art-cloud cloud-one"/><div className="art-cloud cloud-two"/>
          <svg viewBox="0 0 620 370" role="presentation" aria-hidden="true" preserveAspectRatio="xMidYMid meet">
            <path className="art-hill hill-back" d="M0 238 Q130 174 255 236 T620 208 V370 H0Z"/><path className="art-hill hill-front" d="M0 292 Q120 225 249 286 T620 258 V370 H0Z"/>
            <g className="art-buildings"><path d="M52 156h58v138H52zM120 122h76v177h-76zM207 174h53v121h-53zM398 142h73v151h-73zM484 108h74v189h-74zM568 173h36v119h-36z"/><path d="M69 174h9v12h-9zm23 0h9v12h-9zm-23 27h9v12h-9zm23 0h9v12h-9zM140 143h10v13h-10zm25 0h10v13h-10zm-25 30h10v13h-10zm25 0h10v13h-10zm-25 30h10v13h-10zm25 0h10v13h-10zM418 161h10v13h-10zm27 0h10v13h-10zm-27 28h10v13h-10zm27 0h10v13h-10zM504 128h10v13h-10zm28 0h10v13h-10zm-28 29h10v13h-10zm28 0h10v13h-10zm-28 29h10v13h-10zm28 0h10v13h-10z"/></g>
            <path className="art-road" d="M40 365 Q136 329 198 344 T323 322 Q362 312 342 281 T385 241 Q424 212 388 190 T410 152"/>
            <path className="art-route" d="M40 365 Q136 329 198 344 T323 322 Q362 312 342 281 T385 241 Q424 212 388 190 T410 152"/>
            <g className="art-tree tree-a"><circle cx="282" cy="264" r="29"/><circle cx="258" cy="280" r="20"/><path d="M278 281v43"/></g><g className="art-tree tree-b"><circle cx="460" cy="266" r="34"/><circle cx="490" cy="278" r="21"/><path d="M463 284v44"/></g>
            <g className="art-pin"><circle cx="410" cy="151" r="19"/><circle cx="410" cy="151" r="6"/><path d="M410 171v26"/></g>
          </svg>
          <div className="art-label label-air"><span className="air-pulse"/><div><small>LOCAL AIR QUALITY</small><strong>Live city view</strong></div></div>
          <div className="art-label label-route"><span className="route-swatch"/><div><small>YOUR ROUTE</small><strong>Cleaner way ahead</strong></div></div>
          <span className="art-credit">Illustrative city view · route geometry comes from your routing provider</span>
        </div>
      </section>}

      {(error || notice) && <div className={error ? 'banner error-banner' : 'banner success-banner'} role={error ? 'alert' : 'status'}>{error || notice}<button onClick={() => { setError(''); setNotice('') }} aria-label="Dismiss">×</button></div>}

      {activePage === 'overview' && <section className="overview-page page-enter">
      <div className="dashboard-grid conditions-grid" aria-label="Current conditions">
        <article className="panel current-panel">
          <div className="panel-heading"><div><p className="eyebrow">CURRENT AIR QUALITY</p><h2>Central Delhi</h2></div><span className={current?.stale ? 'pill stale' : 'pill'}>{current?.stale ? 'Stale' : 'Latest'}</span></div>
          {loading ? <p className="placeholder">Loading latest observation…</p> : current ? <div className="aqi-reading"><strong>{current.aqi ?? '—'}</strong><span>AQI index</span><small>{current.pm25 == null ? 'PM2.5 unavailable' : `PM2.5 ${current.pm25.toFixed(1)} µg/m³`}</small></div> : <p className="placeholder">No current observation is available.</p>}
          <p className="provenance">{current?.generated ? 'Generated demo observation' : current ? 'Provider observation' : 'Data source unavailable'}{current?.observedAt ? ` · ${new Date(current.observedAt).toLocaleString()}` : ''}</p>
        </article>
        <article className="panel forecast-panel">
          <div className="panel-heading"><div><p className="eyebrow">NEXT 2 HOURS</p><h2>Observed and forecast AQI</h2></div><button className="button quiet" onClick={() => void loadEnvironment()} disabled={loading}>Refresh</button></div>
          <div className="chart-wrap">{loading ? <p className="placeholder">Loading observations and forecast…</p> : chart.length ? <ResponsiveContainer width="100%" height="100%"><LineChart data={chart} margin={{ top: 8, right: 12, left: -15, bottom: 5 }}>
            <CartesianGrid stroke="#e3e9e6" strokeDasharray="3 3"/><XAxis dataKey="time" tickFormatter={value => new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} minTickGap={28}/><YAxis width={42}/><ChartTooltip labelFormatter={value => new Date(String(value)).toLocaleString()}/><Legend/>
            <Line type="monotone" dataKey="observedAqi" name="Observed AQI" stroke="#087d79" strokeWidth={2.5} dot={false} connectNulls={false}/><Line type="monotone" dataKey="forecastAqi" name="Predicted AQI" stroke="#bd622b" strokeWidth={2.5} strokeDasharray="6 4" dot={{ r: 3 }} connectNulls={false}/>
          </LineChart></ResponsiveContainer> : <p className="empty-state">No chart data is available for this cell.</p>}</div>
          <div className="chart-legend-note"><span><i className="legend-observed"/> Observed</span><span><i className="legend-predicted"/> Predicted</span><small>{forecasts.length ? `${forecasts.length} predictions · ${forecasts[0].forecast.quality.toLowerCase()} quality` : 'Forecast baseline'} · not validated health guidance</small></div>
          <p className="provenance">Current AQI and forecast are city-cell context, not a per-route AQI measurement.</p>
        </article>
      </div>
      <div className="overview-note"><span className="note-icon">i</span><p><strong>Data with context.</strong> Air-quality readings are tied to monitored cells; route exposure uses available samples and is not health guidance.</p><button className="text-button" onClick={() => setActivePage('planner')}>Explore routes <span aria-hidden="true">→</span></button></div>
      </section>}

      {activePage === 'planner' && <div className="planner-page page-enter"><div className="page-heading"><div><p className="eyebrow">YOUR JOURNEY</p><h1>Plan a route</h1><p>Choose where you’re going. We’ll show the time and air-quality context for each option.</p></div><span className="pill page-pill">Pollution-aware planning</span></div>
      <section className="planning-grid" id="planner" aria-label="Route planning">
        <article className="panel planner-panel">
          <div className="panel-heading"><div><p className="eyebrow">PLAN A JOURNEY</p><h2>From here to there</h2></div><span className="pill">{token ? 'Ready to plan' : 'Sign-in required'}</span></div>
          <form className="route-form journey-form" onSubmit={calculateRoute}>
            <div className="journey-locations">
              <LocationSearchField label="From" placeholder="Search starting point" value={origin} provider={locationProvider} onChange={value => { invalidateCalculation(); setOrigin(value) }}/>
              <button className="swap-button" type="button" aria-label="Swap origin and destination" onClick={() => { invalidateCalculation(); setOrigin(destination); setDestination(origin) }}>⇅</button>
              <LocationSearchField label="To" placeholder="Search destination" value={destination} provider={locationProvider} onChange={value => { invalidateCalculation(); setDestination(value) }}/>
            </div>
            {(selectedOrigin || selectedDestination) && (!selectedOrigin?.supportedArea || !selectedDestination?.supportedArea) && <p className="provenance" role="status">Global place search works, but environmental coverage depends on the configured provider and route response. No exposure value will be invented when coverage is unavailable.</p>}
            <fieldset className="choice-field"><legend>Travel mode</legend><div className="segmented-control travel-mode-segments" role="group" aria-label="Travel mode">
              {travelModes.map(item => <button key={item.value} type="button" className={mode === item.value ? 'segment active' : 'segment'} aria-pressed={mode === item.value} onClick={() => { invalidateCalculation(); setMode(item.value); if (preference === 'JOGGER' && item.value !== 'JOG' || preference === 'CYCLIST' && item.value !== 'CYCLE') setPreference('BALANCED') }}><span>{item.icon}</span>{item.label}</button>)}
            </div><p className="choice-description">Walking/cycling requests are checked against the configured OSRM profile; unsupported profiles are rejected instead of falling back to a car route.</p></fieldset>
            <fieldset className="choice-field"><legend>Route preference</legend><div className="segmented-control preference-segments" role="group" aria-label="Route preference">
              {rankingModes.map(item => <button key={item.value} type="button" className={preference === item.value ? 'segment active' : 'segment'} aria-pressed={preference === item.value} onClick={() => { invalidateCalculation(); setPreference(item.value); if (item.value === 'JOGGER') setMode('JOG'); if (item.value === 'CYCLIST') setMode('CYCLE') }}><span>{item.icon}</span>{item.title}</button>)}
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
                  pathOptions={{ color: selected ? '#087d79' : '#738b84', weight: selected ? 7 : 4, opacity: selected ? .98 : .48 }}><Tooltip>{routeCoverage(route) === 'unavailable' ? `${route.alternativeId} · environmental data unavailable` : `${route.alternativeId} · modeled exposure ${displayValue(route.expectedPollutionExposure, 1)}`}</Tooltip></Polyline>
            })}
            <FitMap route={activeRoute} origin={selectedOrigin} destination={selectedDestination}/>
          </MapContainer>{displayedRoutes.length > 0 && !displayedRoutes.some(route => validGeometry(route).length) && <div className="map-fallback" role="status">Route geometry is unavailable. Route details are shown below.</div>}</div>
          <div className="map-legend"><span><i className="legend-clean"/> Selected route</span><span><i className="legend-other"/> Other alternatives</span><span><i className="legend-cell"/> Fixed demo AQI cells</span></div>
          <p className="provenance">Route lines use geometry returned by the routing provider. Demo routes are deterministic generated data, not Google Maps directions.</p>
        </article>
      </section>

       <section className="results-section" id="comparison" aria-label="Route alternatives">
        <div className="section-title"><div><p className="eyebrow">COMPARISON</p><h2>{calculation ? `${calculation.preference} alternatives` : 'Route alternatives'}</h2></div><span className="muted">{calculation ? (calculation.generated ? 'Generated route alternatives · backend ranking order' : 'Provider routes · backend ranking order') : 'Compare travel time with modeled pollution exposure'}</span></div>
        {displayedRoutes.length ? <div className="route-cards">{displayedRoutes.map(route => {
          const components = Object.entries(route.scoreComponents ?? {})
          const geometryAvailable = validGeometry(route).length > 0
           const coverage = routeCoverage(route)
           return <article className={`route-card ${route.rank === 1 ? 'top-route' : ''} ${activeRoute?.alternativeId === route.alternativeId ? 'selected-route-card' : ''}`} key={route.alternativeId}>
             <div className="route-card-top"><span className="route-rank">{route.rank}</span><div><h3>{route.alternativeId.replaceAll('-', ' ')}</h3><span className="route-provider">{calculation?.preference} · {route.generated ? 'Generated demo route' : route.provider || 'Provider route'}</span></div><strong className="route-score">{displayValue(route.preferenceScore)}<small>preference score</small></strong></div>
                <EnvironmentalStatus coverage={coverage} source={[route.environmentalProvider, route.observationSource].filter(Boolean).join(' · ')} availableSamples={route.availableSampleCount} sampledPoints={route.sampledPointCount} />
              <div className="route-stats"><span><b>{route.distanceMeters == null ? 'Data unavailable' : `${(route.distanceMeters / 1000).toFixed(1)} km`}</b> distance</span><span><b>{route.durationSeconds == null ? 'Data unavailable' : `${Math.round(route.durationSeconds / 60)} min`}</b> duration</span><span><b>{route.expectedPollutionExposure == null ? 'Unavailable' : `${route.expectedPollutionExposure.toFixed(1)}`}</b> {route.expectedPollutionExposure == null ? 'exposure' : 'modeled exposure'}<small className="metric-caveat">Fixed-cell historical forecast · not roadside sensor data · not health guidance</small></span></div>
               <dl className="route-details"><div><dt>Route AQI</dt><dd>Not provided</dd></div><div><dt>Coverage</dt><dd>{route.environmentalCoveragePercent != null ? `${route.environmentalCoveragePercent.toFixed(1)}%` : 'Unavailable'}</dd></div><div><dt>Forecast quality</dt><dd>{route.forecastQualityScore != null ? `${route.forecastQualityScore}/100` : 'Not provided'}</dd></div><div><dt>Geometry</dt><dd>{geometryAvailable ? 'Available' : 'Data unavailable'}</dd></div></dl>
            {components.length > 0 && <div className="score-components"><strong>Score components</strong><ul>{components.map(([name, value]) => <li key={name}><span>{name.replaceAll('_', ' ')}</span><b>{displayValue(value, 1)}</b></li>)}</ul></div>}
              <details className="route-explanation" open={route.rank === 1}><summary>Why this route?</summary><strong>{route.selectionReason?.headline || 'Route ranking explanation'}</strong><p>{route.selectionReason?.summary || route.reasons?.join(' ') || 'No route explanation was returned.'}</p>{route.selectionReason?.factors?.map(factor => <p className="explanation-factor" key={factor}>{factor}</p>)}{explanationTradeoffs(route.selectionReason).map(line => <p className="explanation-tradeoff" key={line}>{line}</p>)}</details>
            <div className="route-card-actions"><button className="button outline" onClick={() => setSelectedRouteId(route.alternativeId)} aria-pressed={activeRoute?.alternativeId === route.alternativeId}>Show on map</button><button className="button outline" onClick={() => void saveRoute(route)} disabled={!token || busy || !selectedOrigin || !selectedDestination}>Save route</button></div>
          </article>
        })}</div> : <div className="empty-panel"><strong>Plan a cleaner journey</strong><p>Enter your starting point and destination to compare routes using travel time and pollution exposure.</p>{calculation && !displayedRoutes.length && <span>No route alternatives were returned. Try different locations or check again later.</span>}</div>}
        {calculation?.limitation && <p className="provenance">{calculation.limitation}</p>}
      </section>
      </div>}

       {activePage === 'activity' && <section className="activity-page page-enter" id="account" aria-label="Account dashboard">
       <div className="page-heading"><div><p className="eyebrow">YOUR CLEANROUTE</p><h1>Your activity</h1><p>Keep your regular places, routes, and air-quality alerts together.</p></div>{token && <span className="pill page-pill">{unreadCount} unread alerts</span>}</div>
       {token ? <div className="account-grid">
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">YOUR PLACES</p><h2>Saved places</h2></div><span className="pill">{savedPlaces.length}</span></div>
          {savedPlaces.length ? <ul className="saved-list">{savedPlaces.map(place => <li key={place.id}><div><strong>{place.name}</strong><span>{place.address || `${place.latitude.toFixed(4)}, ${place.longitude.toFixed(4)}`}</span><div className="place-actions"><button className="text-button" onClick={() => useSavedPlace(place, 'origin')}>Use as origin</button><button className="text-button" onClick={() => useSavedPlace(place, 'destination')}>Use as destination</button></div></div><button className="text-button danger-text" onClick={() => void removeSavedPlace(place.id)}>Remove</button></li>)}</ul> : <p className="empty-state">Save an origin or destination to reuse it.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">YOUR ROUTES</p><h2>Saved routes</h2></div><span className="pill">{savedRoutes.length} saved</span></div>
          {savedRoutes.length ? <ul className="saved-list">{savedRoutes.map(route => <li key={route.id}><div><strong>{route.originName} → {route.destinationName}</strong><span>{route.mode} · {route.preference}</span></div><button className="text-button danger-text" onClick={() => void removeSavedRoute(route.id)}>Remove</button></li>)}</ul> : <p className="empty-state">Saved routes will show up here.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">RECENT CALCULATIONS</p><h2>Route history</h2></div><span className="pill">{routeHistory.length}</span></div>
          {routeHistory.length ? <ul className="saved-list">{routeHistory.slice(0, 10).map(route => <li key={route.id}><div><strong>{route.originName} → {route.destinationName}</strong><span>{route.mode} · {route.preference} · {route.distanceMeters == null ? 'Data unavailable' : `${(route.distanceMeters / 1000).toFixed(1)} km`}</span></div></li>)}</ul> : <p className="empty-state">Calculated routes will appear here.</p>}
        </article>
        <article className="panel saved-panel"><div className="panel-heading"><div><p className="eyebrow">ALERTS</p><h2>Notifications</h2></div><span className="pill">{unreadCount} unread</span></div>
          {notifications.length ? <ul className="saved-list">{notifications.map(item => <li key={item.id}><div><strong>{item.title}</strong><span>{item.message}</span></div>{!item.readAt && <button className="text-button" onClick={() => void markNotificationRead(item.id)}>Mark read</button>}</li>)}</ul> : <p className="empty-state">No alerts yet. Forecast and route checks can create in-app alerts.</p>}
        </article>
        <article className="panel saved-panel preferences-panel"><div className="panel-heading"><div><p className="eyebrow">ACCOUNT SETTINGS</p><h2>Preferences</h2></div></div>
          <form className="route-form" onSubmit={savePreferences}>
            <label>Preferred route ranking<select value={preferences.routePreference} onChange={event => { const routePreference = event.target.value; setPreferences({ ...preferences, routePreference, preferredTravelMode: routePreference === 'JOGGER' ? 'JOG' : routePreference === 'CYCLIST' ? 'CYCLE' : preferences.preferredTravelMode }) }}>{['FASTEST', 'CLEANEST', 'BALANCED', 'JOGGER', 'CYCLIST'].map(value => <option key={value}>{value}</option>)}</select></label>
            <label>Preferred travel mode<select value={preferences.preferredTravelMode} onChange={event => {
              const preferredTravelMode = event.target.value
              const hasModeConflict = preferences.routePreference === 'JOGGER' && preferredTravelMode !== 'JOG'
                || preferences.routePreference === 'CYCLIST' && preferredTravelMode !== 'CYCLE'
              setPreferences({ ...preferences, preferredTravelMode, routePreference: hasModeConflict ? 'BALANCED' : preferences.routePreference })
            }}>{['WALK', 'CAR', 'CYCLE', 'JOG'].map(value => <option key={value}>{value}</option>)}</select></label>
            <label className="check-label"><input type="checkbox" checked={preferences.notificationsEnabled} onChange={event => setPreferences({ ...preferences, notificationsEnabled: event.target.checked })}/> Enable in-app notifications</label>
            <label>Pollution sensitivity · {preferences.pollutionSensitivity}<input type="range" min="1" max="5" value={preferences.pollutionSensitivity} onChange={event => setPreferences({ ...preferences, pollutionSensitivity: Number(event.target.value) })}/></label>
            <button className="button primary" type="submit">Save preferences</button>
          </form>
        </article>
      </div> : <section className="panel activity-signin"><div className="activity-signin-mark">↗</div><p className="eyebrow">YOUR ROUTES, IN ONE PLACE</p><h2>Sign in to see your activity</h2><p>Save places, keep useful routes, and check your recent air-quality alerts.</p><button className="button primary" onClick={() => setActivePage('planner')}>Sign in or create account</button></section>}
      </section>}

      <footer className="app-footer"><span>CleanRoute <i>·</i> Move well. Breathe easier.</span><span>Environmental values are comparative estimates, not health guidance.</span></footer>
    </main>
  )
}

export default App
