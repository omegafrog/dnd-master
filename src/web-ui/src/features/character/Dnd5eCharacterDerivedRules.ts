import type { Ability, AbilityScores } from './Dnd5eRules'

export type EquipmentGroup = {
  id: string
  label: string
  options: Array<{ id: string; label: string; items: string[] }>
}

export type ClassCreationRule = {
  spellcastingAbility?: Ability
  cantripCount: number
  firstLevelSpellCount: number
  armorProficiencies: string[]
  weaponProficiencies: string[]
  toolProficiencies: string[]
  equipmentGroups: EquipmentGroup[]
}

const rules: Record<string, ClassCreationRule> = {
  파이터: {
    cantripCount: 0, firstLevelSpellCount: 0,
    armorProficiencies: ['모든 갑옷', '방패'], weaponProficiencies: ['단순 무기', '군용 무기'], toolProficiencies: [],
    equipmentGroups: [
      { id: 'armor', label: '방어구', options: [{ id: 'chain', label: '체인 메일', items: ['체인 메일'] }, { id: 'leather', label: '가죽 갑옷, 장궁, 화살 20개', items: ['가죽 갑옷', '장궁', '화살 20개'] }] },
      { id: 'weapons', label: '주 무장', options: [{ id: 'weapon-shield', label: '군용 무기 1개와 방패', items: ['군용 무기 1개', '방패'] }, { id: 'two-weapons', label: '군용 무기 2개', items: ['군용 무기 2개'] }] },
      { id: 'ranged', label: '보조 무장', options: [{ id: 'crossbow', label: '라이트 크로스보우와 볼트 20개', items: ['라이트 크로스보우', '볼트 20개'] }, { id: 'handaxes', label: '핸드액스 2개', items: ['핸드액스 2개'] }] },
      { id: 'pack', label: '꾸러미', options: [{ id: 'dungeoneer', label: '던전 탐험가 꾸러미', items: ['던전 탐험가 꾸러미'] }, { id: 'explorer', label: '탐험가 꾸러미', items: ['탐험가 꾸러미'] }] },
    ],
  },
  로그: {
    cantripCount: 0, firstLevelSpellCount: 0,
    armorProficiencies: ['경갑'], weaponProficiencies: ['단순 무기', '핸드 크로스보우', '롱소드', '레이피어', '숏소드'], toolProficiencies: ['도둑 도구'],
    equipmentGroups: [
      { id: 'weapon', label: '주 무장', options: [{ id: 'rapier', label: '레이피어', items: ['레이피어'] }, { id: 'shortsword', label: '숏소드', items: ['숏소드'] }] },
      { id: 'secondary', label: '보조 무장', options: [{ id: 'bow', label: '단궁과 화살 20개', items: ['단궁', '화살 20개'] }, { id: 'shortsword2', label: '숏소드', items: ['숏소드'] }] },
      { id: 'pack', label: '꾸러미', options: [{ id: 'burglar', label: '도둑 꾸러미', items: ['도둑 꾸러미'] }, { id: 'dungeoneer', label: '던전 탐험가 꾸러미', items: ['던전 탐험가 꾸러미'] }, { id: 'explorer', label: '탐험가 꾸러미', items: ['탐험가 꾸러미'] }] },
    ],
  },
  클레릭: {
    spellcastingAbility: 'wisdom', cantripCount: 3, firstLevelSpellCount: 2,
    armorProficiencies: ['경갑', '평갑', '방패'], weaponProficiencies: ['단순 무기'], toolProficiencies: [],
    equipmentGroups: [
      { id: 'weapon', label: '주 무장', options: [{ id: 'mace', label: '메이스', items: ['메이스'] }, { id: 'warhammer', label: '워해머(숙련 시)', items: ['워해머'] }] },
      { id: 'armor', label: '방어구', options: [{ id: 'scale', label: '스케일 메일', items: ['스케일 메일'] }, { id: 'leather', label: '가죽 갑옷', items: ['가죽 갑옷'] }] },
      { id: 'secondary', label: '보조 무장', options: [{ id: 'crossbow', label: '라이트 크로스보우와 볼트 20개', items: ['라이트 크로스보우', '볼트 20개'] }, { id: 'simple', label: '단순 무기 1개', items: ['단순 무기 1개'] }] },
      { id: 'pack', label: '꾸러미', options: [{ id: 'priest', label: '사제 꾸러미', items: ['사제 꾸러미'] }, { id: 'explorer', label: '탐험가 꾸러미', items: ['탐험가 꾸러미'] }] },
    ],
  },
  위저드: {
    spellcastingAbility: 'intelligence', cantripCount: 3, firstLevelSpellCount: 6,
    armorProficiencies: [], weaponProficiencies: ['대거', '다트', '슬링', '쿼터스태프', '라이트 크로스보우'], toolProficiencies: [],
    equipmentGroups: [
      { id: 'weapon', label: '주 무장', options: [{ id: 'quarterstaff', label: '쿼터스태프', items: ['쿼터스태프'] }, { id: 'dagger', label: '대거', items: ['대거'] }] },
      { id: 'focus', label: '주문 도구', options: [{ id: 'component', label: '구성요소 주머니', items: ['구성요소 주머니'] }, { id: 'focus', label: '비전 매개체', items: ['비전 매개체'] }] },
      { id: 'pack', label: '꾸러미', options: [{ id: 'scholar', label: '학자 꾸러미', items: ['학자 꾸러미'] }, { id: 'explorer', label: '탐험가 꾸러미', items: ['탐험가 꾸러미'] }] },
    ],
  },
}

export function classCreationRule(characterClass: string): ClassCreationRule | undefined {
  return rules[characterClass]
}

export function resolveEquipment(characterClass: string, selections: Record<string, string>): string[] {
  const rule = classCreationRule(characterClass)
  if (!rule) return []
  return rule.equipmentGroups.flatMap(group => {
    const option = group.options.find(item => item.id === selections[group.id])
    return option?.items ?? []
  })
}

export function spellAttackBonus(characterClass: string, modifiers: AbilityScores, proficiencyBonus: number): number | null {
  const ability = classCreationRule(characterClass)?.spellcastingAbility
  return ability ? modifiers[ability] + proficiencyBonus : null
}

export function spellSaveDc(characterClass: string, modifiers: AbilityScores, proficiencyBonus: number): number | null {
  const attack = spellAttackBonus(characterClass, modifiers, proficiencyBonus)
  return attack == null ? null : 8 + attack
}

export function savingThrowBonuses(modifiers: AbilityScores, proficient: Ability[], proficiencyBonus: number): AbilityScores {
  return Object.fromEntries(Object.entries(modifiers).map(([ability, modifier]) => [ability, modifier + (proficient.includes(ability as Ability) ? proficiencyBonus : 0)])) as AbilityScores
}
