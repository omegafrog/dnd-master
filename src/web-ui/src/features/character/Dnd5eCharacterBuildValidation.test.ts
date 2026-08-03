import { describe, expect, it } from 'vitest'
import { validateCharacterBuild } from './Dnd5eCharacterBuildValidation'

const valid = {
  level: 1,
  edition: 'DND_5E_2014',
  abilityScores: [15, 14, 13, 12, 10, 8],
  requiredSkillCount: 2,
  selectedSkills: ['운동', '지각'],
  requiredExpertiseCount: 0,
  selectedExpertise: [],
  proficientSkills: ['운동', '지각'],
  equipmentGroupIds: ['armor'],
  equipmentSelections: { armor: 'chain' },
  requiredCantrips: 0,
  selectedCantrips: [],
  requiredFirstLevelSpells: 0,
  selectedFirstLevelSpells: [],
  choicesComplete: true,
  subclassRequired: false,
  subclass: '',
}

describe('validateCharacterBuild', () => {
  it('accepts a complete level-one 2014 build', () => {
    expect(validateCharacterBuild(valid)).toEqual([])
  })

  it('returns stable error codes for invalid rule choices', () => {
    expect(validateCharacterBuild({ ...valid, abilityScores: [15, 15, 13, 12, 10, 8], choicesComplete: false }).map(error => error.code))
      .toEqual(['STANDARD_ARRAY_INVALID', 'RULE_CHOICES_INCOMPLETE'])
  })
})
