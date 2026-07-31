import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterInputTree } from './CharacterInputTree'

describe('CharacterInputTree', () => {
  it('uses a select for one-of choices and text input for player-authored values', () => {
    render(<CharacterInputTree nodes={[
      { id: 'race', parentId: null, key: 'race', label: '종족', inputMode: 'SINGLE_SELECT', value: null,
        options: ['엘프', '드워프'], suggestions: [], status: 'EXTRACTED', allowUserAddChild: false,
        confidence: 'HIGH', sourceQuote: '', diagnostics: [], sourceEvidence: [], children: [] },
      { id: 'campaign_title', parentId: null, key: 'campaign_title', label: '캠페인 칭호', inputMode: 'FREE_TEXT', value: null,
        options: [], suggestions: [], status: 'EXTRACTED', allowUserAddChild: false,
        confidence: 'HIGH', sourceQuote: '', diagnostics: [], sourceEvidence: [], children: [] },
    ]} values={{}} onChange={vi.fn()} />)

    expect(screen.getByRole('combobox', { name: '종족' })).toBeTruthy()
    expect(screen.getByRole('option', { name: '엘프' })).toBeTruthy()
    expect(screen.getByRole('textbox', { name: '캠페인 칭호' })).toBeTruthy()
    expect(screen.getAllByText('5판 베이스 본').length).toBeGreaterThan(0)
  })

  it('shows storybook values as an optional proposal and applies only after review', () => {
    const onResolve = vi.fn()
    render(<CharacterInputTree nodes={[{
      id: 'race', parentId: null, key: 'race', label: '종족', inputMode: 'SINGLE_SELECT', value: null,
      options: ['드워프', '엘프', '인간', '하플링'], suggestions: ['엘프', '인간'], status: 'CONFLICT_REVIEW',
      allowUserAddChild: false, confidence: 'HIGH', sourceQuote: '플레이어 캐릭터는 엘프나 인간이어야 한다.',
      diagnostics: ['스토리북 제안: 적용 여부를 검토하세요'], sourceEvidence: [], children: [],
    }]} values={{ race: '엘프' }} onChange={vi.fn()} onResolve={onResolve} canResolve />)

    expect(screen.getByText('스토리북 제안')).toBeTruthy()
    expect(screen.getByText('추천 또는 제안 값: 엘프, 인간')).toBeTruthy()
    expect(screen.getByRole('button', { name: '제안 적용' })).toBeTruthy()
    expect(screen.getByText(/저장하지 않으면 베이스 본을 유지합니다/)).toBeTruthy()
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
    expect(screen.getByText('추천 또는 제안 값: 12')).toBeTruthy()
    await user.type(strInput, '13')
    expect(onChange).toHaveBeenCalledWith('starting_ability_scores.str', '1')
  })
})
