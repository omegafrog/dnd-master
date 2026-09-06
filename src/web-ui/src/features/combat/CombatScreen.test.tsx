import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CombatScreen } from './CombatScreen'

describe('CombatScreen', () => {
  it('shows only the final summary after combat and never exposes detailed replay', () => {
    render(<CombatScreen snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ENDED', round: 2,
      currentParticipantId: 'p1', version: 9, eventCursor: 8,
      resources: { movement: 0, actionAvailable: false, bonusActionAvailable: false, reactionAvailable: false },
      finalSummary: { adventureId: 'a1', encounterId: 'e1', reason: 'ENEMIES_DEFEATED',
        summary: '적을 물리쳤습니다.', detailedReplayAvailable: false },
      initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    expect(screen.getByRole('heading', { name: '전투 종료 요약' })).toBeInTheDocument()
    expect(screen.getByText('적을 물리쳤습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '판정 결과' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '게임 마스터 서술' })).not.toBeInTheDocument()
    expect(screen.getByText(/상세 전투 기록은 종료 후 제공되지 않습니다/)).toBeInTheDocument()
  })

  it('shows round, current participant, initiative and resources without hidden enemy fields', () => {
    render(<CombatScreen snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1, currentParticipantId: 'p1', version: 3, eventCursor: 2, resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true }, initiative: [
      { participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' },
      { participantId: 'p2', displayName: '고블린', controller: 'AI', initiative: 10, publicCondition: null },
    ] }} />)
    expect(screen.getByRole('heading', { name: '전투 · 1라운드' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('현재 차례영웅')
    expect(screen.getByText('30ft')).toBeInTheDocument()
    expect(screen.queryByText(/AC|HP|정확한/)).not.toBeInTheDocument()
  })

  it('submits an action and ends the human turn explicitly', async () => {
    const user = userEvent.setup()
    const submitAction = vi.fn(async () => ({ status: 'COMMITTED' as const }))
    const endTurn = vi.fn(async () => ({ status: 'TURN_ENDED' as const }))
    const api = { readSnapshot: vi.fn(), submitAction, endTurn }
    const snapshot = { encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE' as const, round: 1, currentParticipantId: 'p1', version: 3, eventCursor: 2, resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true }, initiative: [
      { participantId: 'p1', displayName: '영웅', controller: 'PLAYER' as const, initiative: 15, publicCondition: 'healthy' },
      { participantId: 'e1', displayName: '거대 쥐', controller: 'AI' as const, initiative: 10, publicCondition: null },
    ] }

    render(<CombatScreen snapshot={snapshot} api={api} />)
    await user.selectOptions(screen.getByRole('combobox', { name: '공격 대상' }), 'e1')
    await user.click(screen.getByRole('button', { name: '공격' }))
    await user.click(screen.getByRole('button', { name: '턴 종료' }))

    expect(submitAction).toHaveBeenCalledWith('a1', expect.objectContaining({ characterSheetId: 'p1', action: 'attack', targetCharacterSheetId: 'e1' }), 3)
    expect(endTurn).toHaveBeenCalledWith('a1', 'p1', 3)
  })

  it('renders mapless range and cover and sends movement through Combat API', async () => {
    const user = userEvent.setup()
    const submitMovement = vi.fn(async () => ({ status: 'MOVE_COMMITTED' as const, judgment: 'NEAR range, HALF cover' }))
    const api = { readSnapshot: vi.fn(), submitAction: vi.fn(), submitMovement, endTurn: vi.fn() }
    render(<CombatScreen api={api} snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 4, eventCursor: 3,
      resources: { movement: 20, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      narrativePositions: [{ subjectId: 'p1', targetId: 'p2', rangeBand: 'NEAR', cover: 'HALF' }],
      initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    expect(screen.getByText('거리: NEAR · 엄폐: HALF')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: '이동 경로' }), '0,0;1,0')
    await user.click(screen.getByRole('button', { name: '이동 실행' }))

    expect(submitMovement).toHaveBeenCalledWith('a1', expect.objectContaining({ action: 'MOVE', movementPath: [{ x: 0, y: 0 }, { x: 1, y: 0 }] }), 4)
  })

  it('keeps player action input, result, and game master narration in separate areas', async () => {
    const user = userEvent.setup()
    const submitFreeForm = vi.fn(async () => ({ status: 'COMMITTED', judgment: '햄이 명중했습니다.', narration: '고블린이 움찔합니다.' }))
    const api = { readSnapshot: vi.fn(), submitAction: vi.fn(), submitFreeForm, endTurn: vi.fn() }
    render(<CombatScreen api={api} snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 4, eventCursor: 3,
      resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    await user.type(screen.getByRole('textbox', { name: '행동 선언' }), 'throw ham at the goblin')
    await user.click(screen.getByRole('button', { name: '행동 보내기' }))

    expect(submitFreeForm).toHaveBeenCalledWith('a1', 'p1', 'throw ham at the goblin', 4)
    expect(screen.getByRole('heading', { name: '판정 결과' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '게임 마스터 서술' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '판정 결과' })).toHaveTextContent('햄이 명중했습니다.')
    expect(screen.getByRole('list', { name: '게임 마스터 서술' })).toHaveTextContent('고블린이 움찔합니다.')
  })

  it('shows the failed operation and retries that same operation', async () => {
    const user = userEvent.setup()
    const retry = vi.fn(async () => ({ status: 'RETRY_SCHEDULED' as const, operationId: 'op-1' }))
    const api = { readSnapshot: vi.fn(), submitAction: vi.fn(), endTurn: vi.fn(), retry }
    render(<CombatScreen api={api} snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 8, eventCursor: 6,
      resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      processingFailure: { operationId: 'op-1', failure: 'AI unavailable', attempts: 3 },
      initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    expect(screen.getByText(/작업 op-1/)).toBeInTheDocument()
    expect(screen.getByText(/시도 3\/3/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(retry).toHaveBeenCalledWith('a1', 'op-1', 8)
    expect(screen.getAllByRole('status').at(-1)).toHaveTextContent('RETRY_SCHEDULED')
  })
})
