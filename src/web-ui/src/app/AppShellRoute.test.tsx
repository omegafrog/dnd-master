import { describe, expect, it } from 'vitest'
import { parseRoute } from './route'

describe('AppShell preparation routes', () => {
  it('keeps the old setup address on the adventure list', () => {
    expect(parseRoute('#/setup')).toEqual({ page: 'adventures' })
    expect(parseRoute('#/setup?mode=create')).toEqual({ page: 'setup' })
  })

  it('routes session party preparation to dedicated party page', () => {
    expect(parseRoute('#/sessions/session-1/party')).toEqual({ page: 'party', sessionId: 'session-1' })
  })

  it('opens an adventure preparation tab without changing the adventure identity', () => {
    expect(parseRoute('#/adventures/adventure-1?tab=review')).toEqual({ page: 'adventure-workspace', adventureId: 'adventure-1', tab: 'review' })
  })

  it('keeps the existing adventure play route compatible when no preparation tab is selected', () => {
    expect(parseRoute('#/adventures/adventure-1')).toEqual({ page: 'adventure', adventureId: 'adventure-1' })
  })

  it('routes an explicitly started session to the play screen', () => {
    expect(parseRoute('#/sessions/session-1?mode=play')).toEqual({ page: 'session-runtime', sessionId: 'session-1' })
  })

})
