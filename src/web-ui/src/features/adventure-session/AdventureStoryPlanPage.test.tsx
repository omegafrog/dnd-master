import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureStoryPlanPage } from './AdventureStoryPlanPage'

describe('AdventureStoryPlanPage configuration', () => {
  it('asks for adventure settings before generating and forwards them', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', version: 1, party: [{ characterSheetId: 'c' }], runtimeConfiguration: null }),
      readStoryPlan: vi.fn().mockRejectedValue(new Error('not found')),
      generateStoryPlan: vi.fn().mockResolvedValue({ planId: 'p', packageRevision: 1, partyRevision: 1, version: 1, status: 'READY', currentStage: 0, stageCount: 7, endingCount: 3, adventureLength: 'LONG', failureReason: null }),
      retryStoryPlan: vi.fn(), start: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }
    render(<AdventureStoryPlanPage api={api} sessionId="s" />)

    const slider = await screen.findByRole('slider', { name: /분기 결말 수/ })
    fireEvent.change(slider, { target: { value: '4' } })
    await userEvent.selectOptions(screen.getByLabelText('모험 길이'), 'LONG')
    await userEvent.click(screen.getByRole('button', { name: '모험 계획 생성' }))

    expect(api.generateStoryPlan).toHaveBeenCalledWith('s', { endingCount: 4, adventureLength: 'LONG' })
  })
})
