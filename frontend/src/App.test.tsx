import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import App from './App'
import { PhotonLocationSearchProvider } from './location/locationSearch'

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: { children: ReactNode }) => <div aria-label="City map">{children}</div>,
  Marker: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  Polyline: () => <div />,
  TileLayer: () => <div />,
  Tooltip: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  useMap: () => ({ fitBounds: vi.fn(), setView: vi.fn() }),
}))
vi.mock('leaflet', () => ({ default: { divIcon: vi.fn(() => ({})) } }))
vi.mock('recharts', () => ({
  CartesianGrid: () => <div />, Legend: () => <div />, Line: () => <div />, LineChart: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>, Tooltip: () => <div />, XAxis: () => <div />, YAxis: () => <div />,
}))

describe('CleanRoute dashboard', () => {
  let storedToken: string | null
  beforeEach(() => {
    storedToken = null
    vi.stubGlobal('localStorage', { getItem: () => storedToken, setItem: vi.fn(), removeItem: vi.fn() })
  })
  afterEach(() => cleanup())
  it('renders route planning, map, and an account entry point', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => undefined)))
    render(<App />)
    expect(screen.getByRole('heading', { name: /choose a clearer way through the city/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'From here to there' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Route planning' })).toBeInTheDocument()
    expect(screen.getByLabelText('City map')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('loads environmental data and the authenticated account dashboard from the API', async () => {
    storedToken = 'test-session-token'
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = new URL(String(input)).pathname
      const responseByPath: Record<string, unknown> = {
        '/api/aqi/current': { cellId: 'demo-delhi-central', observedAt: '2026-09-28T08:00:00Z', aqi: 42, pm25: 12, generated: true },
        '/api/aqi/history': [],
        '/api/pollution/forecast': [],
        '/api/dashboard': { savedPlaces: [], savedRoutes: [], routeHistory: [], notifications: [], unreadNotificationCount: 0 },
        '/api/users/me': { preferences: { preferredTravelMode: 'WALK', routePreference: 'BALANCED', notificationsEnabled: true, pollutionSensitivity: 3 } },
      }
      return { ok: true, status: 200, json: async () => responseByPath[path] } as Response
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByRole('region', { name: 'Account dashboard' })).toBeInTheDocument()
    expect(await screen.findByText(/No alerts yet/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/dashboard'), expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer test-session-token' }),
    }))
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/aqi/current'), expect.anything())
  })

  it('searches and selects places, swaps endpoints, and sends selected coordinates using existing API enums', async () => {
    vi.stubGlobal('localStorage', { getItem: () => 'test-session-token', setItem: vi.fn(), removeItem: vi.fn() })
    let routeBody: Record<string, unknown> | null = null
    const suggestions = {
      features: [
        { geometry: { coordinates: [77.209, 28.614] }, properties: { name: 'Delhi', city: 'Delhi', country: 'India' } },
        { geometry: { coordinates: [77.391, 28.535] }, properties: { name: 'Noida', state: 'Uttar Pradesh', country: 'India' } },
      ],
    }
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input))
      if (url.hostname === 'photon.komoot.io') return { ok: true, status: 200, json: async () => suggestions } as Response
      if (url.pathname === '/api/routes/calculate') {
        routeBody = JSON.parse(String(init?.body)) as Record<string, unknown>
        return { ok: true, status: 200, json: async () => ({
          id: 'calculation-1', preference: 'CLEANEST', generated: true, alternatives: [{
            alternativeId: 'clean-route', rank: 1, provider: 'mock', generated: true,
            geometry: [{ latitude: 28.614, longitude: 77.209 }, { latitude: 28.535, longitude: 77.391 }],
            distanceMeters: 14200, durationSeconds: 1920, expectedPollutionExposure: 31,
            forecastQualityScore: 70, preferenceScore: 82, scoreComponents: { pollutionCleanliness: 74 },
            reasons: ['Lower modeled pollution exposure.'],
          }],
        }) } as Response
      }
      const responseByPath: Record<string, unknown> = {
        '/api/aqi/current': { cellId: 'demo-delhi-central', observedAt: '2026-09-28T08:00:00Z', aqi: 42, pm25: 12, generated: true },
        '/api/aqi/history': [], '/api/pollution/forecast': [],
        '/api/dashboard': { savedPlaces: [], savedRoutes: [], routeHistory: [], notifications: [], unreadNotificationCount: 0 },
        '/api/users/me': { preferences: { preferredTravelMode: 'WALK', routePreference: 'BALANCED', notificationsEnabled: true, pollutionSensitivity: 3 } },
      }
      return { ok: true, status: 200, json: async () => responseByPath[url.pathname] } as Response
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    const from = screen.getByLabelText('From')
    const to = screen.getByLabelText('To')
    const find = screen.getByRole('button', { name: 'Find clean routes' })
    expect(find).toBeDisabled()
    fireEvent.change(from, { target: { value: 'Delhi' } })
    fireEvent.click(within(await screen.findByRole('option', { name: /Delhi/ })).getByRole('button'))
    fireEvent.change(to, { target: { value: 'Noida' } })
    fireEvent.click(within(await screen.findByRole('option', { name: /Noida/ })).getByRole('button'))
    expect(find).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: 'Swap origin and destination' }))
    expect(from).toHaveValue('Noida, Uttar Pradesh, India')
    expect(to).toHaveValue('Delhi, India')
    fireEvent.click(screen.getByRole('button', { name: /Driving/ }))
    fireEvent.click(screen.getByRole('button', { name: /Cleanest/ }))
    fireEvent.click(find)

    expect(await screen.findByRole('heading', { name: /CLEANEST alternatives/ })).toBeInTheDocument()
    expect(routeBody).toEqual({
      origin: { latitude: 28.535, longitude: 77.391 }, destination: { latitude: 28.614, longitude: 77.209 }, mode: 'CAR', preference: 'CLEANEST',
    })
    const card = screen.getByRole('heading', { name: /clean route/i }).closest('article')!
    expect(within(card).getByText('14.2 km')).toBeInTheDocument()
    expect(within(card).getByText('32 min')).toBeInTheDocument()
    expect(within(card).getByText('31.0')).toBeInTheDocument()
    expect(within(card).getByText('Data unavailable')).toBeInTheDocument()
    expect(within(card).getByText('Lower modeled pollution exposure.')).toBeInTheDocument()
    expect(screen.getByText(/not Google Maps directions/i)).toBeInTheDocument()
  })
})

describe('Photon location-search adapter', () => {
  it('normalizes OSM place names and filters invalid coordinates', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) => ({ ok: true, json: async () => ({ features: [
      { geometry: { coordinates: [77.2, 28.6] }, properties: { name: 'Delhi', city: 'Delhi', country: 'India' } },
      { geometry: { coordinates: [300, 91] }, properties: { name: 'Invalid' } },
    ] }) }) as Response)
    vi.stubGlobal('fetch', fetchMock)
    const result = await new PhotonLocationSearchProvider().search(' Delhi ')
    expect(result).toEqual([{ name: 'Delhi', context: 'India', displayName: 'Delhi, India', latitude: 28.6, longitude: 77.2 }])
    expect(String(fetchMock.mock.calls[0][0])).toContain('q=Delhi')
  })

  it('does not call the geocoder for empty or too-short searches', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const provider = new PhotonLocationSearchProvider()
    expect(await provider.search('  ')).toEqual([])
    expect(await provider.search('NY')).toEqual([])
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
