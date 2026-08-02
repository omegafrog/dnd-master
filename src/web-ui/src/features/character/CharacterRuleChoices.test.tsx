import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterRuleChoices } from './CharacterRuleChoices'

describe('CharacterRuleChoices', () => {
  it('limits selection to the required count', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    const { rerender } = render(<CharacterRuleChoices requirements={[{ id: 'language', label: '추가 언어', count: 1, options: ['엘프어', '드워프어'] }]} selections={{}} onChange={onChange} />)
    await user.click(screen.getByLabelText('추가 언어 엘프어'))
    expect(onChange).toHaveBeenCalledWith('language', ['엘프어'])
    rerender(<CharacterRuleChoices requirements={[{ id: 'language', label: '추가 언어', count: 1, options: ['엘프어', '드워프어'] }]} selections={{ language: ['엘프어'] }} onChange={onChange} />)
    expect((screen.getByLabelText('추가 언어 드워프어') as HTMLInputElement).disabled).toBe(true)
  })
})
