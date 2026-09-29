type Coverage = 'complete' | 'partial' | 'unavailable' | 'fixed-cell'

type Props = {
  coverage?: string | null
  source?: string | null
  availableSamples?: number
  sampledPoints?: number
  compact?: boolean
}

const labels: Record<Coverage, { title: string; detail: string }> = {
  complete: { title: 'Complete coverage', detail: 'Environmental samples were available across this route.' },
  partial: { title: 'Partial environmental coverage', detail: 'Some route samples were unavailable; exposure uses available samples only.' },
  unavailable: { title: 'Environmental data unavailable', detail: 'No environmental exposure value is available for this route.' },
  'fixed-cell': { title: 'Modeled exposure', detail: 'Fixed-cell historical forecast; not roadside sensor data or coordinate-level pollution measurement.' },
}

export function EnvironmentalStatus({ coverage, source, availableSamples, sampledPoints, compact = false }: Props) {
  const key = (coverage && coverage in labels ? coverage : 'unavailable') as Coverage
  const label = labels[key]
  const sampleText = sampledPoints && sampledPoints > 0
    ? `${availableSamples ?? 0}/${sampledPoints} samples`
    : null

  return <div className={`environmental-status environmental-${key}`} title={label.detail}>
    <span className="environmental-status-mark" aria-hidden="true">{key === 'complete' ? '✓' : key === 'partial' ? '◐' : key === 'fixed-cell' ? '◌' : '—'}</span>
    <span className="environmental-status-copy">
      <strong>{label.title}</strong>
      {!compact && <small>{label.detail}</small>}
      {(source || sampleText) && <small className="environmental-meta">{source ? source.replaceAll('-', ' ') : ''}{source && sampleText ? ' · ' : ''}{sampleText}</small>}
    </span>
  </div>
}
