import { describe, expect, it } from 'vitest'
import { proficiencyBonusForLevel } from './Dnd5eRules'

describe('proficiencyBonusForLevel', () => {
  it.each([
    [1, 2], [4, 2],
    [5, 3], [8, 3],
    [9, 4], [12, 4],
    [13, 5], [16, 5],
    [17, 6], [20, 6],
  ])('%i레벨은 숙련 보너스 +%i를 사용한다', (level, expected) => {
    expect(proficiencyBonusForLevel(level)).toBe(expected)
  })

  it('지원 범위 밖의 레벨은 1~20으로 제한한다', () => {
    expect(proficiencyBonusForLevel(0)).toBe(2)
    expect(proficiencyBonusForLevel(21)).toBe(6)
  })
})
