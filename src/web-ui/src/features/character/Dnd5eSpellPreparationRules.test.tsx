import { describe, expect, it } from 'vitest'
import { domainSpells, spellSelectionRule } from './Dnd5eSpellPreparationRules'
import type { AbilityScores } from './Dnd5eRules'

const modifiers: AbilityScores = { strength: 0, dexterity: 2, constitution: 1, intelligence: 3, wisdom: 3, charisma: 4 }

describe('Dnd5eSpellPreparationRules', () => {
  it('distinguishes known, prepared, spellbook and pact models', () => {
    expect(spellSelectionRule('바드', modifiers)?.model).toBe('KNOWN')
    expect(spellSelectionRule('클레릭', modifiers)).toMatchObject({ model: 'PREPARED', preparedSpellCount: 4 })
    expect(spellSelectionRule('위저드', modifiers)).toMatchObject({ model: 'SPELLBOOK', learnedSpellCount: 6, preparedSpellCount: 4 })
    expect(spellSelectionRule('워락', modifiers)).toMatchObject({ model: 'PACT', firstLevelSlots: 1, recovery: 'SHORT_REST' })
  })

  it('adds only the life-domain spells detailed in dnd5th.pdf', () => {
    expect(domainSpells('생명 권역')).toEqual(['축복', '상처 치료'])
    expect(domainSpells('대마족')).toEqual([])
    expect(domainSpells('빛 권역')).toEqual([])
  })
})
