import { describe, expect, it } from 'vitest'
import { calculateAttacks, resolvedWeaponIds, unresolvedWeaponSlots, weaponChoices } from './Dnd5eWeaponRules'

describe('Dnd5eWeaponRules', () => {
  it('turns generic simple and martial equipment into explicit weapon slots', () => {
    expect(unresolvedWeaponSlots(['군용 무기 2개', '단순 근접 무기 1개'])).toEqual([
      { id: 'martial-0', label: '군용 무기 2개', category: 'MARTIAL', count: 2 },
      { id: 'simple-1', label: '단순 근접 무기 1개', category: 'SIMPLE', count: 1 },
    ])
  })

  it('combines fixed equipment weapons with selected generic slots', () => {
    expect(resolvedWeaponIds(['레이피어', '군용 무기 1개'], { 'martial-1': ['longbow'] }))
      .toEqual(['rapier', 'longbow'])
  })

  it('uses dexterity for ranged weapons and the better ability for finesse weapons', () => {
    const attacks = calculateAttacks(['longbow', 'rapier', 'greatsword'], {
      strength: 2, dexterity: 4, constitution: 1, intelligence: 0, wisdom: 0, charisma: 0,
    }, 2)
    expect(attacks).toEqual([
      expect.objectContaining({ label: '장궁', attackBonus: 6, damage: '1d8+4' }),
      expect.objectContaining({ label: '레이피어', attackBonus: 6, damage: '1d8+4' }),
      expect.objectContaining({ label: '그레이트소드', attackBonus: 4, damage: '2d6+2' }),
    ])
  })

  it('filters generic weapon choices by proficiency category', () => {
    expect(weaponChoices('SIMPLE').every(weapon => weapon.category === 'SIMPLE')).toBe(true)
    expect(weaponChoices('MARTIAL').every(weapon => weapon.category === 'MARTIAL')).toBe(true)
  })
})
