import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
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
})
