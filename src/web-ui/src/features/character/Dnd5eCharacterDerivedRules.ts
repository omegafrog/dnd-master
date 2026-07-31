import type { Ability, AbilityScores } from './Dnd5eRules'

export type EquipmentGroup = { id: string; label: string; options: Array<{ id: string; label: string; items: string[] }> }
export type ClassCreationRule = {
  spellcastingAbility?: Ability
  cantripCount: number
  firstLevelSpellCount: number
  armorProficiencies: string[]
  weaponProficiencies: string[]
  toolProficiencies: string[]
  equipmentGroups: EquipmentGroup[]
}
const o = (id: string, label: string, ...items: string[]) => ({ id, label, items })
const g = (id: string, label: string, ...options: ReturnType<typeof o>[]): EquipmentGroup => ({ id, label, options })
const one = (id: string, label: string, item: string) => g(id, label, o(item, item, item))
const packs = (...names: string[]) => g('pack', '꾸러미', ...names.map(name => o(name, `${name} 꾸러미`, `${name} 꾸러미`)))

const rules: Record<string, ClassCreationRule> = {
  바바리안: rule(['경갑', '평갑', '방패'], ['단순 무기', '군용 무기'], [], [g('weapon', '주 무장', o('greataxe', '그레이트액스', '그레이트액스'), o('martial', '군용 근접 무기', '군용 근접 무기 1개')), g('secondary', '보조 무장', o('handaxes', '핸드액스 2개', '핸드액스 2개'), o('simple', '단순 무기', '단순 무기 1개')), one('pack', '꾸러미', '탐험가 꾸러미')]),
  바드: caster('charisma', 2, 4, ['경갑'], ['단순 무기', '핸드 크로스보우', '롱소드', '레이피어', '숏소드'], ['악기 3종'], [g('weapon', '주 무장', o('rapier', '레이피어', '레이피어'), o('longsword', '롱소드', '롱소드'), o('simple', '단순 무기', '단순 무기 1개')), packs('외교관', '연예인'), g('instrument', '악기', o('lute', '류트', '류트'), o('other', '선택 악기', '선택 악기 1개'))]),
  클레릭: caster('wisdom', 3, 2, ['경갑', '평갑', '방패'], ['단순 무기'], [], [g('weapon', '주 무장', o('mace', '메이스', '메이스'), o('warhammer', '워해머', '워해머')), g('armor', '방어구', o('scale', '스케일 메일', '스케일 메일'), o('leather', '가죽 갑옷', '가죽 갑옷')), g('secondary', '보조 무장', o('crossbow', '라이트 크로스보우와 볼트', '라이트 크로스보우', '볼트 20개'), o('simple', '단순 무기', '단순 무기 1개')), packs('사제', '탐험가')]),
  드루이드: caster('wisdom', 2, 2, ['비금속 경갑', '비금속 평갑', '비금속 방패'], ['드루이드 무기'], ['약초학 도구'], [g('shield', '방패 또는 무기', o('shield', '나무 방패', '나무 방패'), o('simple', '단순 무기', '단순 무기 1개')), g('weapon', '주 무장', o('scimitar', '시미터', '시미터'), o('simple', '단순 근접 무기', '단순 근접 무기 1개')), one('pack', '꾸러미', '탐험가 꾸러미')]),
  파이터: rule(['모든 갑옷', '방패'], ['단순 무기', '군용 무기'], [], [g('armor', '방어구', o('chain', '체인 메일', '체인 메일'), o('leather', '가죽 갑옷과 장궁', '가죽 갑옷', '장궁', '화살 20개')), g('weapons', '주 무장', o('shield', '군용 무기와 방패', '군용 무기 1개', '방패'), o('two', '군용 무기 2개', '군용 무기 2개')), g('ranged', '보조 무장', o('crossbow', '라이트 크로스보우', '라이트 크로스보우', '볼트 20개'), o('handaxes', '핸드액스 2개', '핸드액스 2개')), packs('던전 탐험가', '탐험가')]),
  몽크: rule([], ['단순 무기', '숏소드'], ['장인 도구 또는 악기 1종'], [g('weapon', '주 무장', o('shortsword', '숏소드', '숏소드'), o('simple', '단순 무기', '단순 무기 1개')), packs('던전 탐험가', '탐험가')]),
  팔라딘: rule(['모든 갑옷', '방패'], ['단순 무기', '군용 무기'], [], [g('weapon', '주 무장', o('shield', '군용 무기와 방패', '군용 무기 1개', '방패'), o('two', '군용 무기 2개', '군용 무기 2개')), g('secondary', '보조 무장', o('javelins', '재블린 5개', '재블린 5개'), o('simple', '단순 근접 무기', '단순 근접 무기 1개')), packs('사제', '탐험가')]),
  레인저: rule(['경갑', '평갑', '방패'], ['단순 무기', '군용 무기'], [], [g('armor', '방어구', o('scale', '스케일 메일', '스케일 메일'), o('leather', '가죽 갑옷', '가죽 갑옷')), g('weapons', '주 무장', o('shortswords', '숏소드 2개', '숏소드 2개'), o('simple', '단순 근접 무기 2개', '단순 근접 무기 2개')), packs('던전 탐험가', '탐험가')]),
  로그: rule(['경갑'], ['단순 무기', '핸드 크로스보우', '롱소드', '레이피어', '숏소드'], ['도둑 도구'], [g('weapon', '주 무장', o('rapier', '레이피어', '레이피어'), o('shortsword', '숏소드', '숏소드')), g('secondary', '보조 무장', o('bow', '단궁과 화살', '단궁', '화살 20개'), o('shortsword', '숏소드', '숏소드')), packs('도둑', '던전 탐험가', '탐험가')]),
  소서러: caster('charisma', 4, 2, [], ['대거', '다트', '슬링', '쿼터스태프', '라이트 크로스보우'], [], [g('weapon', '주 무장', o('crossbow', '라이트 크로스보우', '라이트 크로스보우', '볼트 20개'), o('simple', '단순 무기', '단순 무기 1개')), g('focus', '주문 도구', o('component', '구성요소 주머니', '구성요소 주머니'), o('focus', '비전 매개체', '비전 매개체')), packs('던전 탐험가', '탐험가')]),
  워락: caster('charisma', 2, 2, ['경갑'], ['단순 무기'], [], [g('weapon', '주 무장', o('crossbow', '라이트 크로스보우', '라이트 크로스보우', '볼트 20개'), o('simple', '단순 무기', '단순 무기 1개')), g('focus', '주문 도구', o('component', '구성요소 주머니', '구성요소 주머니'), o('focus', '비전 매개체', '비전 매개체')), packs('학자', '던전 탐험가')]),
  위저드: caster('intelligence', 3, 6, [], ['대거', '다트', '슬링', '쿼터스태프', '라이트 크로스보우'], [], [g('weapon', '주 무장', o('staff', '쿼터스태프', '쿼터스태프'), o('dagger', '대거', '대거')), g('focus', '주문 도구', o('component', '구성요소 주머니', '구성요소 주머니'), o('focus', '비전 매개체', '비전 매개체')), packs('학자', '탐험가')]),
}

