import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PackageBlueprintReviewPage } from './PackageBlueprintReviewPage'
import type { PlayPreparationView } from '../rulebooks/SetupApi'
import type { AdventureSessionView } from '../adventure-session/AdventureSessionApi'

function preparation(status: 'READY' | 'PUBLISHED', revision: number): PlayPreparationView {
  return {
    scenarioPackageId: 'package-1',
    bundleId: 'bundle-1',
    bundleRevision: 1,
    status: 'READY',
    blockers: [],
    characterLimit: { maximumCharacters: 1, source: null, sourceQuote: '' },
    characterCreationBlueprint: {
      available: true,
      summary: null,
      rulebookDocumentCount: 1,
      storybookDocumentCount: 1,
      diagnostics: [],
      revision,
      status,
      roots: [],
    },
  }
}

describe('PackageBlueprintReviewPage', () => {
  it('publishes blueprint before creating the session', async () => {
    const user = userEvent.setup()
    let published = false
    const setupApi = {
      getPlayPreparation: vi.fn(async () => preparation(published ? 'PUBLISHED' : 'READY', published ? 2 : 1)),
      publishBlueprint: vi.fn(async () => { published = true }),
    }
    const sessionApi = {
      create: vi.fn(async (): Promise<AdventureSessionView> => ({
        sessionId: 'session-1',
        scenarioPackageId: 'package-1',
        scenarioPackageRevision: 1,
        blueprintId: 'package-1',
        blueprintRevision: 2,
        characterLimit: 1,
        version: 0,
        status: 'DRAFT',
        adventureId: null,
        runtimeConfiguration: null,
        party: [],
      })),
    }
    const onSessionCreated = vi.fn()

    render(<PackageBlueprintReviewPage packageId="package-1" setupApi={setupApi} sessionApi={sessionApi} onSessionCreated={onSessionCreated} />)

    await user.click(await screen.findByRole('button', { name: '검토 완료 후 게시' }))
    expect(setupApi.publishBlueprint).toHaveBeenCalledWith('package-1')

    await user.click(await screen.findByRole('button', { name: '세션 생성 후 캐릭터 만들기로 이동' }))
    expect(sessionApi.create).toHaveBeenCalledWith({ scenarioPackageId: 'package-1', blueprintId: 'package-1', blueprintRevision: 2 })
    expect(onSessionCreated).toHaveBeenCalledWith('session-1')
  })
})
