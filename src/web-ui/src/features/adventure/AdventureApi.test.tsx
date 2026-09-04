import { describe, expect, it, vi } from 'vitest'
import { HttpAdventureApi } from './AdventureApi'

describe('Adventure event projection', () => {
  it('submits text through typed turns with version and idempotency headers', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ narration: 'ok' }), { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)
    await new HttpAdventureApi(() => 'token-1').sendMessage('a1', 'look', { turnId: 't1', commandId: 'c1' }, 7)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/adventures/a1/turns', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer token-1', 'Idempotency-Key': 'c1', 'If-Match-Version': '7' }),
      body: JSON.stringify({ turnId: 't1', input: { type: 'TEXT', text: 'look' } }),
    }))
  })

  it('uses bearer fetch stream and parses committed events', async () => {
    const encoded = new TextEncoder().encode('id: 8\nevent: GM_TURN_COMMITTED\ndata: turn-8\n\n')
    const fetchMock = vi.fn().mockResolvedValue(new Response(new ReadableStream({
      start(controller) { controller.enqueue(encoded); controller.close() },
    })))
    vi.stubGlobal('fetch', fetchMock)
    const received: unknown[] = []

    const close = new HttpAdventureApi(() => 'token-1')
      .subscribeEvents!('adventure-1', 7, event => received.push(event))
    await vi.waitFor(() => expect(received).toEqual([{ version: 8, type: 'GM_TURN_COMMITTED', payload: 'turn-8' }]))
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/adventures/adventure-1/events?afterVersion=7', expect.objectContaining({
      headers: { Authorization: 'Bearer token-1' },
    }))
    close()
  })
})
