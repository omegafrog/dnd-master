import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AdventureWorkspace } from './AdventureWorkspace'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { ScenarioBundleView, ScenarioPackageView, SetupApi } from '../rulebooks/SetupApi'

describe('AdventureWorkspace 세션 연결', () => {
  afterEach(() => {
    window.location.hash = ''
  })

  it('런타임 연결의 자료 묶음으로 실제 세션 목록을 조회하고 이어간다', async () => {
    const session = makeSession()
    const sessionApi = {
      listByScenarioPackage: vi.fn().mockResolvedValue([session]),
      create: vi.fn(),
      read: vi.fn().mockResolvedValue(session),
    } as unknown as Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage' | 'read'>
    const setupApi = makeSetupApi()

    render(<AdventureWorkspace adventureId="adventure-1" activeTab="sessions" playApi={makePlayApi()} setupApi={setupApi} sessionApi={sessionApi} playerId="player-1" />)

    const list = await screen.findByRole('list', { name: '이 모험의 세션 목록' })
    expect(sessionApi.listByScenarioPackage).toHaveBeenCalledWith('package-1')
    expect(within(list).getByText('진행 중')).toBeInTheDocument()

    await userEvent.click(within(list).getByRole('button', { name: '이어하기' }))
    expect(window.location.hash).toBe('#/sessions/session-1?mode=play')
  })

  it('게시된 캐릭터 설정으로 새 세션을 생성하고 파티 구성으로 이동한다', async () => {
    const created = { ...makeSession(), sessionId: 'session-new', status: 'DRAFT', adventureId: null }
    const create = vi.fn().mockResolvedValue(created)
    const sessionApi = {
      listByScenarioPackage: vi.fn().mockResolvedValue([]),
      create,
      read: vi.fn().mockResolvedValue(created),
    } as unknown as Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage' | 'read'>
    const setupApi = makeSetupApi()

    render(<AdventureWorkspace adventureId="adventure-1" activeTab="sessions" playApi={makePlayApi()} setupApi={setupApi} sessionApi={sessionApi} playerId="player-1" />)

    await screen.findByRole('heading', { name: '이 모험의 세션' })
    await userEvent.click(screen.getByRole('button', { name: /세션 시작/ }))
    await userEvent.click(screen.getByRole('button', { name: '새 세션 만들기' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      scenarioPackageId: 'package-1',
      blueprintId: 'package-1',
      blueprintRevision: 4,
      partySize: 4,
    }))
    expect(window.location.hash).toBe('#/sessions/session-new/party')
  })

  it('자료 역할을 변경하고 모험 자료 묶음에 저장한다', async () => {
    const bundle = makeBundle()
    const reviseScenarioBundle = vi.fn().mockResolvedValue({
      ...bundle,
      currentRevision: 2,
      documents: bundle.documents.map(document => document.knowledgeDocumentId === 'doc-map' ? { ...document, role: 'MAP' as const } : document),
    })
    const setupApi = {
      ...makeSetupApi(),
      listKnowledgeDocuments: vi.fn().mockResolvedValue([{ knowledgeDocumentId: 'doc-map', documentType: 'STORYBOOK', originalFilename: 'map.pdf', status: 'INDEXED', format: 'PDF' }]),
      getScenarioPackage: vi.fn().mockResolvedValue({ packageId: 'package-1', bundleId: 'bundle-1' } as unknown as ScenarioPackageView),
      getScenarioBundle: vi.fn().mockResolvedValue(bundle),
      reviseScenarioBundle,
    } as unknown as SetupApi
    const sessionApi = {
      listByScenarioPackage: vi.fn().mockResolvedValue([]),
      create: vi.fn(),
      read: vi.fn().mockResolvedValue(makeSession()),
    } as unknown as Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage' | 'read'>

    render(<AdventureWorkspace adventureId="adventure-1" activeTab="materials" playApi={makePlayApi()} setupApi={setupApi} sessionApi={sessionApi} playerId="player-1" />)

    const roleSelect = await screen.findByLabelText('map.pdf 역할')
    await userEvent.selectOptions(roleSelect, 'MAP')
    expect(roleSelect).toHaveValue('MAP')
    await userEvent.click(screen.getByRole('button', { name: '역할 변경사항 저장' }))

    await waitFor(() => expect(reviseScenarioBundle).toHaveBeenCalledWith('bundle-1', 'player-1', [{ knowledgeDocumentId: 'doc-map', role: 'MAP' }], { name: '테스트 모험', rulebookEdition: 'DND_5E_2024' }))
    expect(await screen.findByText('자료 역할을 v2로 저장했습니다.')).toBeInTheDocument()
  })
})

function makePlayApi(): AdventurePlayApi {
  return {
    listSaved: vi.fn().mockResolvedValue([{ id: 'adventure-1', title: '테스트 모험', statusLabel: '진행 중인 모험', resumable: true, updatedAt: '', version: 1 }]),
    getSessionKnowledgeSet: vi.fn().mockResolvedValue({ adventureId: 'adventure-1', sessionId: 'session-1', knowledgeDocumentIds: [] }),
  } as unknown as AdventurePlayApi
}

function makeSetupApi(): SetupApi {
  return {
    listKnowledgeDocuments: vi.fn().mockResolvedValue([]),
    getRuntimeBinding: vi.fn().mockResolvedValue({ scenarioPackageId: 'package-1' }),
    getPlayPreparation: vi.fn().mockResolvedValue({
      scenarioPackageId: 'package-1',
      bundleId: 'bundle-1',
      bundleRevision: 1,
      status: 'READY',
      blockers: [],
      characterCreationBlueprint: { status: 'PUBLISHED', revision: 4 },
      characterLimit: { maximumCharacters: 4, source: null, sourceQuote: '' },
    }),
  } as unknown as SetupApi
}

function makeSession(): AdventureSessionView {
  return {
    sessionId: 'session-1',
    scenarioPackageId: 'package-1',
    scenarioPackageRevision: 1,
    blueprintId: 'package-1',
    blueprintRevision: 4,
    characterEdition: 'DND_5E_2014',
    characterLimit: 4,
    version: 1,
    status: 'STARTED',
    adventureId: 'adventure-1',
    runtimeConfiguration: null,
    party: [],
  }
}

function makeBundle(): ScenarioBundleView {
  return {
    bundleId: 'bundle-1',
    name: '테스트 모험',
    rulebookEdition: 'DND_5E_2024',
    currentRevision: 1,
    documents: [{
      knowledgeDocumentId: 'doc-map',
      documentType: 'STORYBOOK',
      originalFilename: 'map.pdf',
      status: 'INDEXED',
      role: 'HANDOUT',
      extractionVersion: 1,
    }],
  }
}
