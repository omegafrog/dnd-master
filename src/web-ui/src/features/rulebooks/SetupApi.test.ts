import { describe, expect, it } from 'vitest'
import { normalizeScenarioCompilation } from './SetupApi'

describe('normalizeScenarioCompilation', () => {
  it('keeps a retryable compilation in WAITING_RETRY even when it has a failure reason', () => {
    expect(normalizeScenarioCompilation({
      compilationId: 'compilation-1',
      bundleId: 'bundle-1',
      bundleRevision: 1,
      status: 'WAITING_RETRY',
      attempt: 1,
      failureReason: 'resolution extraction failed',
    }).status).toBe('WAITING_RETRY')
  })

  it('keeps a terminal failed compilation as FAILED', () => {
    expect(normalizeScenarioCompilation({
      compilationId: 'compilation-2',
      bundleId: 'bundle-1',
      bundleRevision: 1,
      status: 'FAILED',
      attempt: 3,
      failureReason: 'resolution extraction failed',
    }).status).toBe('FAILED')
  })
})
