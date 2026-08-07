import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureStoryPlanPage } from './AdventureStoryPlanPage'
import type { AdventureSessionView, AdventureStoryPlanView } from './AdventureSessionApi'

const session: AdventureSessionView = {
  sessionId: 'session-1', scenarioPackageId: 'package-1', scenarioPackageRevision: 1,
  blueprintId: 'blueprint-1', blueprintRevision: 1, characterLimit: 1, version: 1,
  status: 'DRAFT', adventureId: null, runtimeConfiguration: { scenarioId: 's', ruleSetId: 'r', rulebookIds: ['r'], engineId: 'e', toolIds: [], initialScene: 'scene' }, party: [],
}
const ready: AdventureStoryPlanView = { planId: 'plan-1', packageRevision: 1, partyRevision: 0, version: 1, status: 'READY', currentStage: 6, stageCount: 6, failureReason: null }

describe('AdventureStoryPlanPage', () => {
  it('offers a safe retry when the real provider is unavailable', async () => {
    const user = userEvent.setup()
    let attempts = 0
    const api = {
      read: vi.fn(async () => session),
      readStoryPlan: vi.fn(async () => { throw new Error('GM provider를 사용할 수 없습니다. 잠시 후 다시 시도하세요.') }),
      generateStoryPlan: vi.fn(async () => { attempts++; if (attempts === 1) throw new Error('GM provider를 사용할 수 없습니다. 잠시 후 다시 시도하세요.'); return ready }),
      retryStoryPlan: vi.fn(async () => ready),
      start: vi.fn(),
    }
    render(<AdventureStoryPlanPage api={api} sessionId="session-1" />)
    expect(await screen.findByRole('status')).toHaveTextContent('GM provider를 사용할 수 없습니다.')
    await user.click(screen.getByRole('button', { name: '다시 생성' }))
    await waitFor(() => expect(screen.getByText('모험 계획 준비')).toBeInTheDocument())
    expect(screen.queryByText(/secret|stack trace|prompt/i)).not.toBeInTheDocument()
  })
})
