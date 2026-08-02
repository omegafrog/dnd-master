import { describe, expect, it } from 'vitest'
import { armorCategory, validateArmorEquipment } from './Dnd5eArmorEquipmentRules'

describe('Dnd5eArmorEquipmentRules', () => {
  it('classifies armor by category', () => {
    expect(armorCategory('가죽 갑옷')).toBe('LIGHT')
    expect(armorCategory('스케일 메일')).toBe('MEDIUM')
    expect(armorCategory('체인 메일')).toBe('HEAVY')
  })

  it('rejects armor outside the resolved proficiency list', () => {
    expect(validateArmorEquipment('로그', '체인 메일', ['경갑']).map(issue => issue.code))
      .toContain('ARMOR_NOT_PROFICIENT')
    expect(validateArmorEquipment('파이터', '체인 메일', ['모든 갑옷', '방패']))
      .toEqual([])
  })

  it('rejects metal armor for druids even when the category is proficient', () => {
    expect(validateArmorEquipment('드루이드', '스케일 메일', ['비금속 경갑', '비금속 평갑']).map(issue => issue.code))
      .toContain('DRUID_METAL_ARMOR')
  })
})
