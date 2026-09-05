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
})
