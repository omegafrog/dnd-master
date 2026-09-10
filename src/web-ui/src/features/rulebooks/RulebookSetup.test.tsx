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
  ScenarioBundleDraft,
  ScenarioBundleView,
  SetupApi,
  SourcePreviewView,
} from './SetupApi'

class FakeSetupApi implements SetupApi {
  uploadError = ''
  uploadCalls: Array<{ ownerId: string; documents: string[]; types: string[] }> = []
  createCalls: ScenarioBundleDraft[][] = []
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
  async createScenarioBundle(_ownerId: string, documents: ScenarioBundleDraft[]) {
    this.createCalls.push(documents)
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

describe('new adventure setup', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses the same document hierarchy as the adventure workspace', async () => {
    stubCatalog()
    render(<RulebookSetup api={new FakeSetupApi({ includeFailed: false })} playerId="p1" />)

    expect(screen.getByRole('heading', { name: '새 모험 준비' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '룰북' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '모험 자료' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '준비 확인' })).toBeInTheDocument()
    expect(screen.queryByText('저장된 모험 자료')).not.toBeInTheDocument()
    expect(screen.queryByText('목록 새로고침')).not.toBeInTheDocument()
    expect(await screen.findByRole('checkbox', { name: 'D&D 5e 2024 선택' })).toBeChecked()
  })

  it('uploads new materials as storybooks from a focused dialog', async () => {
    stubCatalog()
    const api = new FakeSetupApi({ includeFailed: false })
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)

    await user.click(screen.getByRole('button', { name: '자료 추가' }))
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('자료 파일'), { target: { files: [
      new File(['story'], 'campaign.pdf', { type: 'application/pdf' }),
      new File(['map'], 'map.png', { type: 'image/png' }),
    ] } })
    await user.click(within(dialog).getByRole('button', { name: '자료 추가' }))

    expect(api.uploadCalls[0].types).toEqual(['STORYBOOK', 'STORYBOOK'])
    expect(await screen.findByText('phb.txt')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'phb.txt 모험 자료 선택' })).toBeChecked()
  })

  it('shows processing progress inline and resumes polling', async () => {
    stubCatalog()
    render(<RulebookSetup api={new FakeSetupApi({ includeFailed: false, status: 'PROCESSING' })} playerId="p1" />)

    expect(await screen.findByRole('progressbar', { name: 'phb.txt 자료 준비 진행률' })).toHaveAttribute('aria-valuenow', '50')
    await waitFor(() => expect(screen.queryByRole('progressbar', { name: 'phb.txt 자료 준비 진행률' })).not.toBeInTheDocument(), { timeout: 2500 })
  })

  it('retries a failed material without exposing backend pipeline controls', async () => {
    stubCatalog()
    const user = userEvent.setup()
    render(<RulebookSetup api={new FakeSetupApi()} playerId="p1" />)

    await user.click(await screen.findByRole('button', { name: 'campaign.md 다시 처리' }))
    expect(screen.getByRole('status')).toHaveTextContent('자료를 다시 준비하고 있습니다.')
    expect(screen.queryByText('Pipeline')).not.toBeInTheDocument()
  })

  it('opens source preview in a sheet instead of expanding the setup page', async () => {
    stubCatalog()
    const user = userEvent.setup()
    render(<RulebookSetup api={new FakeSetupApi({ includeFailed: false })} playerId="p1" />)

    await user.click(await screen.findByRole('button', { name: 'phb.txt 미리보기' }))
    const preview = screen.getByRole('dialog')
    expect(within(preview).getByRole('heading', { name: 'phb.txt' })).toBeInTheDocument()
    expect(within(preview).getByRole('list', { name: '원문 줄 미리보기' })).toHaveTextContent('alpha')
  })

  it('creates a bundle from selected material and its visible role', async () => {
    stubCatalog()
    const api = new FakeSetupApi({ includeFailed: false })
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)

    await user.click(await screen.findByRole('checkbox', { name: 'phb.txt 모험 자료 선택' }))
    await waitFor(() => expect(screen.getByRole('button', { name: '모험 만들기' })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: '모험 만들기' }))

    expect(api.createCalls).toHaveLength(1)
    expect(api.createCalls[0]).toEqual(expect.arrayContaining([
      { knowledgeDocumentId: 'doc-1', role: 'MAIN_SCENARIO' },
      { knowledgeDocumentId: 'rulebook-1', role: 'RULEBOOK' },
    ]))
    expect(await screen.findByRole('heading', { name: '모험 준비' })).toBeInTheDocument()
  })
})

function bundle(bundleId: string, currentRevision: number, documents: ScenarioBundleView['documents']): ScenarioBundleView {
  return { bundleId, ownerPlayerId: 'p1', currentRevision, documents }
}
