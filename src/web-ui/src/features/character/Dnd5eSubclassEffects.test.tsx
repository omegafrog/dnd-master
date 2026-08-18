import { describe, expect, it } from 'vitest'
import { applySubclassArmorClass, subclassEffects } from './Dnd5eSubclassEffects'
import type { AbilityScores } from './Dnd5eRules'

const modifiers: AbilityScores = { strength: 0, dexterity: 3, constitution: 2, intelligence: 1, wisdom: 2, charisma: 4 }

describe('Dnd5eSubclassEffects', () => {
  it('grants the life domain heavy armor from dnd5th.pdf', () => {
    expect(subclassEffects('생명 권역', modifiers).armorProficiencies).toContain('중갑')
  })

  it('does not invent effects for subclasses without detail in dnd5th.pdf', () => {
    const effects = subclassEffects('챔피언', modifiers)
    expect(applySubclassArmorClass(13, '', effects)).toBe(13)
    expect(applySubclassArmorClass(14, '스케일 메일', effects)).toBe(14)
  })
})
