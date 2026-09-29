import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { EnvironmentalStatus } from './EnvironmentalStatus'

afterEach(() => cleanup())

describe('EnvironmentalStatus', () => {
  it.each([
    ['complete', 'Complete coverage'],
    ['partial', 'Partial environmental coverage'],
    ['unavailable', 'Environmental data unavailable'],
  ])('communicates %s coverage without inventing a value', (coverage, label) => {
    render(<EnvironmentalStatus coverage={coverage} source="open-meteo-air-quality" availableSamples={coverage === 'complete' ? 4 : 2} sampledPoints={4} />)

    expect(screen.getByText(label)).toBeInTheDocument()
    expect(screen.getByText(new RegExp(`${coverage === 'complete' ? '4' : '2'}/4 samples`))).toBeInTheDocument()
  })

  it('explains that partial coverage uses available samples only', () => {
    render(<EnvironmentalStatus coverage="partial" availableSamples={2} sampledPoints={4} />)

    expect(screen.getByText(/some route samples were unavailable/i)).toBeInTheDocument()
    expect(screen.getByText('2/4 samples')).toBeInTheDocument()
  })
})
