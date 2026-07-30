import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterInputTree } from './CharacterInputTree'

describe('CharacterInputTree', () => {
  it('uses a select for one-of storybook choices and text input for player-authored values', () => {
    render(<CharacterInputTree nodes={[
      { id: 'race', parentId: null, key: 'race', label: '종족', inputMode: 'SINGLE_SELECT', value: null,
        options: ['Elf', 'Dwarf'], suggestions: [], status: 'EXTRACTED', allowUserAddChild: false,
        confidence: 'HIGH', sourceQuote: 'Choose one race.', diagnostics: [], sourceEvidence: [], children: [] },
      { id: 'campaign_title', parentId: null, key: 'campaign_title', label: '캠페인 칭호', inputMode: 'FREE_TEXT', value: null,
        options: [], suggestions: [], status: 'EXTRACTED', allowUserAddChild: false,
        confidence: 'HIGH', sourceQuote: 'Player chooses a title.', diagnostics: [], sourceEvidence: [], children: [] },
    ]} values={{}} onChange={vi.fn()} />)

    expect(screen.getByRole('combobox', { name: '종족' })).toBeTruthy()
    expect(screen.getByRole('option', { name: 'Elf' })).toBeTruthy()
    expect(screen.getByRole('textbox', { name: '캠페인 칭호' })).toBeTruthy()
  })

  it('renders ability scores as bounded numeric input and keeps suggestions non-select', async () => {
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
    expect(strInput.getAttribute('type')).toBe('number')
    expect(screen.getByText('추천: 12')).toBeTruthy()
    await user.type(strInput, '13')
    expect(onChange).toHaveBeenCalledWith('starting_ability_scores.str', '1')
  })
})
