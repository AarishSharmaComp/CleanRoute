import { useEffect, useState } from 'react'
import type { LocationSearchProvider, LocationSearchResult } from './locationSearch'

export type LocationInput = { query: string; selected: LocationSearchResult | null }

type Props = {
  label: string
  placeholder: string
  value: LocationInput
  provider: LocationSearchProvider
  onChange: (value: LocationInput) => void
}

export function LocationSearchField({ label, placeholder, value, provider, onChange }: Props) {
  const [results, setResults] = useState<LocationSearchResult[]>([])
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    const query = value.query.trim()
    if (value.selected || query.length < 3) {
      setResults([])
      setLoading(false)
      setMessage(query.length > 0 && !value.selected ? 'Type at least 3 characters to search.' : '')
      return
    }
    const controller = new AbortController()
    const timer = window.setTimeout(async () => {
      setLoading(true)
      setMessage('')
      try {
        const found = await provider.search(query, controller.signal)
        setResults(found)
        setMessage(found.length ? '' : 'No places found. Try a city, address, or landmark.')
      } catch (error) {
        if (!controller.signal.aborted) {
          setResults([])
          setMessage('Location search is temporarily unavailable. Check your connection and try again.')
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }, 400)
    return () => { window.clearTimeout(timer); controller.abort() }
  }, [provider, value.query, value.selected])

  return <div className="location-field">
    <label htmlFor={`location-${label.toLowerCase()}`}>{label}</label>
    <input id={`location-${label.toLowerCase()}`} type="search" autoComplete="off" role="combobox"
      aria-autocomplete="list" aria-expanded={results.length > 0} aria-controls={`suggestions-${label.toLowerCase()}`}
      placeholder={placeholder} value={value.query}
      onChange={event => { onChange({ query: event.target.value, selected: null }); setResults([]); setMessage('') }} />
    {value.selected && <span className="location-selected" role="status">Selected: {value.selected.displayName}</span>}
    {loading && <span className="location-feedback" role="status">Searching locations…</span>}
    {message && !loading && <span className="location-feedback" role="status">{message}</span>}
    {results.length > 0 && <ul className="location-suggestions" id={`suggestions-${label.toLowerCase()}`} role="listbox">
      {results.map((result, index) => <li key={`${result.latitude}-${result.longitude}-${index}`} role="option" aria-selected="false">
        <button type="button" onClick={() => { onChange({ query: result.displayName, selected: result }); setResults([]); setMessage('') }}>
          <strong>{result.name}</strong><span>{result.context || result.displayName}</span>
        </button>
      </li>)}
    </ul>}
  </div>
}
