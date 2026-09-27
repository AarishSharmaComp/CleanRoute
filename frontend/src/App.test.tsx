import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('CleanRoute shell', () => {
  it('confirms the frontend is running', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'CleanRoute is running' })).toBeTruthy()
    expect(screen.getByRole('status')).toHaveTextContent('Frontend service is online')
  })
})
