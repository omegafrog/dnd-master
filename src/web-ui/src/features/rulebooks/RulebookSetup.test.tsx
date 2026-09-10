import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RulebookSetup } from './RulebookSetup'
import type {
  BatchRulebookView,
  KnowledgeDocumentView,
  LegacyScenarioMigrationView,
  RulebookUploadDraft,
  ScenarioBundleContract,
  ScenarioBundleDraft,
  ScenarioBundleView,
  SetupApi,
  SourcePreviewView,
} from './SetupApi'

class FakeSetupApi implements SetupApi {
  uploadError = ''
  uploadCalls: Array<{ ownerId: string; documents: string[]; types: string[] }> = []
  createCalls: Array<{ documents: ScenarioBundleDraft[]; contract?: ScenarioBundleContract }> = []
  private listCalls = 0
  private knowledgeDocuments: KnowledgeDocumentView[]
  private preview: SourcePreviewView = {
    rulebookId: 'doc-1',
    knowledgeDocumentId: 'doc-1',
    documentType: 'STORYBOOK',
    originalFilename: 'phb.txt',
    format: 'TXT',
    status: 'INDEXED',
    content: 'alpha\nbeta',
    extractionVersion: 1,
    warnings: [],
    spans: [
      { kind: 'LINE', path: ['line 1'], pageNumber: null, bounds: null, lineNumber: 1, startInclusive: 0, endExclusive: 5, text: 'alpha', locator: 'line 1 chars 0-5' },
      { kind: 'LINE', path: ['line 2'], pageNumber: null, bounds: null, lineNumber: 2, startInclusive: 6, endExclusive: 10, text: 'beta', locator: 'line 2 chars 6-10' },
    ],
    assets: [],
  }

