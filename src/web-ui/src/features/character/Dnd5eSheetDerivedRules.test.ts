import { describe, expect, it } from 'vitest'
import { expertiseChoiceCount, firstLevelSpellSlots, passivePerception, skillBonuses, uniqueProficiencies } from './Dnd5eSheetDerivedRules'
import type { AbilityScores } from './Dnd5eRules'

const modifiers: AbilityScores = { strength: 2, dexterity: 3, constitution: 1, intelligence: 0, wisdom: 2, charisma: -1 }

describe('Dnd5eSheetDerivedRules', () => {
  it('adds proficiency once and expertise twice to skill bonuses', () => {
    const skills = skillBonuses(modifiers, 2, ['지각', '은신', '지각'], ['은신'])
    expect(skills.find(skill => skill.label === '지각')?.bonus).toBe(4)
    expect(skills.find(skill => skill.label === '은신')?.bonus).toBe(7)
    expect(skills.find(skill => skill.label === '운동')?.bonus).toBe(2)
  })

  it('derives passive perception from the final perception bonus', () => {
    const skills = skillBonuses(modifiers, 2, ['지각'])
    expect(passivePerception(skills)).toBe(14)
  })

  it('declares first-level spell slots by class', () => {
    expect(firstLevelSpellSlots('위저드')).toEqual([{ level: 1, slots: 2 }])
    expect(firstLevelSpellSlots('워락')).toEqual([{ level: 1, slots: 1 }])
    expect(firstLevelSpellSlots('파이터')).toEqual([])
  })

  it('requires rogue expertise choices and deduplicates proficiency sources', () => {
    expect(expertiseChoiceCount('로그')).toBe(2)
    expect(expertiseChoiceCount('바드')).toBe(0)
    expect(uniqueProficiencies(['지각', '은신'], ['지각', '통찰'])).toEqual(['지각', '은신', '통찰'])
  })
})
