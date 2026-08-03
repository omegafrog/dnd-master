import { STANDARD_ARRAY } from './Dnd5eCharacterCatalog'

export type CharacterBuildValidationInput = {
  level: number
  edition: string
  abilityScores: number[]
  requiredSkillCount: number
  selectedSkills: string[]
  requiredExpertiseCount: number
  selectedExpertise: string[]
  proficientSkills: string[]
  equipmentGroupIds: string[]
  equipmentSelections: Record<string, string>
  requiredCantrips: number
  selectedCantrips: string[]
  requiredFirstLevelSpells: number
  selectedFirstLevelSpells: string[]
  choicesComplete: boolean
  subclassRequired: boolean
  subclass: string
}

export type CharacterBuildValidationError = { code: string; message: string }

export function validateCharacterBuild(input: CharacterBuildValidationInput): CharacterBuildValidationError[] {
  const errors: CharacterBuildValidationError[] = []
  const sorted = [...input.abilityScores].sort((a, b) => b - a).join(',')
  if (input.edition !== 'DND_5E_2014') errors.push({ code: 'EDITION_UNSUPPORTED', message: 'D&D 5e 2014 규칙만 지원합니다.' })
  if (input.level !== 1) errors.push({ code: 'LEVEL_INVALID', message: '신규 캐릭터는 1레벨이어야 합니다.' })
  if (sorted !== [...STANDARD_ARRAY].sort((a, b) => b - a).join(',')) errors.push({ code: 'STANDARD_ARRAY_INVALID', message: '표준 배열을 정확히 한 번씩 사용해야 합니다.' })
  if (input.selectedSkills.length !== input.requiredSkillCount) errors.push({ code: 'SKILL_COUNT_INVALID', message: '클래스 기술 선택 개수가 올바르지 않습니다.' })
  if (input.selectedExpertise.length !== input.requiredExpertiseCount || input.selectedExpertise.some(skill => !input.proficientSkills.includes(skill))) errors.push({ code: 'EXPERTISE_INVALID', message: '숙달은 숙련된 기술에서 정확한 개수만 선택해야 합니다.' })
  if (input.equipmentGroupIds.some(id => !input.equipmentSelections[id])) errors.push({ code: 'EQUIPMENT_INCOMPLETE', message: '모든 시작 장비 묶음을 선택해야 합니다.' })
  if (input.selectedCantrips.length !== input.requiredCantrips) errors.push({ code: 'CANTRIP_COUNT_INVALID', message: '소마법 선택 개수가 올바르지 않습니다.' })
  if (input.selectedFirstLevelSpells.length !== input.requiredFirstLevelSpells) errors.push({ code: 'SPELL_COUNT_INVALID', message: '1레벨 주문 선택 개수가 올바르지 않습니다.' })
  if (!input.choicesComplete) errors.push({ code: 'RULE_CHOICES_INCOMPLETE', message: '언어·도구·악기 선택을 완료해야 합니다.' })
  if (input.subclassRequired && !input.subclass) errors.push({ code: 'SUBCLASS_REQUIRED', message: '1레벨 하위 클래스를 선택해야 합니다.' })
  return errors
}
