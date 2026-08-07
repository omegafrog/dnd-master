import { describe, expect, it, vi } from 'vitest'
import { AdventureSessionApi } from './AdventureSessionApi'

describe('AdventureSessionApi', () => {
  it('creates a session from the published package and blueprint revision', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ sessionId: 'session-1' }), { status: 200 })))
    await new AdventureSessionApi('token').create({ scenarioPackageId: 'package-1', blueprintId: 'blueprint-1', blueprintRevision: 3 })
    expect(fetch).toHaveBeenCalledWith('/api/v1/adventure-sessions', expect.objectContaining({ method: 'POST', body: JSON.stringify({ scenarioPackageId: 'package-1', blueprintId: 'blueprint-1', blueprintRevision: 3 }) }))
  })

  it('does not expose provider response bodies to the player', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('secret prompt and stack trace', { status: 503 })))
    await expect(new AdventureSessionApi('token').generateStoryPlan('session-1'))
      .rejects.toThrow('GM provider를 사용할 수 없습니다. 잠시 후 다시 시도하세요.')
    await expect(new AdventureSessionApi('token').generateStoryPlan('session-1'))
      .rejects.not.toThrow('secret prompt')
  })
})
