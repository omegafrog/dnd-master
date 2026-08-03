import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterAbilityScores } from './CharacterAbilityScores'
import type { Ability, AbilityScores } from './Dnd5eRules'

const abilities: Ability[] = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma']
const emptyScores: AbilityScores = { strength: 0, dexterity: 0, constitution: 0, intelligence: 0, wisdom: 0, charisma: 0 }
const standardArray = [15, 14, 13, 12, 10, 8]

describe('CharacterAbilityScores', () => {
  it('다른 능력치에 이미 배정한 표준 배열 값은 비활성화한다', () => {
    render(<CharacterAbilityScores abilities={abilities} standardArray={standardArray} scores={{ ...emptyScores, strength: 15 }} onChange={vi.fn()} />)
    const dexterity = screen.getByLabelText('민첩') as HTMLSelectElement
    const option15 = Array.from(dexterity.options).find(option => option.value === '15')
    expect(option15?.disabled).toBe(true)
  })

  it('현재 능력치에 배정된 값은 유지하고 변경 결과 전체를 반환한다', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<CharacterAbilityScores abilities={abilities} standardArray={standardArray} scores={{ ...emptyScores, strength: 15 }} onChange={onChange} />)
    const strength = screen.getByLabelText('근력') as HTMLSelectElement
    expect(strength.value).toBe('15')
    await user.selectOptions(screen.getByLabelText('민첩'), '14')
    expect(onChange).toHaveBeenCalledWith({ ...emptyScores, strength: 15, dexterity: 14 })
  })

  it('배정 진행 수를 표시한다', () => {
    render(<CharacterAbilityScores abilities={abilities} standardArray={standardArray} scores={{ ...emptyScores, strength: 15, dexterity: 14 }} onChange={vi.fn()} />)
    expect(screen.getByText('2/6개 배정')).toBeTruthy()
  })
})
