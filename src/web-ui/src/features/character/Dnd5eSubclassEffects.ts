import type { AbilityScores } from './Dnd5eRules'

export type SubclassEffects = {
  armorProficiencies: string[]
  weaponProficiencies: string[]
  bonusCantrips: string[]
  minimumArmorClass?: number
  notes: string[]
}

export function subclassEffects(subclass: string, modifiers: AbilityScores): SubclassEffects {
  switch (subclass) {
    case '생명 권역':
      return { armorProficiencies: ['중갑'], weaponProficiencies: [], bonusCantrips: [], notes: ['생명의 제자: 치유 주문의 회복량 증가'] }
    case '빛 권역':
      return { armorProficiencies: [], weaponProficiencies: [], bonusCantrips: ['빛'], notes: ['수호의 섬광'] }
    case '자연 권역':
      return { armorProficiencies: ['중갑'], weaponProficiencies: [], bonusCantrips: ['드루이드 소마법 1개'], notes: ['자연의 수행자'] }
    case '폭풍 권역':
    case '전쟁 권역':
      return { armorProficiencies: ['중갑'], weaponProficiencies: ['군용 무기'], bonusCantrips: [], notes: [] }
    case '용의 혈통':
      return { armorProficiencies: [], weaponProficiencies: [], bonusCantrips: [], minimumArmorClass: 13 + modifiers.dexterity, notes: ['용의 회복력: 갑옷 미착용 시 AC 13 + 민첩 수정치'] }
    default:
      return { armorProficiencies: [], weaponProficiencies: [], bonusCantrips: [], notes: [] }
  }
}

export function applySubclassArmorClass(baseArmorClass: number, equippedArmor: string, effects: SubclassEffects): number {
  if (equippedArmor || effects.minimumArmorClass == null) return baseArmorClass
  return Math.max(baseArmorClass, effects.minimumArmorClass)
}
