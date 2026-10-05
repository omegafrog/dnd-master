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

  it('restores committed GM narration from the durable event stream after mounting', async () => {
    const subscribeEvents = vi.fn((_adventureId: string, afterSequence: number,
      onEvent: (event: { sequence: number; type: string; payload: string }) => void) => {
      expect(afterSequence).toBe(0)
      onEvent({ sequence: 8, type: 'ACTION_RESOLVED', payload: JSON.stringify({ judgment: 'hit (attack=16, AC=12)', diceTotal: 11 }) })
      onEvent({ sequence: 9, type: 'GM_NARRATION', payload: JSON.stringify({ narration: '영웅의 검이 거대 쥐를 쓰러뜨립니다.' }) })
      return vi.fn()
    })
    render(<CombatScreen api={{ readSnapshot: vi.fn(), submitAction: vi.fn(), endTurn: vi.fn(), subscribeEvents }}
      snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
        currentParticipantId: 'p1', version: 3, eventCursor: 9,
        resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true },
        initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    expect(await screen.findByText('영웅의 검이 거대 쥐를 쓰러뜨립니다.')).toBeInTheDocument()
    expect(screen.queryByText(/attack=16|AC=12|diceTotal/)).not.toBeInTheDocument()
    expect(subscribeEvents).toHaveBeenCalledOnce()
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

  it('shows safe Korean action status instead of private mechanics or operation identifiers', async () => {
    const user = userEvent.setup()
    const submitAction = vi.fn(async () => ({
      status: 'COMMITTED' as const,
      operationId: 'private-operation',
      diceTotal: 11,
      judgment: 'hit (attack=16, AC=12)',
    }))
    render(<CombatScreen api={{ readSnapshot: vi.fn(), submitAction, endTurn: vi.fn() }} snapshot={{
      encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 3, eventCursor: 2,
      resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      initiative: [
        { participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' },
        { participantId: 'enemy-1', displayName: '거대 쥐', controller: 'AI', initiative: 10, publicCondition: null },
      ],
    }} />)

    await user.selectOptions(screen.getByRole('combobox', { name: '공격 대상' }), 'enemy-1')
    await user.click(screen.getByRole('button', { name: '공격' }))

    expect(await screen.findByText('행동 결과를 반영했습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/attack=16|AC=12|diceTotal|private-operation/)).not.toBeInTheDocument()
  })

  it('casts a selected spell at a selected enemy and displays remaining spell slots', async () => {
    const user = userEvent.setup()
    const castSpell = vi.fn(async () => ({ status: 'COMMITTED' as const, judgment: '마법 화살 적중 · 피해 9' }))
    const api = { readSnapshot: vi.fn(), submitAction: vi.fn(), castSpell, endTurn: vi.fn() }
    render(<CombatScreen api={api} snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 3, eventCursor: 2,
      resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      spellcasting: { availableSpells: [{ name: '마법 화살', level: 1 }], availableSlots: { '1': 2 } },
      initiative: [
        { participantId: 'p1', displayName: '마루', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' },
        { participantId: 'rat-3', displayName: '거대 쥐 3', controller: 'AI', initiative: 10, publicCondition: null },
      ] }} />)

    expect(screen.getByText('1레벨 주문 슬롯: 2')).toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: '사용할 주문' }), '마법 화살')
    await user.selectOptions(screen.getByRole('combobox', { name: '주문 대상' }), 'rat-3')
    await user.click(screen.getByRole('button', { name: '시전' }))

    expect(castSpell).toHaveBeenCalledWith('a1', {
      characterSheetId: 'p1', spellName: '마법 화살', targetParticipantId: 'rat-3',
    }, 3)
  })

  it('shows insufficient slots and prevents the player from submitting a spell', () => {
    render(<CombatScreen api={{ readSnapshot: vi.fn(), submitAction: vi.fn(), castSpell: vi.fn(), endTurn: vi.fn() }}
      snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
        currentParticipantId: 'p1', version: 3, eventCursor: 2,
        resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
        spellcasting: { availableSpells: [{ name: '마법 화살', level: 1 }], availableSlots: { '1': 0 } },
        initiative: [{ participantId: 'p1', displayName: '마루', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    expect(screen.getByText('1레벨 주문 슬롯: 0')).toBeInTheDocument()
    expect(screen.getByText('1레벨 주문 슬롯이 부족합니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '시전' })).toBeDisabled()
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

  it('does not render narration directly from an action response', async () => {
    const user = userEvent.setup()
    const submitFreeForm = vi.fn(async () => ({ status: 'COMMITTED', judgment: 'hit (attack=16, AC=12)', narration: '비밀 통로는 동쪽 벽 뒤에 있습니다.' }))
    const api = { readSnapshot: vi.fn(), submitAction: vi.fn(), submitFreeForm, endTurn: vi.fn() }
    render(<CombatScreen api={api} snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 4, eventCursor: 3,
      resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    await user.type(screen.getByRole('textbox', { name: '행동 선언' }), 'throw ham at the goblin')
    await user.click(screen.getByRole('button', { name: '행동 보내기' }))

    expect(submitFreeForm).toHaveBeenCalledWith('a1', 'p1', 'throw ham at the goblin', 4)
    expect(screen.getByRole('heading', { name: '행동 상태' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '게임 마스터 서술' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '행동 상태' })).toHaveTextContent('행동 결과를 반영했습니다.')
    expect(screen.getByRole('list', { name: '행동 상태' })).not.toHaveTextContent(/attack=16|AC=12/)
    expect(screen.getByRole('list', { name: '게임 마스터 서술' })).not.toHaveTextContent('비밀 통로는 동쪽 벽 뒤에 있습니다.')
  })

  it('hides internal failure details and retries the available operation', async () => {
    const user = userEvent.setup()
    const retry = vi.fn(async () => ({ status: 'RETRY_SCHEDULED' as const, operationId: 'op-1' }))
    const api = { readSnapshot: vi.fn(), submitAction: vi.fn(), endTurn: vi.fn(), retry }
    render(<CombatScreen api={api} snapshot={{ encounterId: 'e1', adventureId: 'a1', status: 'ACTIVE', round: 1,
      currentParticipantId: 'p1', version: 8, eventCursor: 6,
      resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
      processingFailure: { operationId: 'op-1', failure: 'AI unavailable', attempts: 3 },
      initiative: [{ participantId: 'p1', displayName: '영웅', controller: 'PLAYER', initiative: 15, publicCondition: 'healthy' }] }} />)

    expect(screen.getByText('전투 처리를 완료하지 못했습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/op-1|AI unavailable|시도 3\/3/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(retry).toHaveBeenCalledWith('a1', 'op-1', 8)
    expect(screen.getAllByRole('status').at(-1)).toHaveTextContent('전투 처리를 다시 요청했습니다.')
  })
})
