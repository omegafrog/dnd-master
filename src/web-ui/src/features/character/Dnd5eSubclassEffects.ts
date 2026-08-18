import type { AbilityScores } from './Dnd5eRules'

export type SubclassEffects = {
  armorProficiencies: string[]
  weaponProficiencies: string[]
  bonusCantrips: string[]
  minimumArmorClass?: number
  notes: string[]
}

export function subclassEffects(subclass: string, modifiers: AbilityScores): SubclassEffects {
  void modifiers
  switch (subclass) {
    case '생명 권역':
      return { armorProficiencies: ['중갑'], weaponProficiencies: [], bonusCantrips: [], notes: ['생명의 제자: 치유 주문의 회복량 증가'] }
    default:
      return { armorProficiencies: [], weaponProficiencies: [], bonusCantrips: [], notes: [] }
  }
}

export function applySubclassArmorClass(baseArmorClass: number, equippedArmor: string, effects: SubclassEffects): number {
  if (equippedArmor || effects.minimumArmorClass == null) return baseArmorClass
  return Math.max(baseArmorClass, effects.minimumArmorClass)
}
