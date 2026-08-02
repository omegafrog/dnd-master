export type Ability = 'strength' | 'dexterity' | 'constitution' | 'intelligence' | 'wisdom' | 'charisma'
export type AbilityScores = Record<Ability, number>
export type ConditionalTrait = { name: string; kind: 'ADVANTAGE' | 'DISADVANTAGE' | 'IMMUNITY' | 'RESISTANCE'; trigger: string; source: 'RACE' | 'CLASS' | 'BACKGROUND' }
export type HitPointIncrease = { method: 'AVERAGE' | 'ROLL'; roll?: number }
export type Dnd5eCharacterStatistics = {
  abilityScores: AbilityScores; abilityModifiers: AbilityScores; hitDie: string; hitPointMaximum: number
  proficiencyBonus: number; speed: number; armorClass: number; savingThrowProficiencies: Ability[]
  conditionalTraits: ConditionalTrait[]; notes: string[]
}
type RaceEffect = { bonuses: Partial<AbilityScores>; speed: number; traits: ConditionalTrait[] }
type ClassEffect = { hitDie: number; savingThrows: Ability[] }
type ArmorEffect = { base: number; dexterityCap?: number }
const abilities: Ability[] = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma']
const normalize = (value: string) => value.trim().toLowerCase()
const trait = (name: string, kind: ConditionalTrait['kind'], trigger: string): ConditionalTrait => ({ name, kind, trigger, source: 'RACE' })

