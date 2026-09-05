import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CombatScreen } from './CombatScreen'

describe('CombatScreen', () => {
  it('shows round, current participant, initiative and resources without hidden enemy fields', () => {
    render(<CombatScreen snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1, currentParticipantId: 'p1', version: 3, eventCursor: 2, resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true }, initiative: [
      { participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' },
      { participantId: 'p2', displayName: '고블린', controller: 'AI', initiative: 10, publicCondition: null },
    ] }} />)
    expect(screen.getByRole('heading', { name: '전투 · Round 1' })).toBeInTheDocument()
    expect(screen.getByText(/현재 턴: 영웅/)).toBeInTheDocument()
    expect(screen.getByText(/이동 30ft/)).toBeInTheDocument()
    expect(screen.queryByText(/AC|HP|정확한/)).not.toBeInTheDocument()
  })

  it('submits an action and ends the human turn explicitly', async () => {
    const user = userEvent.setup()
    const submitAction = vi.fn(async () => ({ status: 'COMMITTED' as const }))
    const endTurn = vi.fn(async () => ({ status: 'TURN_ENDED' as const }))
    const api = { readSnapshot: vi.fn(), submitAction, endTurn }
    const snapshot = { encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE' as const, round: 1, currentParticipantId: 'p1', version: 3, eventCursor: 2, resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true }, initiative: [
      { participantId: 'p1', displayName: '영웅', controller: 'PLAYER' as const, initiative: 15, publicCondition: 'healthy' },
    ] }

    render(<CombatScreen snapshot={snapshot} api={api} />)
    await user.click(screen.getByRole('button', { name: '공격 실행' }))
    await user.click(screen.getByRole('button', { name: '턴 종료' }))

    expect(submitAction).toHaveBeenCalledWith('a1', expect.objectContaining({ characterSheetId: 'p1', action: 'attack' }), 3)
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

    expect(screen.getByText('상대적 위치: NEAR · 엄폐: HALF')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: '이동 경로' }), '0,0;1,0')
    await user.click(screen.getByRole('button', { name: '이동 실행' }))

    expect(submitMovement).toHaveBeenCalledWith('a1', expect.objectContaining({ action: 'MOVE', movementPath: [{ x: 0, y: 0 }, { x: 1, y: 0 }] }), 4)
  })

  it('keeps free-form input, Combat Log, and GM narration in separate areas', async () => {
    const user = userEvent.setup()
    const submitFreeForm = vi.fn(async () => ({ status: 'COMMITTED', judgment: '햄이 명중했습니다.', narration: '고블린이 움찔합니다.' }))
    const api = { readSnapshot: vi.fn(), submitAction: vi.fn(), submitFreeForm, endTurn: vi.fn() }
    render(<CombatScreen api={api} snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 4, eventCursor: 3,
      resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    await user.type(screen.getByRole('textbox', { name: '자유 행동 선언' }), 'throw ham at the goblin')
    await user.click(screen.getByRole('button', { name: '자유 행동 보내기' }))

    expect(submitFreeForm).toHaveBeenCalledWith('a1', 'p1', 'throw ham at the goblin', 4)
    expect(screen.getByRole('heading', { name: 'Combat Log' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'GM Narration' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Combat Log' })).toHaveTextContent('햄이 명중했습니다.')
    expect(screen.getByRole('list', { name: 'GM Narration' })).toHaveTextContent('고블린이 움찔합니다.')
  })
})
