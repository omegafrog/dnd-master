import type { Ability, AbilityScores } from './Dnd5eRules'

export type SkillDefinition = { id: string; label: string; ability: Ability }
export type SkillBonusView = SkillDefinition & { proficient: boolean; expertise: boolean; bonus: number }
export type SpellSlotSummary = { level: number; slots: number }

export const skillDefinitions: SkillDefinition[] = [
  { id: 'acrobatics', label: '곡예', ability: 'dexterity' },
  { id: 'animal-handling', label: '동물 조련', ability: 'wisdom' },
  { id: 'arcana', label: '비전학', ability: 'intelligence' },
  { id: 'athletics', label: '운동', ability: 'strength' },
  { id: 'deception', label: '기만', ability: 'charisma' },
  { id: 'history', label: '역사', ability: 'intelligence' },
  { id: 'insight', label: '통찰', ability: 'wisdom' },
  { id: 'intimidation', label: '위협', ability: 'charisma' },
  { id: 'investigation', label: '수사', ability: 'intelligence' },
  { id: 'medicine', label: '의학', ability: 'wisdom' },
  { id: 'nature', label: '자연', ability: 'intelligence' },
  { id: 'perception', label: '지각', ability: 'wisdom' },
  { id: 'performance', label: '공연', ability: 'charisma' },
  { id: 'persuasion', label: '설득', ability: 'charisma' },
  { id: 'religion', label: '종교', ability: 'intelligence' },
  { id: 'sleight-of-hand', label: '손재주', ability: 'dexterity' },
  { id: 'stealth', label: '은신', ability: 'dexterity' },
  { id: 'survival', label: '생존', ability: 'wisdom' },
]

export function skillBonuses(
  modifiers: AbilityScores,
  proficiencyBonus: number,
  proficiencies: string[],
  expertise: string[] = [],
): SkillBonusView[] {
  const proficientSet = new Set(proficiencies)
  const expertiseSet = new Set(expertise)
  return skillDefinitions.map(skill => {
    const isExpert = expertiseSet.has(skill.label)
    const isProficient = isExpert || proficientSet.has(skill.label)
    const multiplier = isExpert ? 2 : isProficient ? 1 : 0
    return { ...skill, proficient: isProficient, expertise: isExpert, bonus: modifiers[skill.ability] + proficiencyBonus * multiplier }
  })
}

export function passivePerception(skills: SkillBonusView[]): number {
  return 10 + (skills.find(skill => skill.id === 'perception')?.bonus ?? 0)
}

export function firstLevelSpellSlots(characterClass: string): SpellSlotSummary[] {
  switch (characterClass) {
    case '바드':
    case '클레릭':
    case '드루이드':
    case '소서러':
    case '위저드':
      return [{ level: 1, slots: 2 }]
    case '워락':
      return [{ level: 1, slots: 1 }]
    default:
      return []
  }
}

export function expertiseChoiceCount(characterClass: string, level = 1): number {
  if (characterClass === '로그' && level >= 1) return 2
  return 0
}

export function uniqueProficiencies(...groups: string[][]): string[] {
  return groups.flat().filter((value, index, all) => all.indexOf(value) === index)
}
