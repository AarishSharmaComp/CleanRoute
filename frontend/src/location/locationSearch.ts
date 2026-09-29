export type LocationSearchResult = {
  name: string
  displayName: string
  context: string
  latitude: number
  longitude: number
  supportedArea: boolean
}

export interface LocationSearchProvider {
  search(query: string, signal?: AbortSignal): Promise<LocationSearchResult[]>
}

/** Frontend adapter for CleanRoute's backend geocoding endpoint. */
export class BackendLocationSearchProvider implements LocationSearchProvider {
  constructor(private readonly endpoint = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080') {}

  async search(query: string, signal?: AbortSignal): Promise<LocationSearchResult[]> {
    const normalizedQuery = query.trim()
    if (normalizedQuery.length < 3) return []

    const url = new URL('/api/places/search', this.endpoint)
    url.searchParams.set('q', normalizedQuery)
    const response = await fetch(url, { headers: { Accept: 'application/json' }, signal })
    if (!response.ok) throw new Error('Location search is temporarily unavailable.')
    const body = await response.json() as LocationSearchResult[]
    return Array.isArray(body) ? body.slice(0, 6).filter(result =>
      typeof result.name === 'string' && typeof result.displayName === 'string'
      && typeof result.context === 'string' && Number.isFinite(result.latitude)
      && Number.isFinite(result.longitude) && typeof result.supportedArea === 'boolean') : []
  }
}