const races: Record<string, RaceEffect> = {
  dwarf: { bonuses: { constitution: 2 }, speed: 25, traits: [trait('Dwarven Resilience', 'ADVANTAGE', 'poison saving throw')] }, '드워프': { bonuses: { constitution: 2 }, speed: 25, traits: [trait('드워프의 회복력', 'ADVANTAGE', '독 내성 굴림')] },
  elf: { bonuses: { dexterity: 2 }, speed: 30, traits: [trait('Fey Ancestry', 'ADVANTAGE', 'charmed saving throw')] }, elves: { bonuses: { dexterity: 2 }, speed: 30, traits: [trait('Fey Ancestry', 'ADVANTAGE', 'charmed saving throw')] }, '엘프': { bonuses: { dexterity: 2 }, speed: 30, traits: [trait('요정 혈통', 'ADVANTAGE', '매혹 내성 굴림')] },
  halfling: { bonuses: { dexterity: 2 }, speed: 25, traits: [trait('Brave', 'ADVANTAGE', 'frightened saving throw')] }, '하플링': { bonuses: { dexterity: 2 }, speed: 25, traits: [trait('용감함', 'ADVANTAGE', '공포 내성 굴림')] },
  human: { bonuses: { strength: 1, dexterity: 1, constitution: 1, intelligence: 1, wisdom: 1, charisma: 1 }, speed: 30, traits: [] }, '인간': { bonuses: { strength: 1, dexterity: 1, constitution: 1, intelligence: 1, wisdom: 1, charisma: 1 }, speed: 30, traits: [] },
}
const subraces: Record<string, Partial<RaceEffect>> = {
  'hill dwarf': { bonuses: { wisdom: 1 } }, '언덕 드워프': { bonuses: { wisdom: 1 } },
  'mountain dwarf': { bonuses: { strength: 2 } }, '산 드워프': { bonuses: { strength: 2 } },
  'high elf': { bonuses: { intelligence: 1 } }, '하이 엘프': { bonuses: { intelligence: 1 } },
  'wood elf': { bonuses: { wisdom: 1 }, speed: 35 }, '우드 엘프': { bonuses: { wisdom: 1 }, speed: 35 },
  'lightfoot halfling': { bonuses: { charisma: 1 } }, '라이트풋 하플링': { bonuses: { charisma: 1 } },
  'stout halfling': { bonuses: { constitution: 1 }, traits: [trait('Stout Resilience', 'ADVANTAGE', 'poison saving throw')] }, '스타우트 하플링': { bonuses: { constitution: 1 }, traits: [trait('스타우트 회복력', 'ADVANTAGE', '독 내성 굴림')] },
}
const classes: Record<string, ClassEffect> = {
  barbarian: { hitDie: 12, savingThrows: ['strength', 'constitution'] }, '바바리안': { hitDie: 12, savingThrows: ['strength', 'constitution'] },
  bard: { hitDie: 8, savingThrows: ['dexterity', 'charisma'] }, '바드': { hitDie: 8, savingThrows: ['dexterity', 'charisma'] },
  cleric: { hitDie: 8, savingThrows: ['wisdom', 'charisma'] }, '클레릭': { hitDie: 8, savingThrows: ['wisdom', 'charisma'] },
  druid: { hitDie: 8, savingThrows: ['intelligence', 'wisdom'] }, '드루이드': { hitDie: 8, savingThrows: ['intelligence', 'wisdom'] },
  fighter: { hitDie: 10, savingThrows: ['strength', 'constitution'] }, '파이터': { hitDie: 10, savingThrows: ['strength', 'constitution'] },
  monk: { hitDie: 8, savingThrows: ['strength', 'dexterity'] }, '몽크': { hitDie: 8, savingThrows: ['strength', 'dexterity'] },
  paladin: { hitDie: 10, savingThrows: ['wisdom', 'charisma'] }, '팔라딘': { hitDie: 10, savingThrows: ['wisdom', 'charisma'] },
  ranger: { hitDie: 10, savingThrows: ['strength', 'dexterity'] }, '레인저': { hitDie: 10, savingThrows: ['strength', 'dexterity'] },
  rogue: { hitDie: 8, savingThrows: ['dexterity', 'intelligence'] }, '로그': { hitDie: 8, savingThrows: ['dexterity', 'intelligence'] },
  sorcerer: { hitDie: 6, savingThrows: ['constitution', 'charisma'] }, '소서러': { hitDie: 6, savingThrows: ['constitution', 'charisma'] },
  warlock: { hitDie: 8, savingThrows: ['wisdom', 'charisma'] }, '워락': { hitDie: 8, savingThrows: ['wisdom', 'charisma'] },
  wizard: { hitDie: 6, savingThrows: ['intelligence', 'wisdom'] }, '위저드': { hitDie: 6, savingThrows: ['intelligence', 'wisdom'] },
}
const armor: Record<string, ArmorEffect> = {
  'padded armor': { base: 11 }, '패디드 아머': { base: 11 }, leather: { base: 11 }, '가죽 갑옷': { base: 11 },
  'studded leather': { base: 12 }, '스터디드 레더': { base: 12 }, hide: { base: 12, dexterityCap: 2 }, '하이드': { base: 12, dexterityCap: 2 },
  'chain shirt': { base: 13, dexterityCap: 2 }, '체인 셔츠': { base: 13, dexterityCap: 2 }, 'scale mail': { base: 14, dexterityCap: 2 }, '스케일 메일': { base: 14, dexterityCap: 2 },
  breastplate: { base: 14, dexterityCap: 2 }, '브레스트플레이트': { base: 14, dexterityCap: 2 }, 'half plate': { base: 15, dexterityCap: 2 }, '하프 플레이트': { base: 15, dexterityCap: 2 },
  'ring mail': { base: 14, dexterityCap: 0 }, '링 메일': { base: 14, dexterityCap: 0 }, chainmail: { base: 16, dexterityCap: 0 }, 'chain mail': { base: 16, dexterityCap: 0 }, '체인 메일': { base: 16, dexterityCap: 0 },
  splint: { base: 17, dexterityCap: 0 }, '스플린트': { base: 17, dexterityCap: 0 }, plate: { base: 18, dexterityCap: 0 }, '플레이트': { base: 18, dexterityCap: 0 },
}

export function subracesFor(race: string): string[] {
  return ({ dwarf: ['Hill Dwarf', 'Mountain Dwarf'], '드워프': ['언덕 드워프', '산 드워프'], elf: ['High Elf', 'Wood Elf'], elves: ['High Elf', 'Wood Elf'], '엘프': ['하이 엘프', '우드 엘프'], halfling: ['Lightfoot Halfling', 'Stout Halfling'], '하플링': ['라이트풋 하플링', '스타우트 하플링'] }[normalize(race)] ?? [])
}
export function armorOptions(): string[] { return Object.keys(armor) }
export function proficiencyBonusForLevel(level: number): number {
  const normalizedLevel = Math.max(1, Math.min(20, Math.trunc(level) || 1))
  return 2 + Math.floor((normalizedLevel - 1) / 4)
}

