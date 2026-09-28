import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import App from './App'

vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: { children: ReactNode }) => <div aria-label="City map">{children}</div>,
  Marker: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  Polyline: () => <div />,
  TileLayer: () => <div />,
  Tooltip: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  useMap: () => ({ fitBounds: vi.fn() }),
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
  it('renders route planning, map, and an account entry point', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => undefined)))
    render(<App />)
    expect(screen.getByRole('heading', { name: /choose a clearer way through the city/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Route preferences' })).toBeInTheDocument()
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
})
