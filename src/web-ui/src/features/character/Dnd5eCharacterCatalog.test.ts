import { describe, expect, it } from 'vitest'
import { backgroundOptions, classOptions, raceOptions } from './Dnd5eCharacterCatalog'
import { classCreationRule } from './Dnd5eCharacterDerivedRules'

const baseClasses = ['로그', '위저드', '클레릭', '파이터']

describe('Dnd5eCharacterCatalog', () => {
  it('contains all twelve base classes with creation rules', () => {
    expect(classOptions.map(option => option.id)).toEqual(baseClasses)
    for (const characterClass of baseClasses) {
      const rule = classCreationRule(characterClass)
      expect(rule).toBeDefined()
      expect(rule?.equipmentGroups.length).toBeGreaterThan(0)
      expect(new Set(rule?.equipmentGroups.map(group => group.id)).size).toBe(rule?.equipmentGroups.length)
    }
  })

  it('separates cantrips and first-level spells for first-level casters', () => {
    for (const option of classOptions.filter(item => item.canCastSpells)) {
      expect(option.cantrips.length).toBeGreaterThan(0)
      expect(option.firstLevelSpells.length).toBeGreaterThan(0)
      expect(option.cantrips.some(spell => option.firstLevelSpells.includes(spell))).toBe(false)
    }
  })

  it('provides languages, traits, and parent-scoped subraces', () => {
    for (const race of raceOptions) {
      expect(race.languages.length).toBeGreaterThan(0)
      expect(Array.isArray(race.traits)).toBe(true)
      for (const subrace of race.subraces) expect(subrace.traits.length).toBeGreaterThan(0)
    }
    expect(raceOptions.find(race => race.id === '인간')?.subraces).toEqual([])
  })

  it('provides the standard backgrounds with descriptions and automatic equipment', () => {
    expect(backgroundOptions.length).toBeGreaterThanOrEqual(13)
    for (const background of backgroundOptions) {
      expect(background.description).not.toBe('')
      expect(background.skills).toHaveLength(2)
      expect(background.equipment.length).toBeGreaterThan(0)
    }
  })
})