export function calculateDnd5eCharacter(input: { race: string; subrace?: string; characterClass: string; level: number; baseAbilities: Partial<AbilityScores>; equippedArmor?: string; equippedShield?: boolean; hitPointIncreases?: HitPointIncrease[] }): Dnd5eCharacterStatistics {
  const race = races[normalize(input.race)]
  const subrace = subraces[normalize(input.subrace ?? '')]
  const normalizedClass = normalize(input.characterClass)
  const characterClass = classes[normalizedClass]
  const bonuses = abilities.reduce((result, ability) => ({ ...result, [ability]: (race?.bonuses[ability] ?? 0) + (subrace?.bonuses?.[ability] ?? 0) }), {} as Partial<AbilityScores>)
  const abilityScores = Object.fromEntries(abilities.map(ability => [ability, (input.baseAbilities[ability] ?? 0) + (bonuses[ability] ?? 0)])) as AbilityScores
  const abilityModifiers = Object.fromEntries(abilities.map(ability => [ability, Math.floor((abilityScores[ability] - 10) / 2)])) as AbilityScores
  const level = Math.max(1, Math.min(20, input.level || 1)); const hitDie = characterClass?.hitDie ?? 0
  const hpChoices = input.hitPointIncreases ?? []; const missingRoll = hpChoices.findIndex(choice => choice.method === 'ROLL' && (!choice.roll || choice.roll < 1 || choice.roll > hitDie))
  const levelUpHp = hpChoices.reduce((total, choice) => total + (choice.method === 'AVERAGE' ? Math.floor(hitDie / 2) + 1 : choice.roll ?? 0) + abilityModifiers.constitution, 0)
  const equipped = armor[normalize(input.equippedArmor ?? '')]
  const dexterityForArmor = !equipped ? abilityModifiers.dexterity : equipped.dexterityCap === 0 ? 0 : Math.min(abilityModifiers.dexterity, equipped.dexterityCap ?? abilityModifiers.dexterity)
  const unarmoredBase = normalizedClass === 'barbarian' || normalizedClass === '바바리안'
    ? 10 + abilityModifiers.dexterity + abilityModifiers.constitution
    : (normalizedClass === 'monk' || normalizedClass === '몽크') && !input.equippedShield
      ? 10 + abilityModifiers.dexterity + abilityModifiers.wisdom
      : 10 + abilityModifiers.dexterity
  const armorClass = equipped
    ? equipped.base + dexterityForArmor + (input.equippedShield ? 2 : 0)
    : unarmoredBase + (input.equippedShield && normalizedClass !== 'monk' && normalizedClass !== '몽크' ? 2 : 0)
  return {
    abilityScores, abilityModifiers, hitDie: hitDie ? `d${hitDie}` : '', hitPointMaximum: hitDie && missingRoll < 0 && hpChoices.length >= level - 1 ? Math.max(1, hitDie + abilityModifiers.constitution + levelUpHp) : 0,
    proficiencyBonus: proficiencyBonusForLevel(level), speed: subrace?.speed ?? race?.speed ?? 0,
    armorClass, savingThrowProficiencies: characterClass?.savingThrows ?? [], conditionalTraits: [...(race?.traits ?? []), ...(subrace?.traits ?? [])],
    notes: [!race ? '선택한 종족 효과를 아직 계산할 수 없습니다.' : '', !characterClass ? '선택한 클래스 효과를 아직 계산할 수 없습니다.' : '', level > 1 && hpChoices.length < level - 1 ? '레벨별 HP 증가 방식을 모두 선택해야 합니다.' : '', missingRoll >= 0 ? `${missingRoll + 2}레벨 HP 굴림 결과를 입력해야 합니다.` : '', !equipped ? '방어도는 클래스의 비무장 방어 규칙을 적용했습니다.' : '방패는 장착 중일 때 AC +2입니다.'].filter(Boolean),
  }
}