function rule(armorProficiencies: string[], weaponProficiencies: string[], toolProficiencies: string[], equipmentGroups: EquipmentGroup[]): ClassCreationRule {
  return { cantripCount: 0, firstLevelSpellCount: 0, armorProficiencies, weaponProficiencies, toolProficiencies, equipmentGroups }
}
function caster(spellcastingAbility: Ability, cantripCount: number, firstLevelSpellCount: number, armor: string[], weapons: string[], tools: string[], equipmentGroups: EquipmentGroup[]): ClassCreationRule {
  return { ...rule(armor, weapons, tools, equipmentGroups), spellcastingAbility, cantripCount, firstLevelSpellCount }
}
export function classCreationRule(characterClass: string): ClassCreationRule | undefined { return rules[characterClass] }
export function resolveEquipment(characterClass: string, selections: Record<string, string>): string[] {
  const creation = rules[characterClass]
  return creation ? creation.equipmentGroups.flatMap(group => group.options.find(item => item.id === selections[group.id])?.items ?? []) : []
}
export function spellAttackBonus(characterClass: string, modifiers: AbilityScores, proficiencyBonus: number): number | null {
  const ability = rules[characterClass]?.spellcastingAbility
  return ability ? modifiers[ability] + proficiencyBonus : null
}
export function spellSaveDc(characterClass: string, modifiers: AbilityScores, proficiencyBonus: number): number | null {
  const attack = spellAttackBonus(characterClass, modifiers, proficiencyBonus)
  return attack == null ? null : 8 + attack
}
export function savingThrowBonuses(modifiers: AbilityScores, proficient: Ability[], proficiencyBonus: number): AbilityScores {
  return Object.fromEntries(Object.entries(modifiers).map(([ability, modifier]) => [ability, modifier + (proficient.includes(ability as Ability) ? proficiencyBonus : 0)])) as AbilityScores
}
