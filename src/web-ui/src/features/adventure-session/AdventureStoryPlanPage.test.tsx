import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureStoryPlanPage } from './AdventureStoryPlanPage'

describe('AdventureStoryPlanPage configuration', () => {
  it('asks for adventure settings before generating and forwards them', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', version: 1, party: [{ characterSheetId: 'c' }], runtimeConfiguration: null }),
      readStoryPlan: vi.fn().mockRejectedValue(new Error('not found')),
      generateStoryPlan: vi.fn().mockResolvedValue({ status: 'READY', currentStage: 0, planRevision: 0, endingCount: 3, adventureLength: 'LONG', stages: [], failureReason: null }),
      retryStoryPlan: vi.fn(), start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }
    render(<AdventureStoryPlanPage api={api} sessionId="s" />)

    const slider = await screen.findByRole('slider', { name: /분기 결말 수/ })
    fireEvent.change(slider, { target: { value: '4' } })
    await userEvent.selectOptions(screen.getByLabelText('모험 길이'), 'LONG')
    await userEvent.click(screen.getByRole('button', { name: '모험 계획 생성' }))

    expect(api.generateStoryPlan).toHaveBeenCalledWith('s', { endingCount: 4, adventureLength: 'LONG' })
  })

  it('shows blocked diagnostics, keeps start disabled, and offers regeneration', async () => {
    const blockedPlan = {
      status: 'BLOCKED' as const, currentStage: 0, planRevision: 0,
      endingCount: 2, adventureLength: 'STANDARD' as const, stages: [],
      failureReason: '근거 없는 적 배치',
    }
    const api = {
      read: vi.fn().mockResolvedValue({
        sessionId: 's', version: 1, status: 'DRAFT', party: [],
        runtimeConfiguration: { scenarioId: 'scenario', ruleSetId: 'rules', rulebookIds: ['book'], engineId: 'engine', toolIds: [], initialScene: 'opening' },
      }),
      readStoryPlan: vi.fn().mockResolvedValue(blockedPlan),
      generateStoryPlan: vi.fn(),
      retryStoryPlan: vi.fn().mockResolvedValue({ ...blockedPlan, status: 'READY', failureReason: null }),
      start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }
    render(<AdventureStoryPlanPage api={api} sessionId="s" />)

    expect(await screen.findByText('BLOCKED')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('근거 없는 적 배치')
    expect((screen.getByRole('button', { name: '모험 시작' }) as HTMLButtonElement).disabled).toBe(true)

    await userEvent.click(screen.getByRole('button', { name: '다시 생성' }))

    expect(api.retryStoryPlan).toHaveBeenCalledWith('s', { endingCount: 2, adventureLength: 'STANDARD' })
  })
})
