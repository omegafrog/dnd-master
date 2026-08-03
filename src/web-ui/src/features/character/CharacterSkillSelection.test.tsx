import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterSkillSelection } from './CharacterSkillSelection'

describe('CharacterSkillSelection', () => {
  it('클래스 기술 선택 수를 제한하고 기존 선택은 해제할 수 있다', async () => {
    const user = userEvent.setup()
    const onSkillsChange = vi.fn()
    render(<CharacterSkillSelection
      skillOptions={['곡예', '은신', '지각']}
      skillChoiceCount={2}
      selectedSkills={['곡예', '은신']}
      proficientSkills={['곡예', '은신']}
      expertiseChoiceCount={0}
      selectedExpertise={[]}
      onSkillsChange={onSkillsChange}
      onExpertiseChange={vi.fn()}
    />)

    expect(screen.getByLabelText('지각')).toBeDisabled()
    await user.click(screen.getByLabelText('곡예'))
    expect(onSkillsChange).toHaveBeenCalledWith(['은신'])
  })

  it('숙달은 숙련 기술 중에서만 고르고 요구 개수를 제한한다', async () => {
    const user = userEvent.setup()
    const onExpertiseChange = vi.fn()
    render(<CharacterSkillSelection
      skillOptions={['곡예', '은신', '지각']}
      skillChoiceCount={2}
      selectedSkills={['곡예', '은신']}
      proficientSkills={['곡예', '은신', '지각']}
      expertiseChoiceCount={2}
      selectedExpertise={['곡예', '은신']}
      onSkillsChange={vi.fn()}
      onExpertiseChange={onExpertiseChange}
    />)

    const expertiseGroup = screen.getByText('숙달 2개 선택').closest('fieldset')
    expect(expertiseGroup?.textContent).toContain('지각')
    const expertisePerception = expertiseGroup?.querySelector('input[type="checkbox"]:not(:checked)') as HTMLInputElement
    expect(expertisePerception.disabled).toBe(true)
    await user.click(screen.getAllByLabelText('곡예')[1])
    expect(onExpertiseChange).toHaveBeenCalledWith(['은신'])
  })

  it('기술 숙련 해제 시 더 이상 유효하지 않은 숙달도 제거한다', async () => {
    const user = userEvent.setup()
    const onExpertiseChange = vi.fn()
    render(<CharacterSkillSelection
      skillOptions={['곡예', '은신']}
      skillChoiceCount={2}
      selectedSkills={['곡예', '은신']}
      proficientSkills={['곡예', '은신']}
      expertiseChoiceCount={2}
      selectedExpertise={['곡예', '은신']}
      onSkillsChange={vi.fn()}
      onExpertiseChange={onExpertiseChange}
    />)

    await user.click(screen.getAllByLabelText('곡예')[0])
    expect(onExpertiseChange).toHaveBeenCalledWith(['은신'])
  })
})
