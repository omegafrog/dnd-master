import { describe, expect, it } from 'vitest'
import {
  backgroundRule,
  choicesComplete,
  classChoiceRequirements,
  raceChoiceRequirements,
  selectedChoiceValues,
} from './Dnd5eCharacterChoiceRules'

describe('Dnd5eCharacterChoiceRules', () => {
  it('requires one additional language for humans and high elves', () => {
    expect(raceChoiceRequirements('인간', '')).toEqual([
      expect.objectContaining({ id: 'race-language', count: 1 }),
    ])
    expect(raceChoiceRequirements('엘프', '하이 엘프')).toEqual([
      expect.objectContaining({ id: 'subrace-language', count: 1 }),
    ])
  })

  it('requires three instruments for bards and one tool or instrument for monks', () => {
    expect(classChoiceRequirements('바드')[0]).toMatchObject({ id: 'class-instruments', count: 3 })
    expect(classChoiceRequirements('몽크')[0]).toMatchObject({ id: 'class-tool', count: 1 })
  })

  it('provides background feature and typed choices', () => {
    expect(backgroundRule('현자')).toMatchObject({
      feature: { name: '연구자' },
      choiceRequirements: [expect.objectContaining({ count: 2 })],
    })
    expect(backgroundRule('민중 영웅')?.feature.name).toBe('민중의 환대')
  })

  it('accepts only exact valid selections', () => {
    const requirements = raceChoiceRequirements('인간', '')
    expect(choicesComplete(requirements, { 'race-language': ['엘프어'] })).toBe(true)
    expect(choicesComplete(requirements, { 'race-language': [] })).toBe(false)
    expect(choicesComplete(requirements, { 'race-language': ['존재하지 않는 언어'] })).toBe(false)
    expect(selectedChoiceValues(requirements, { 'race-language': ['엘프어'] })).toEqual(['엘프어'])
  })
})