  constructor({ includeFailed = true, status = 'INDEXED' as KnowledgeDocumentView['status'] } = {}) {
    this.knowledgeDocuments = [
      { knowledgeDocumentId: 'doc-1', documentType: 'STORYBOOK', originalFilename: 'phb.txt', status, format: 'TXT', progress: status === 'PROCESSING' ? { stage: 'INDEXING', percent: 50 } : undefined },
      { knowledgeDocumentId: 'doc-3', documentType: 'STORYBOOK', originalFilename: 'castle.pdf', status: 'INDEXED', format: 'PDF' },
      ...(includeFailed ? [{ knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK' as const, originalFilename: 'campaign.md', status: 'FAILED' as const, format: 'TXT' as const, failureReason: 'indexer timeout' }] : []),
    ]
  }

  async uploadRulebooks(documents: RulebookUploadDraft[], ownerId: string): Promise<BatchRulebookView[]> {
    this.uploadCalls.push({ ownerId, documents: documents.map(document => document.file.name), types: documents.map(document => document.documentType) })
    if (this.uploadError) throw new Error(this.uploadError)
    return documents.map((document, index) => ({ knowledgeDocumentId: index === 0 ? 'doc-1' : null, documentType: 'STORYBOOK', originalFilename: document.file.name, status: index === 0 ? 'ACCEPTED' : 'VALIDATION_FAILED', failureReason: index === 0 ? null : 'unsupported format' }))
  }
  async getRulebookStatus(rulebookId: string) { return { rulebookId, status: 'INDEXED' as const } }
  async retryKnowledgeDocument(knowledgeDocumentId: string) {
    this.knowledgeDocuments = this.knowledgeDocuments.map(document => document.knowledgeDocumentId === knowledgeDocumentId ? { ...document, status: 'QUEUED' as const, failureReason: null } : document)
    return { rulebookId: knowledgeDocumentId, status: 'QUEUED' as const }
  }
  async getSourcePreview(knowledgeDocumentId: string) {
    if (knowledgeDocumentId !== this.preview.knowledgeDocumentId) throw new Error('not found')
    return this.preview
  }
  async uploadScenario(file: File) { return { id: 'scenario-1', name: file.name } }
  async migrateLegacyScenario(): Promise<LegacyScenarioMigrationView> { throw new Error('not used') }
  async reuploadLegacyScenario(): Promise<LegacyScenarioMigrationView> { throw new Error('not used') }
  async createScenarioBundle(_ownerId: string, documents: ScenarioBundleDraft[], contract?: ScenarioBundleContract) {
    this.createCalls.push({ documents, contract })
    return bundle('bundle-1', 1, documents.map(document => ({
      knowledgeDocumentId: document.knowledgeDocumentId,
      documentType: document.role === 'RULEBOOK' ? 'RULEBOOK' as const : 'STORYBOOK' as const,
      originalFilename: document.role === 'RULEBOOK' ? 'D&D 5e' : 'phb.txt',
      status: 'INDEXED' as const,
      role: document.role,
      extractionVersion: 1,
    })))
  }
  async reviseScenarioBundle() { return bundle('bundle-1', 2, []) }
  async getScenarioBundle() { return bundle('bundle-1', 1, []) }
  async createCharacterSheet() { return { characterSheetId: 'sheet-1', adventureId: 'adventure-1', edition: 'DND_5E_2024', characterName: 'Aria', level: 1, inspiration: false, version: 0 } }
  async saveRuleSet() {}
  async listKnowledgeDocuments(ownerId: string) {
    void ownerId
    this.listCalls += 1
    if (this.listCalls > 1) {
      this.knowledgeDocuments = this.knowledgeDocuments.map(document => document.knowledgeDocumentId === 'doc-1' && document.status === 'PROCESSING'
        ? { ...document, status: 'INDEXED' as const, progress: { stage: 'READY' as const, percent: 100 } }
        : document)
    }
    return this.knowledgeDocuments
  }
}

function stubCatalog() {
  vi.stubGlobal('fetch', async () => new Response(JSON.stringify([{
    catalogRevisionId: 'catalog-1',
    edition: 'DND_5E_2024',
    displayName: 'D&D 5e 2024',
    rulebookId: 'rulebook-1',
    revisionNumber: 3,
    status: 'READY',
  }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
}

async function enterMaterials(user: ReturnType<typeof userEvent.setup>, name = '고성의 밤') {
  await user.click(screen.getByRole('button', { name: '시작하기' }))
  await screen.findByText('D&D 5e 2024')
  await user.type(screen.getByLabelText('모험 이름'), name)
  await user.click(screen.getByRole('button', { name: '다음 단계' }))
  expect(screen.getByRole('heading', { name: '모험 자료를 추가하세요' })).toBeInTheDocument()
}

describe('new adventure setup', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('starts with the onboarding screen and moves through basic information', async () => {
    stubCatalog()
    const user = userEvent.setup()
    render(<RulebookSetup api={new FakeSetupApi({ includeFailed: false })} playerId="p1" />)

    expect(screen.getByRole('heading', { name: '새로운 모험을 시작하세요' })).toBeInTheDocument()
    expect(screen.getByText('기본 정보')).toBeInTheDocument()
    expect(screen.getByText('자료 추가')).toBeInTheDocument()
    expect(screen.getByText('준비 완료')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '시작하기' }))
    expect(screen.getByRole('heading', { name: '모험의 기본 정보를 입력하세요' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음 단계' })).toBeDisabled()
    await screen.findByText('D&D 5e 2024')
    await user.type(screen.getByLabelText('모험 이름'), '폭풍왕의 천둥')
    expect(screen.getByRole('button', { name: '다음 단계' })).toBeEnabled()
  })

  it('uploads materials inline and keeps them in the shared material list', async () => {
    stubCatalog()
    const api = new FakeSetupApi({ includeFailed: false })
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    await enterMaterials(user)

    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [
      new File(['story'], 'campaign.pdf', { type: 'application/pdf' }),
      new File(['map'], 'map.png', { type: 'image/png' }),
    ] } })
    await user.click(screen.getByRole('button', { name: '자료 추가' }))

    expect(api.uploadCalls[0].types).toEqual(['STORYBOOK', 'STORYBOOK'])
    expect(await screen.findByText('phb.txt')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'phb.txt 모험 자료 선택' })).toBeChecked()
    expect(screen.getByLabelText('phb.txt 역할')).toHaveValue('MAIN_SCENARIO')
  })

  it('shows processing progress inline and resumes polling', async () => {
    stubCatalog()
    const user = userEvent.setup()
    render(<RulebookSetup api={new FakeSetupApi({ includeFailed: false, status: 'PROCESSING' })} playerId="p1" />)
    await enterMaterials(user)

    expect(await screen.findByRole('progressbar', { name: 'phb.txt 자료 준비 진행률' })).toHaveAttribute('aria-valuenow', '50')
    await waitFor(() => expect(screen.queryByRole('progressbar', { name: 'phb.txt 자료 준비 진행률' })).not.toBeInTheDocument(), { timeout: 2500 })
  })

  it('retries a failed material without exposing backend pipeline controls', async () => {
    stubCatalog()
    const user = userEvent.setup()
    render(<RulebookSetup api={new FakeSetupApi()} playerId="p1" />)
    await enterMaterials(user)

    await user.click(await screen.findByRole('button', { name: 'campaign.md 다시 처리' }))
    expect(screen.getByRole('status')).toHaveTextContent('자료를 다시 준비하고 있습니다.')
    expect(screen.queryByText('Pipeline')).not.toBeInTheDocument()
  })

  it('opens source preview in a sheet instead of expanding the page', async () => {
    stubCatalog()
    const user = userEvent.setup()
    render(<RulebookSetup api={new FakeSetupApi({ includeFailed: false })} playerId="p1" />)
    await enterMaterials(user)

    await user.click(await screen.findByRole('button', { name: 'phb.txt 미리보기' }))
    const preview = screen.getByRole('dialog')
    expect(within(preview).getByRole('heading', { name: 'phb.txt' })).toBeInTheDocument()
    expect(within(preview).getByRole('list', { name: '원문 줄 미리보기' })).toHaveTextContent('alpha')
  })

  it('creates the bundle with the entered adventure name and visible material role', async () => {
    stubCatalog()
    const api = new FakeSetupApi({ includeFailed: false })
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    await enterMaterials(user, '폭풍왕의 천둥')

    await user.click(screen.getByRole('checkbox', { name: 'phb.txt 모험 자료 선택' }))
    await waitFor(() => expect(screen.getByRole('button', { name: '모험 준비' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: '모험 준비' }))

    expect(api.createCalls).toHaveLength(1)
    expect(api.createCalls[0].contract).toEqual({ name: '폭풍왕의 천둥', rulebookEdition: 'DND_5E_2024' })
    expect(api.createCalls[0].documents).toEqual(expect.arrayContaining([
      { knowledgeDocumentId: 'doc-1', role: 'MAIN_SCENARIO' },
      { knowledgeDocumentId: 'rulebook-1', role: 'RULEBOOK' },
    ]))
    expect(await screen.findByRole('heading', { name: '모험을 준비하고 있습니다' })).toBeInTheDocument()
  })
})

function bundle(bundleId: string, currentRevision: number, documents: ScenarioBundleView['documents']): ScenarioBundleView {
  return { bundleId, ownerPlayerId: 'p1', currentRevision, documents }
}
