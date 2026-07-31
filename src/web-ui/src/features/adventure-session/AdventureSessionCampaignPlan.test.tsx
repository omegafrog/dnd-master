import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureSessionPanel } from './AdventureSessionPanel'

describe('AdventureSessionPanel campaign planning', () => {
  it('prepares and renders a source-linked ordered campaign plan', async () => {
    const plan = {
      planId: 'plan-1',
      sessionId: 'session-1',
      scenarioPackageId: 'package-1',
      scenarioPackageRevision: 3,
      revision: 1,
      overview: '두 개의 STORYBOOK 근거를 순서대로 진행한다.',
      documents: [
        { knowledgeDocumentId: 'doc-1', extractionVersion: 7, originalFilename: 'chapter-one.txt' },
      ],
      characterSheetIds: ['sheet-1'],
      evidence: [
        {
          evidenceId: 'evidence-1',
          knowledgeDocumentId: 'doc-1',
          extractionVersion: 7,
          locator: 'page:3:span:2',
          excerpt: 'The keeper hides a silver key below the broken stair.',
        },
      ],
      stages: [
        {
          order: 1,
          scene: 'The keeper hides a silver key below the broken stair.',
          goal: '근거의 상황을 확인한다.',
          conflict: '근거의 갈등만 사용한다.',
          cluesAndNpcs: ['The keeper hides a silver key below the broken stair.'],
          transitionCondition: '상황이 해결되면 전환한다.',
          evidenceIds: ['evidence-1'],
        },
      ],
    }
    const api = {
      read: vi.fn().mockResolvedValue({
        sessionId: 'session-1',
        characterLimit: 4,
        version: 1,
        status: 'DRAFT',
        adventureId: null,
        runtimeConfiguration: null,
        party: [{
          characterSheetId: 'sheet-1',
          controlMode: 'DIRECT',
          nameMutableAfterStart: true,
          raceMutableAfterStart: true,
          characterClassMutableAfterStart: true,
          backgroundMutableAfterStart: true,
          startingAbilitiesMutableAfterStart: true,
          levelMutableAfterStart: true,
        }],
      }),
      readCampaignPlan: vi.fn().mockRejectedValue(new Error('not prepared')),
      prepareCampaignPlan: vi.fn().mockResolvedValue(plan),
      addMember: vi.fn(),
      removeMember: vi.fn(),
      start: vi.fn(),
      complete: vi.fn(),
      delete: vi.fn(),
    }

    render(<AdventureSessionPanel api={api} sessionId="session-1" />)
    await userEvent.click(await screen.findByRole('button', { name: '캠페인 계획 준비' }))

    expect(api.prepareCampaignPlan).toHaveBeenCalledWith('session-1')
    expect(await screen.findByRole('heading', { name: '캠페인 단계 계획' })).toBeTruthy()
    expect(screen.getByText(/계획 revision 1/)).toBeTruthy()
    expect(screen.getByText(/page:3:span:2/)).toBeTruthy()
  })
})
