import { describe, expect, it, vi } from 'vitest'
import { AdventureSessionApi } from './AdventureSessionApi'

describe('AdventureSessionApi', () => {
  it('creates a session from the published package and blueprint revision', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ sessionId: 'session-1' }), { status: 200 })))
    await new AdventureSessionApi('token').create({ scenarioPackageId: 'package-1', blueprintId: 'blueprint-1', blueprintRevision: 3 })
    expect(fetch).toHaveBeenCalledWith('/api/v1/adventure-sessions', expect.objectContaining({ method: 'POST', body: JSON.stringify({ scenarioPackageId: 'package-1', blueprintId: 'blueprint-1', blueprintRevision: 3 }) }))
  })
})
