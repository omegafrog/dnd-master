import type { Ability, AbilityScores } from './Dnd5eRules'

export type EquipmentGroup = { id: string; label: string; options: Array<{ id: string; label: string; items: string[] }> }
export type ClassCreationRule = {
  spellcastingAbility?: Ability; cantripCount: number; firstLevelSpellCount: number
  armorProficiencies: string[]; weaponProficiencies: string[]; toolProficiencies: string[]
  equipmentGroups: EquipmentGroup[]
}
const option = (id: string, label: string, ...items: string[]) => ({ id, label, items })
const group = (id: string, label: string, ...options: ReturnType<typeof option>[]): EquipmentGroup => ({ id, label, options })
const pack = group('pack', '꾸러미', option('dungeoneer', '던전 탐험가 꾸러미', '던전 탐험가 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))
const simpleWeapon = group('weapon', '주 무장', option('simple', '단순 무기 1개', '단순 무기 1개'), option('crossbow', '라이트 크로스보우와 볼트 20개', '라이트 크로스보우', '볼트 20개'))

const rules: Record<string, ClassCreationRule> = {
  바바리안: { cantripCount: 0, firstLevelSpellCount: 0, armorProficiencies: ['경갑', '평갑', '방패'], weaponProficiencies: ['단순 무기', '군용 무기'], toolProficiencies: [], equipmentGroups: [group('weapon', '주 무장', option('greataxe', '그레이트액스', '그레이트액스'), option('martial', '군용 근접 무기', '군용 근접 무기 1개')), group('secondary', '보조 무장', option('handaxes', '핸드액스 2개', '핸드액스 2개'), option('simple', '단순 무기 1개', '단순 무기 1개')), optionGroup('pack', '탐험가 꾸러미', 'explorer', '탐험가 꾸러미')] },
  바드: { spellcastingAbility: 'charisma', cantripCount: 2, firstLevelSpellCount: 4, armorProficiencies: ['경갑'], weaponProficiencies: ['단순 무기', '핸드 크로스보우', '롱소드', '레이피어', '숏소드'], toolProficiencies: ['악기 3종'], equipmentGroups: [group('weapon', '주 무장', option('rapier', '레이피어', '레이피어'), option('longsword', '롱소드', '롱소드'), option('simple', '단순 무기 1개', '단순 무기 1개')), group('pack', '꾸러미', option('diplomat', '외교관 꾸러미', '외교관 꾸러미'), option('entertainer', '연예인 꾸러미', '연예인 꾸러미')), group('instrument', '악기', option('lute', '류트', '류트'), option('instrument', '선택 악기', '선택 악기 1개'))] },
  클레릭: { spellcastingAbility: 'wisdom', cantripCount: 3, firstLevelSpellCount: 2, armorProficiencies: ['경갑', '평갑', '방패'], weaponProficiencies: ['단순 무기'], toolProficiencies: [], equipmentGroups: [group('weapon', '주 무장', option('mace', '메이스', '메이스'), option('warhammer', '워해머(숙련 시)', '워해머')), group('armor', '방어구', option('scale', '스케일 메일', '스케일 메일'), option('leather', '가죽 갑옷', '가죽 갑옷')), simpleWeapon, group('pack', '꾸러미', option('priest', '사제 꾸러미', '사제 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))] },
  드루이드: { spellcastingAbility: 'wisdom', cantripCount: 2, firstLevelSpellCount: 2, armorProficiencies: ['비금속 경갑', '비금속 평갑', '비금속 방패'], weaponProficiencies: ['곤봉', '대거', '다트', '재블린', '메이스', '쿼터스태프', '시미터', '슬링', '창'], toolProficiencies: ['약초학 도구'], equipmentGroups: [group('shield', '방패 또는 무기', option('shield', '나무 방패', '나무 방패'), option('simple', '단순 무기 1개', '단순 무기 1개')), group('weapon', '주 무장', option('scimitar', '시미터', '시미터'), option('simple2', '단순 근접 무기 1개', '단순 근접 무기 1개')), optionGroup('pack', '탐험가 꾸러미', 'explorer', '탐험가 꾸러미')] },
  파이터: { cantripCount: 0, firstLevelSpellCount: 0, armorProficiencies: ['모든 갑옷', '방패'], weaponProficiencies: ['단순 무기', '군용 무기'], toolProficiencies: [], equipmentGroups: [group('armor', '방어구', option('chain', '체인 메일', '체인 메일'), option('leather', '가죽 갑옷, 장궁, 화살 20개', '가죽 갑옷', '장궁', '화살 20개')), group('weapons', '주 무장', option('weapon-shield', '군용 무기와 방패', '군용 무기 1개', '방패'), option('two-weapons', '군용 무기 2개', '군용 무기 2개')), group('ranged', '보조 무장', option('crossbow', '라이트 크로스보우와 볼트 20개', '라이트 크로스보우', '볼트 20개'), option('handaxes', '핸드액스 2개', '핸드액스 2개')), pack] },
  몽크: { cantripCount: 0, firstLevelSpellCount: 0, armorProficiencies: [], weaponProficiencies: ['단순 무기', '숏소드'], toolProficiencies: ['장인 도구 또는 악기 1종'], equipmentGroups: [group('weapon', '주 무장', option('shortsword', '숏소드', '숏소드'), option('simple', '단순 무기 1개', '단순 무기 1개')), group('pack', '꾸러미', option('dungeoneer', '던전 탐험가 꾸러미', '던전 탐험가 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))] },
  팔라딘: { cantripCount: 0, firstLevelSpellCount: 0, armorProficiencies: ['모든 갑옷', '방패'], weaponProficiencies: ['단순 무기', '군용 무기'], toolProficiencies: [], equipmentGroups: [group('weapon', '주 무장', option('weapon-shield', '군용 무기와 방패', '군용 무기 1개', '방패'), option('two-weapons', '군용 무기 2개', '군용 무기 2개')), group('secondary', '보조 무장', option('javelins', '재블린 5개', '재블린 5개'), option('simple', '단순 근접 무기 1개', '단순 근접 무기 1개')), group('pack', '꾸러미', option('priest', '사제 꾸러미', '사제 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))] },
  레인저: { cantripCount: 0, firstLevelSpellCount: 0, armorProficiencies: ['경갑', '평갑', '방패'], weaponProficiencies: ['단순 무기', '군용 무기'], toolProficiencies: [], equipmentGroups: [group('armor', '방어구', option('scale', '스케일 메일', '스케일 메일'), option('leather', '가죽 갑옷', '가죽 갑옷')), group('weapons', '주 무장', option('shortswords', '숏소드 2개', '숏소드 2개'), option('simple', '단순 근접 무기 2개', '단순 근접 무기 2개')), group('pack', '꾸러미', option('dungeoneer', '던전 탐험가 꾸러미', '던전 탐험가 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))] },
  로그: { cantripCount: 0, firstLevelSpellCount: 0, armorProficiencies: ['경갑'], weaponProficiencies: ['단순 무기', '핸드 크로스보우', '롱소드', '레이피어', '숏소드'], toolProficiencies: ['도둑 도구'], equipmentGroups: [group('weapon', '주 무장', option('rapier', '레이피어', '레이피어'), option('shortsword', '숏소드', '숏소드')), group('secondary', '보조 무장', option('bow', '단궁과 화살 20개', '단궁', '화살 20개'), option('shortsword2', '숏소드', '숏소드')), group('pack', '꾸러미', option('burglar', '도둑 꾸러미', '도둑 꾸러미'), option('dungeoneer', '던전 탐험가 꾸러미', '던전 탐험가 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))] },
  소서러: { spellcastingAbility: 'charisma', cantripCount: 4, firstLevelSpellCount: 2, armorProficiencies: [], weaponProficiencies: ['대거', '다트', '슬링', '쿼터스태프', '라이트 크로스보우'], toolProficiencies: [], equipmentGroups: [simpleWeapon, group('focus', '주문 도구', option('component', '구성요소 주머니', '구성요소 주머니'), option('focus', '비전 매개체', '비전 매개체')), group('pack', '꾸러미', option('dungeoneer', '던전 탐험가 꾸러미', '던전 탐험가 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))] },
  워락: { spellcastingAbility: 'charisma', cantripCount: 2, firstLevelSpellCount: 2, armorProficiencies: ['경갑'], weaponProficiencies: ['단순 무기'], toolProficiencies: [], equipmentGroups: [simpleWeapon, group('focus', '주문 도구', option('component', '구성요소 주머니', '구성요소 주머니'), option('focus', '비전 매개체', '비전 매개체')), group('pack', '꾸러미', option('scholar', '학자 꾸러미', '학자 꾸러미'), option('dungeoneer', '던전 탐험가 꾸러미', '던전 탐험가 꾸러미'))] },
  위저드: { spellcastingAbility: 'intelligence', cantripCount: 3, firstLevelSpellCount: 6, armorProficiencies: [], weaponProficiencies: ['대거', '다트', '슬링', '쿼터스태프', '라이트 크로스보우'], toolProficiencies: [], equipmentGroups: [group('weapon', '주 무장', option('quarterstaff', '쿼터스태프', '쿼터스태프'), option('dagger', '대거', '대거')), group('focus', '주문 도구', option('component', '구성요소 주머니', '구성요소 주머니'), option('focus', '비전 매개체', '비전 매개체')), group('pack', '꾸러미', option('scholar', '학자 꾸러미', '학자 꾸러미'), option('explorer', '탐험가 꾸러미', '탐험가 꾸러미'))] },
}

function optionGroup(id: string, label: string, optionId: string, item: string): EquipmentGroup { return group(id, label, option(optionId, item, item)) }
export function classCreationRule(characterClass: string): ClassCreationRule | undefined { return rules[characterClass] }
export function resolveEquipment(characterClass: string, selections: Record<string, string>): string[] {
  const rule = classCreationRule(characterClass)
  return rule ? rule.equipmentGroups.flatMap(group => group.options.find(item => item.id === selections[group.id])?.items ?? []) : []
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
