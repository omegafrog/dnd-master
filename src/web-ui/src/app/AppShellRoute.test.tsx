import { describe, expect, it } from 'vitest'
import { parseRoute } from './route'

describe('AppShell preparation routes', () => {
  it('routes session party preparation to dedicated party page', () => {
    expect(parseRoute('#/sessions/session-1/party')).toEqual({ page: 'party', sessionId: 'session-1' })
  })

})
