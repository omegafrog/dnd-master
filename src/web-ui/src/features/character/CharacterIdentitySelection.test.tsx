import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterIdentitySelection } from './CharacterIdentitySelection'

const raceOptions = [
  { id: '인간', subraces: [] },
  { id: '엘프', subraces: [{ id: '하이 엘프' }, { id: '우드 엘프' }] },
]

describe('CharacterIdentitySelection', () => {
  it('이름과 고정된 신규 캐릭터 진행 정보를 표시한다', async () => {
    const user = userEvent.setup()
    const onNameChange = vi.fn()
    render(<CharacterIdentitySelection
      name=""
      race=""
      subrace=""
      proficiencyBonus={2}
      raceOptions={raceOptions}
      onNameChange={onNameChange}
      onRaceChange={vi.fn()}
      onSubraceChange={vi.fn()}
    />)

    expect(screen.getByText(/레벨:/).textContent).toContain('1')
    expect(screen.getByText(/숙련 보너스:/).textContent).toContain('+2')
    await user.type(screen.getByLabelText('캐릭터 이름'), '아리아')
    expect(onNameChange).toHaveBeenLastCalledWith('아')
  })

  it('종족 변경 시 기존 하위 종족을 초기화한다', async () => {
    const user = userEvent.setup()
    const onRaceChange = vi.fn()
    const onSubraceChange = vi.fn()
    render(<CharacterIdentitySelection
      name="아리아"
      race="엘프"
      subrace="하이 엘프"
      proficiencyBonus={2}
      raceOptions={raceOptions}
      onNameChange={vi.fn()}
      onRaceChange={onRaceChange}
      onSubraceChange={onSubraceChange}
    />)

    expect(screen.getByLabelText('하위 종족')).toBeTruthy()
    await user.selectOptions(screen.getByLabelText('종족'), '인간')
    expect(onRaceChange).toHaveBeenCalledWith('인간')
    expect(onSubraceChange).toHaveBeenCalledWith('')
  })
})
