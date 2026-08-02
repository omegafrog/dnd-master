import { describe, expect, it } from 'vitest'
import { classCreationRule, inferArmorLoadout, resolveEquipment, savingThrowBonuses, spellAttackBonus, spellSaveDc } from './Dnd5eCharacterDerivedRules'
import type { AbilityScores } from './Dnd5eRules'

const modifiers: AbilityScores = { strength: 2, dexterity: 3, constitution: 1, intelligence: 4, wisdom: 2, charisma: 0 }

describe('Dnd5eCharacterDerivedRules', () => {
  it('resolves one option from every equipment choice group', () => {
    expect(resolveEquipment('파이터', { armor: 'chain', weapons: 'shield', ranged: 'crossbow', pack: '탐험가' }))
      .toEqual(['체인 메일', '군용 무기 1개', '방패', '라이트 크로스보우', '볼트 20개', '탐험가 꾸러미'])
  })

  it('infers equipped armor and shield from selected starting equipment', () => {
    expect(inferArmorLoadout(['체인 메일', '군용 무기 1개', '방패'])).toEqual({
      equippedArmor: '체인 메일', equippedShield: true,
    })
    expect(inferArmorLoadout(['가죽 갑옷', '장궁'])).toEqual({
      equippedArmor: '가죽 갑옷', equippedShield: false,
    })
  })

  it('derives spell attack and save DC from class ability and proficiency', () => {
    expect(spellAttackBonus('위저드', modifiers, 2)).toBe(6)
    expect(spellSaveDc('위저드', modifiers, 2)).toBe(14)
    expect(spellAttackBonus('파이터', modifiers, 2)).toBeNull()
  })

  it('adds proficiency only to class saving throws', () => {
    expect(savingThrowBonuses(modifiers, ['strength', 'constitution'], 2)).toEqual({
      strength: 4, dexterity: 3, constitution: 3, intelligence: 4, wisdom: 2, charisma: 0,
    })
  })

  it('declares required equipment groups and spell counts by class', () => {
    expect(classCreationRule('클레릭')?.equipmentGroups).toHaveLength(4)
    expect(classCreationRule('클레릭')?.cantripCount).toBe(3)
    expect(classCreationRule('위저드')?.firstLevelSpellCount).toBe(6)
  })
})
