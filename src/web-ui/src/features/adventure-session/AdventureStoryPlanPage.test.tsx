import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureStoryPlanPage } from './AdventureStoryPlanPage'
import { normalizeAdventureStoryPlanStatus, normalizeAdventureStoryPlanGenerationJob } from './AdventureSessionApi'

describe('AdventureStoryPlanPage configuration', () => {
  it('normalizes legacy Korean API terminal values', () => {
    expect(normalizeAdventureStoryPlanStatus('계획 검증 실패')).toBe('BLOCKED')
    expect(normalizeAdventureStoryPlanGenerationJob({ status: 'COMPLETE', message: '계획 검증 실패', stage: '완료' } as never).status).toBe('FAILED')
  })
  it('shows completion progress when an existing story plan is already ready', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', version: 1, status: 'DRAFT', party: [], runtimeConfiguration: null }),
      readStoryPlan: vi.fn().mockResolvedValue({ status: 'READY', currentStage: 0, planRevision: 1, endingCount: 2, adventureLength: 'STANDARD', stages: [], failureReason: null }),
      startStoryPlanGeneration: vi.fn(), readStoryPlanGeneration: vi.fn(), retryStoryPlan: vi.fn(),
      start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }

    render(<AdventureStoryPlanPage api={api} sessionId="s" />)

    await screen.findByText('100%', { exact: true })
    const progress = document.querySelector<HTMLElement>('.preparation-progress[role="status"]')
    if (!progress) throw new Error('completion progress is not rendered')
    expect(progress.textContent).toContain('플레이 준비 완료')
    expect(api.readStoryPlanGeneration).not.toHaveBeenCalled()
  })

  it('asks for adventure settings before generating and forwards them', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', version: 1, party: [{ characterSheetId: 'c' }], runtimeConfiguration: null }),
      readStoryPlan: vi.fn().mockRejectedValueOnce(new Error('not found')).mockResolvedValue({ status: 'READY', currentStage: 0, planRevision: 1, endingCount: 4, adventureLength: 'LONG', stages: [], failureReason: null }),
    startStoryPlanGeneration: vi.fn().mockResolvedValue({ jobId: 'job-1', sessionId: 's', status: 'QUEUED', progress: 0, stage: '대기 중', message: null, updatedAt: '' }),
    readStoryPlanGeneration: vi.fn().mockResolvedValue({ jobId: 'job-1', sessionId: 's', status: 'COMPLETE', progress: 100, stage: '플레이 준비 완료', message: null, updatedAt: '' }),
      retryStoryPlan: vi.fn(), start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }
    render(<AdventureStoryPlanPage api={api} sessionId="s" />)

    const slider = await screen.findByRole('slider', { name: /분기 결말 수/ })
    fireEvent.change(slider, { target: { value: '4' } })
    await userEvent.selectOptions(screen.getByLabelText('모험 길이'), 'LONG')
    await userEvent.click(screen.getByRole('button', { name: '모험 계획 생성' }))

    expect(api.startStoryPlanGeneration).toHaveBeenCalledWith('s', { endingCount: 4, adventureLength: 'LONG' })
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
    startStoryPlanGeneration: vi.fn(),
    readStoryPlanGeneration: vi.fn(),
    retryStoryPlan: vi.fn().mockResolvedValue({ jobId: 'job-2', sessionId: 's', status: 'QUEUED', progress: 0, stage: '대기 중', message: null, updatedAt: '' }),
      start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }
    render(<AdventureStoryPlanPage api={api} sessionId="s" />)

    expect(await screen.findByText('BLOCKED')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('근거 없는 적 배치')
    expect((screen.getByRole('button', { name: '모험 시작' }) as HTMLButtonElement).disabled).toBe(true)

    await userEvent.click(screen.getByRole('button', { name: '다시 생성' }))

    expect(api.retryStoryPlan).toHaveBeenCalledWith('s', { endingCount: 2, adventureLength: 'STANDARD' })
  })

  it('keeps dependency-repair failures blocked without exposing a rejected candidate', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({
        sessionId: 's', version: 1, status: 'DRAFT', party: [],
        runtimeConfiguration: { scenarioId: 'scenario', ruleSetId: 'rules', rulebookIds: ['book'], engineId: 'engine', toolIds: [], initialScene: 'opening' },
      }),
      readStoryPlan: vi.fn().mockResolvedValue({
        status: 'BLOCKED' as const, currentStage: 0, planRevision: 2,
        endingCount: 2, adventureLength: 'STANDARD' as const, stages: [],
        failureReason: '참가자 근거 부족으로 의존 필드 검증이 차단되었습니다.',
      }),
      startStoryPlanGeneration: vi.fn(), readStoryPlanGeneration: vi.fn(),
      retryStoryPlan: vi.fn(), start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }

    render(<AdventureStoryPlanPage api={api} sessionId="s" />)

    expect((await screen.findByRole('alert')).textContent).toContain('의존 필드 검증이 차단되었습니다.')
    expect(screen.queryByText('rejected-candidate-secret')).toBeNull()
    expect((screen.getByRole('button', { name: '모험 시작' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it.each([
    ['BLOCKED', '근거 검증 실패', 'COMPLETE'],
    ['FAILED', 'provider timeout', 'FAILED'],
  ] as const)('stops generation polling and shows %s diagnostics when the terminal plan is returned', async (status, failureReason, jobStatus) => {
    const terminalPlan = {
      status,
      currentStage: 0,
      planRevision: 1,
      endingCount: 2,
      adventureLength: 'STANDARD' as const,
      stages: [],
      failureReason,
    }
    const api = {
      read: vi.fn().mockResolvedValue({
        sessionId: 's', version: 1, status: 'DRAFT', party: [], runtimeConfiguration: null,
      }),
      readStoryPlan: vi.fn()
        .mockRejectedValueOnce(new Error('not found'))
        .mockResolvedValueOnce(terminalPlan),
      startStoryPlanGeneration: vi.fn().mockResolvedValue({ jobId: 'job-1', sessionId: 's', status: 'QUEUED', progress: 0, stage: '대기 중', message: null, updatedAt: '' }),
      readStoryPlanGeneration: vi.fn().mockResolvedValue({ jobId: 'job-1', sessionId: 's', status: jobStatus, progress: 100, stage: '완료', message: null, updatedAt: '' }),
      retryStoryPlan: vi.fn(), start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }

    render(<AdventureStoryPlanPage api={api} sessionId="s" />)
    fireEvent.click(await screen.findByRole('button', { name: '모험 계획 생성' }))

    await waitFor(() => expect(screen.getByText(status)).toBeTruthy(), { timeout: 3_000 })
    expect(screen.getAllByRole('alert').map(alert => alert.textContent).join(' ')).toContain(failureReason)
    const generationReads = api.readStoryPlanGeneration.mock.calls.length
    await new Promise(resolve => window.setTimeout(resolve, 1_200))
    expect(api.readStoryPlanGeneration).toHaveBeenCalledTimes(generationReads)
  })

  it('converges a stale generating plan when a failed job carries the terminal diagnostic', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', version: 1, status: 'DRAFT', party: [], runtimeConfiguration: null }),
      readStoryPlan: vi.fn()
        .mockRejectedValueOnce(new Error('not found'))
        .mockResolvedValueOnce({ status: 'GENERATING', currentStage: 0, planRevision: 1, endingCount: 2, adventureLength: 'STANDARD', stages: [], failureReason: null }),
      startStoryPlanGeneration: vi.fn().mockResolvedValue({ jobId: 'job-1', sessionId: 's', status: 'QUEUED', progress: 0, stage: '대기 중', message: null, updatedAt: '' }),
      readStoryPlanGeneration: vi.fn().mockResolvedValue({ jobId: 'job-1', sessionId: 's', status: 'FAILED', progress: 100, stage: '계획 검증 실패', message: '계획 검증 실패', updatedAt: '' }),
      retryStoryPlan: vi.fn(), start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }

    render(<AdventureStoryPlanPage api={api} sessionId="s" />)
    await userEvent.click(await screen.findByRole('button', { name: '모험 계획 생성' }))

    expect(await screen.findByText('BLOCKED')).toBeTruthy()
    expect((await screen.findAllByRole('alert')).some(alert => alert.textContent?.includes('계획 검증 실패'))).toBe(true)
    const reads = api.readStoryPlanGeneration.mock.calls.length
    await new Promise(resolve => window.setTimeout(resolve, 1200))
    expect(api.readStoryPlanGeneration).toHaveBeenCalledTimes(reads)
  })

  it('materializes a blocked terminal plan when the rejected plan is not readable', async () => {
    const failureReason = 'endingIds must be empty; npcOrClues must be explicit; story plan scoped repair could not be safely merged'
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', version: 1, status: 'DRAFT', party: [], runtimeConfiguration: null }),
      readStoryPlan: vi.fn().mockRejectedValue(new Error('not found')),
      startStoryPlanGeneration: vi.fn().mockResolvedValue({ jobId: 'job-1', sessionId: 's', status: 'QUEUED', progress: 0, stage: '대기 중', message: null, updatedAt: '' }),
      readStoryPlanGeneration: vi.fn().mockResolvedValue({
        jobId: 'job-1', sessionId: 's', status: 'FAILED', progress: 100,
        stage: '계획 검증 실패', message: failureReason, updatedAt: '',
      }),
      retryStoryPlan: vi.fn(), start: vi.fn(), recoverStart: vi.fn(), saveAppliedRuleSet: vi.fn(),
    }

    render(<AdventureStoryPlanPage api={api} sessionId="s" />)
    await userEvent.click(await screen.findByRole('button', { name: '모험 계획 생성' }))

    expect(await screen.findByText('BLOCKED')).toBeTruthy()
    expect(screen.getAllByRole('alert').map(alert => alert.textContent).join(' ')).toContain(failureReason)
    const reads = api.readStoryPlanGeneration.mock.calls.length
    await new Promise(resolve => window.setTimeout(resolve, 1_200))
    expect(api.readStoryPlanGeneration).toHaveBeenCalledTimes(reads)
  })
})
