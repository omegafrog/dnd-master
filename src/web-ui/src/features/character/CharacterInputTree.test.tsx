import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterInputTree } from './CharacterInputTree'

describe('CharacterInputTree', () => {
  it('renders nested nodes by explicit mode and keeps free-text suggestions non-select', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<CharacterInputTree nodes={[{
      id: 'starting_ability_scores', parentId: null, key: 'starting_ability_scores', label: 'Scores',
      inputMode: 'FREE_TEXT', value: null, options: [], suggestions: [], status: 'PARTIALLY_EXTRACTED',
      allowUserAddChild: true, confidence: 'LOW', sourceQuote: '', diagnostics: [], sourceEvidence: [], children: [{
        id: 'starting_ability_scores.str', parentId: 'starting_ability_scores', key: 'str', label: 'STR',
        inputMode: 'FREE_TEXT', value: null, options: [], suggestions: ['12'], status: 'EXTRACTED',
        allowUserAddChild: false, confidence: 'HIGH', sourceQuote: 'STR', diagnostics: [], sourceEvidence: [], children: [],
      }],
    }]} values={{}} onChange={onChange} />)

    const strInput = screen.getAllByLabelText('STR').find(element => element.tagName === 'INPUT')!
    expect(strInput.getAttribute('type')).toBe('text')
    expect(screen.getByText('추천: 12')).toBeTruthy()
    await user.type(strInput, '13')
    expect(onChange).toHaveBeenCalledWith('starting_ability_scores.str', '1')
  })
})
