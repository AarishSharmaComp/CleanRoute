export type LocationSearchResult = {
  name: string
  displayName: string
  context: string
  latitude: number
  longitude: number
}

export interface LocationSearchProvider {
  search(query: string, signal?: AbortSignal): Promise<LocationSearchResult[]>
}

type PhotonFeature = {
  geometry?: { coordinates?: unknown }
  properties?: Record<string, unknown>
}

type PhotonResponse = { features?: PhotonFeature[] }

/** Provider adapter for Photon, an OpenStreetMap based geocoder. */
export class PhotonLocationSearchProvider implements LocationSearchProvider {
  constructor(private readonly endpoint = import.meta.env.VITE_PHOTON_API_URL || 'https://photon.komoot.io/api/') {}

  async search(query: string, signal?: AbortSignal): Promise<LocationSearchResult[]> {
    const normalizedQuery = query.trim()
    if (normalizedQuery.length < 3) return []

    const url = new URL(this.endpoint)
    url.searchParams.set('q', normalizedQuery)
    url.searchParams.set('limit', '6')
    url.searchParams.set('lang', 'en')
    const response = await fetch(url, { headers: { Accept: 'application/json' }, signal })
    if (!response.ok) throw new Error('Location search is temporarily unavailable.')

    const body = await response.json() as PhotonResponse
    const results = (body.features ?? []).flatMap(feature => {
      const properties = feature.properties ?? {}
      const coordinates = feature.geometry?.coordinates
      if (!Array.isArray(coordinates) || coordinates.length < 2) return []
      const longitude = Number(coordinates[0])
      const latitude = Number(coordinates[1])
      if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
          || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) return []

      const labels = [properties.name, properties.district, properties.city, properties.county,
        properties.state, properties.country, properties.postcode]
        .filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
        .map(value => value.trim())
      const name = labels[0]
      if (!name) return []
      const context = [...new Set(labels.slice(1).filter(value => value.toLocaleLowerCase() !== name.toLocaleLowerCase()))]
        .join(', ')
      return [{ name, context, displayName: [name, context].filter(Boolean).join(', '), latitude, longitude }]
    })
    return results.filter((result, index) => results.findIndex(other =>
      other.displayName === result.displayName && other.latitude === result.latitude && other.longitude === result.longitude) === index)
  }
}
