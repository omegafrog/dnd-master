import { afterEach, describe, expect, it, vi } from 'vitest'
import { evaluateCharacterBuild, getCharacterRulesCatalog } from './CharacterRulesApi'

afterEach(() => vi.unstubAllGlobals())

describe('CharacterRulesApi', () => {
  it('loads the authoritative edition catalog', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      edition: 'DND_5E_2014', baseSchema: 'DND_5E_2014', revision: 3,
      races: ['인간'], classes: ['파이터'], backgrounds: ['군인'],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    const catalog = await getCharacterRulesCatalog()

    expect(catalog.revision).toBe(3)
    expect(catalog.classes).toEqual(['파이터'])
    expect(fetchMock).toHaveBeenCalledWith('/internal/v1/character-rules/catalogs/DND_5E_2014', expect.any(Object))
  })

  it('posts a non-persisting build evaluation request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      valid: false, derived: { armorClass: 14 }, violations: [{
        code: 'DRUID_METAL_ARMOR_RESTRICTION', category: 'CHARACTER_RULE', severity: 'ERROR',
        message: '드루이드는 금속 갑옷을 장착할 수 없습니다.', parameters: {},
      }],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await evaluateCharacterBuild('session-1', {
      sessionId: 'session-1', edition: 'DND_5E_2014', characterName: '아리아', level: 1,
      inspiration: false, race: '인간', characterClass: '드루이드', background: '은둔자',
    })

    expect(result.valid).toBe(false)
    expect(result.violations[0].code).toBe('DRUID_METAL_ARMOR_RESTRICTION')
    expect(fetchMock).toHaveBeenCalledWith(
      '/internal/v1/adventure-sessions/session-1/character-builds/evaluate',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
