import { expect, it } from 'vitest'
import { TokenVisualCatalog, type TokenVisualToken } from './TokenVisualCatalog'

const token = (overrides: Partial<TokenVisualToken> = {}): TokenVisualToken => ({
  id: 'token-1',
  type: 'ENEMY',
  x: 2,
  y: 3,
  ...overrides,
})

it('maps every existing token type to a bundled local image and a faction frame', () => {
  const catalog = new TokenVisualCatalog()

  for (const type of ['PLAYER', 'FRIENDLY_NPC', 'NEUTRAL_NPC', 'ENEMY', 'BOSS', 'TRAP', 'OBJECT']) {
    const visual = catalog.resolve(token({ id: type, type }))

    expect(visual.assetPath).toMatch(/^\/assets\/tokens\/.+\.svg$/)
    expect(visual.framePath).toBe('/assets/tokens/token-frame.svg')
    expect(visual.assetPath).not.toMatch(/^https?:\/\//)
    expect(visual.faction).toBeDefined()
  }
})

it('keeps the faction visible while choosing the strongest overlapping status', () => {
  const catalog = new TokenVisualCatalog()
  const visual = catalog.resolve(token({ type: 'PLAYER', selected: true, currentTurn: true, lastSeen: true }))

  expect(visual.faction).toBe('PLAYER')
  expect(visual.primaryStatus).toBe('CURRENT_TURN')
  expect(visual.classes).toEqual(expect.arrayContaining([
    'token-faction-player',
    'token-status-current-turn',
    'token-status-selected',
    'token-status-last-seen',
  ]))
  expect(visual.isThumbnail).toBe(true)
})

it('uses the selected and current-turn context when the server token has no display flags', () => {
  const catalog = new TokenVisualCatalog()
  const visual = catalog.resolve(token({ id: 'player-1', type: 'PLAYER' }), {
    selectedTokenId: 'player-1',
    currentTurnTokenId: 'player-1',
  })

  expect(visual.primaryStatus).toBe('CURRENT_TURN')
  expect(visual.isThumbnail).toBe(false)
})
