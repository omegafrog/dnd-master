import { describe, expect, it } from 'vitest'
import { applySubclassArmorClass, subclassEffects } from './Dnd5eSubclassEffects'
import type { AbilityScores } from './Dnd5eRules'

const modifiers: AbilityScores = { strength: 0, dexterity: 3, constitution: 2, intelligence: 1, wisdom: 2, charisma: 4 }

describe('Dnd5eSubclassEffects', () => {
  it('grants life domain heavy armor and light domain cantrip', () => {
    expect(subclassEffects('생명 권역', modifiers).armorProficiencies).toContain('중갑')
    expect(subclassEffects('빛 권역', modifiers).bonusCantrips).toContain('빛')
  })

  it('applies draconic resilience only while unarmored', () => {
    const effects = subclassEffects('용의 혈통', modifiers)
    expect(applySubclassArmorClass(13, '', effects)).toBe(16)
    expect(applySubclassArmorClass(14, '스케일 메일', effects)).toBe(14)
  })
})
