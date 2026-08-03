import { describe, expect, it } from 'vitest'
import { calculateCombatAttacks, defaultEquipmentState, validateEquipmentState } from './Dnd5eEquipmentStateRules'
import type { AbilityScores } from './Dnd5eRules'

const scores: AbilityScores = { strength: 2, dexterity: 4, constitution: 1, intelligence: 0, wisdom: 3, charisma: -1 }

describe('Dnd5eEquipmentStateRules', () => {
  it('rejects a shield equipped with a two-handed weapon', () => {
    expect(validateEquipmentState(['greatsword'], {
      armor: '체인 메일', shield: true, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: 'greatsword',
    })).toContainEqual(expect.objectContaining({ code: 'SHIELD_WITH_TWO_HANDED' }))
  })

  it('creates thrown and versatile attack variants', () => {
    const attacks = calculateCombatAttacks('파이터', {
      armor: '', shield: false, mainHandWeaponId: 'spear', offHandWeaponId: null, twoHandedWeaponId: null,
    }, ['spear'], scores, 2)
    expect(attacks).toContainEqual(expect.objectContaining({ weaponId: 'spear', versatileDamage: '1d8+2' }))
    expect(attacks).toContainEqual(expect.objectContaining({ weaponId: 'spear-thrown', mode: 'THROWN', range: '20/60ft' }))
  })

  it('uses dexterity for monk martial arts unarmed attacks', () => {
    expect(calculateCombatAttacks('몽크', {
      armor: '', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: null,
    }, [], scores, 2)).toContainEqual(expect.objectContaining({
      weaponId: 'unarmed', label: '무술 비무장 공격', attackBonus: 6, damage: '1d4+4',
    }))
  })

  it('does not equip a shield by default when the first weapon is two-handed', () => {
    expect(defaultEquipmentState(['longbow'], '가죽 갑옷', true)).toEqual({
      armor: '가죽 갑옷', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: 'longbow',
    })
  })
})
