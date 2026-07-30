import { describe, expect, it } from 'vitest'
import { calculateDnd5eCharacter } from './Dnd5eRules'

describe('calculateDnd5eCharacter', () => {
  it('applies race ability bonuses and derives first-level fighter statistics', () => {
    expect(calculateDnd5eCharacter({
      race: 'Dwarf',
      characterClass: 'Fighter',
      level: 1,
      baseAbilities: { strength: 15, dexterity: 12, constitution: 14, intelligence: 8, wisdom: 10, charisma: 13 },
    })).toMatchObject({
      abilityScores: { constitution: 16 },
      abilityModifiers: { constitution: 3, dexterity: 1 },
      hitDie: 'd10',
      hitPointMaximum: 13,
      proficiencyBonus: 2,
      speed: 25,
      armorClass: 11,
      savingThrowProficiencies: ['strength', 'constitution'],
    })
  })

  it('exposes conditional advantage traits from the selected race', () => {
    expect(calculateDnd5eCharacter({
      race: 'Halfling', characterClass: 'Rogue', level: 1,
      baseAbilities: { strength: 8, dexterity: 15, constitution: 14, intelligence: 12, wisdom: 10, charisma: 13 },
    }).conditionalTraits).toContainEqual(expect.objectContaining({
      kind: 'ADVANTAGE', trigger: 'frightened saving throw',
    }))
  })

  it('applies subrace, equipped armor and level-up average HP', () => {
    expect(calculateDnd5eCharacter({
      race: 'Elf', subrace: 'Wood Elf', characterClass: 'Ranger', level: 3,
      baseAbilities: { strength: 10, dexterity: 14, constitution: 12, intelligence: 8, wisdom: 15, charisma: 10 },
      equippedArmor: 'scale mail', equippedShield: true,
      hitPointIncreases: [{ method: 'AVERAGE' }, { method: 'AVERAGE' }],
    })).toMatchObject({
      abilityScores: { dexterity: 16, wisdom: 16 }, speed: 35, armorClass: 18,
      hitDie: 'd10', hitPointMaximum: 25,
    })
  })

  it('requires an actual rolled HP result instead of inventing it', () => {
    expect(calculateDnd5eCharacter({
      race: 'Human', characterClass: 'Barbarian', level: 2,
      baseAbilities: { constitution: 14 }, hitPointIncreases: [{ method: 'ROLL' }],
    }).notes).toContain('2레벨 HP 굴림 결과를 입력해야 합니다.')
  })
})
