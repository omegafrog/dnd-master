import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { BundleDetailPage } from './BundleDetailPage'
import type { SetupApi, ScenarioBundleView, ScenarioPackageView } from './SetupApi'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'

describe('BundleDetailPage session cleanup', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('deletes a connected active session so its bundle can be removed', async () => {
    const bundle = makeBundle()
    const packageView = makePackage()
    const session = makeSession()
    const setupApi = {
      getScenarioBundle: vi.fn().mockResolvedValue(bundle),
      listKnowledgeDocuments: vi.fn().mockResolvedValue(bundle.documents),
      listScenarioPackages: vi.fn().mockResolvedValue([packageView]),
      getPlayPreparation: vi.fn().mockResolvedValue({
        scenarioPackageId: packageView.packageId,
        bundleId: bundle.bundleId,
        bundleRevision: 1,
        status: 'READY',
        blockers: [],
        characterCreationBlueprint: { available: true, summary: null, rulebookDocumentCount: 1, storybookDocumentCount: 1, diagnostics: [], status: 'PUBLISHED', revision: 1 },
        characterLimit: { maximumCharacters: 1, source: null, sourceQuote: '' },
      }),
    } as unknown as SetupApi
    const deleteSession = vi.fn().mockResolvedValue({ ...session, status: 'DELETED', version: session.version + 1 })
    const sessionApi = {
      create: vi.fn(),
      listByScenarioPackage: vi.fn().mockResolvedValue([session]),
      delete: deleteSession,
    } as unknown as Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage' | 'delete'>
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]', { status: 200 })))
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))

    render(<BundleDetailPage bundleId={bundle.bundleId} api={setupApi} playerId="p1" sessionApi={sessionApi} />)

    const sessions = await screen.findByRole('list', { name: '자료로 만든 모험 목록' })
    const row = within(sessions).getByRole('listitem')
    await userEvent.click(within(row).getByRole('button', { name: '세션 삭제' }))

    expect(deleteSession).toHaveBeenCalledWith(session.sessionId, session.version)
    await waitFor(() => expect(screen.getByText('이 자료로 생성된 모험 세션이 없습니다.')).toBeInTheDocument())
  })

  it('lists sessions connected to every saved package revision', async () => {
    const bundle = { ...makeBundle(), currentRevision: 2 }
    const currentPackage = { ...makePackage(), bundleRevision: 2 }
    const previousPackage = { ...makePackage(), packageId: 'package-previous', bundleRevision: 1 }
    const currentSession = makeSession()
    const previousSession = { ...makeSession(), sessionId: 'session-previous', scenarioPackageId: previousPackage.packageId, scenarioPackageRevision: previousPackage.bundleRevision }
    const setupApi = {
      getScenarioBundle: vi.fn().mockResolvedValue(bundle),
      listKnowledgeDocuments: vi.fn().mockResolvedValue(bundle.documents),
      listScenarioPackages: vi.fn().mockResolvedValue([currentPackage, previousPackage]),
      getPlayPreparation: vi.fn().mockResolvedValue({
        scenarioPackageId: currentPackage.packageId,
        bundleId: bundle.bundleId,
        bundleRevision: bundle.currentRevision,
        status: 'READY',
        blockers: [],
        characterCreationBlueprint: { available: true, summary: null, rulebookDocumentCount: 1, storybookDocumentCount: 1, diagnostics: [], status: 'PUBLISHED', revision: 1 },
        characterLimit: { maximumCharacters: 1, source: null, sourceQuote: '' },
      }),
    } as unknown as SetupApi
    const sessionApi = {
      create: vi.fn(),
      listByScenarioPackage: vi.fn().mockImplementation(async (packageId: string) => packageId === currentPackage.packageId ? [currentSession] : [previousSession]),
      delete: vi.fn(),
    } as unknown as Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage' | 'delete'>

    render(<BundleDetailPage bundleId={bundle.bundleId} api={setupApi} playerId="p1" sessionApi={sessionApi} />)

    const sessions = await screen.findByRole('list', { name: '자료로 만든 모험 목록' })
    expect(within(sessions).getAllByRole('listitem')).toHaveLength(2)
  })

  it('keeps sessions from revisions that load when another revision fails', async () => {
    const bundle = { ...makeBundle(), currentRevision: 2 }
    const currentPackage = { ...makePackage(), bundleRevision: 2 }
    const previousPackage = { ...makePackage(), packageId: 'package-previous', bundleRevision: 1 }
    const currentSession = makeSession()
    const setupApi = {
      getScenarioBundle: vi.fn().mockResolvedValue(bundle),
      listKnowledgeDocuments: vi.fn().mockResolvedValue(bundle.documents),
      listScenarioPackages: vi.fn().mockResolvedValue([currentPackage, previousPackage]),
    } as unknown as SetupApi
    const sessionApi = {
      create: vi.fn(),
      listByScenarioPackage: vi.fn().mockImplementation(async (packageId: string) => {
        if (packageId === previousPackage.packageId) throw new Error('previous revision unavailable')
        return [currentSession]
      }),
      delete: vi.fn(),
    } as unknown as Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage' | 'delete'>

    render(<BundleDetailPage bundleId={bundle.bundleId} api={setupApi} playerId="p1" sessionApi={sessionApi} />)

    const sessions = await screen.findByRole('list', { name: '자료로 만든 모험 목록' })
    expect(within(sessions).getAllByRole('listitem')).toHaveLength(1)
    expect(await screen.findByRole('status')).toHaveTextContent('1개 자료 버전의 연결 세션을 불러오지 못했습니다')
  })
})

function makeBundle(): ScenarioBundleView {
  return {
    bundleId: 'bundle-1',
    ownerPlayerId: 'p1',
    name: '테스트 모험',
    rulebookEdition: 'DND_5E_2014',
    currentRevision: 1,
    documents: [{
      knowledgeDocumentId: 'doc-1',
      documentType: 'STORYBOOK',
      originalFilename: 'story.pdf',
      status: 'INDEXED',
      role: 'MAIN_SCENARIO',
      extractionVersion: 1,
    }],
  }
}

function makePackage(): ScenarioPackageView {
  return {
    packageId: 'package-1',
    bundleId: 'bundle-1',
    bundleRevision: 1,
    inputFingerprint: 'fingerprint',
    reportStatus: 'COMPLETE',
    warnings: [],
    characterLimit: { maximumCharacters: 1, source: null, sourceQuote: '' },
    units: [],
  }
}

function makeSession(): AdventureSessionView {
  return {
    sessionId: 'session-1',
    scenarioPackageId: 'package-1',
    scenarioPackageRevision: 1,
    blueprintId: 'package-1',
    blueprintRevision: 1,
    characterEdition: 'DND_5E_2014',
    characterLimit: 1,
    version: 4,
    status: 'STARTED',
    adventureId: 'adventure-1',
    runtimeConfiguration: null,
    party: [],
  }
}
