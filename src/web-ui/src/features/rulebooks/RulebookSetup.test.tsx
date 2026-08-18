import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RulebookSetup } from './RulebookSetup'
import type {
  BatchRulebookView,
  KnowledgeDocumentView,
  LegacyScenarioMigrationView,
  ScenarioBundleView,
  RulebookUploadDraft,
  SetupApi,
  SourcePreviewView,
} from './SetupApi'

class FakeSetupApi implements SetupApi {
  uploadError = ''
  uploadCalls: Array<{ ownerId: string; documents: string[]; types: string[] }> = []
  private knowledgeDocuments: KnowledgeDocumentView[]
  private preview: SourcePreviewView = {
    rulebookId: 'doc-1',
    knowledgeDocumentId: 'doc-1',
    documentType: 'RULEBOOK',
    originalFilename: 'phb.txt',
    format: 'TXT',
    status: 'EXTRACTED',
    content: 'alpha\nbeta',
    extractionVersion: 1,
    warnings: [],
    spans: [
      { kind: 'LINE', path: ['line 1'], pageNumber: null, bounds: null, lineNumber: 1, startInclusive: 0, endExclusive: 5, text: 'alpha', locator: 'line 1 chars 0-5' },
      { kind: 'LINE', path: ['line 2'], pageNumber: null, bounds: null, lineNumber: 2, startInclusive: 6, endExclusive: 10, text: 'beta', locator: 'line 2 chars 6-10' },
    ],
    assets: [],
  }
  private results: BatchRulebookView[] = [
    { knowledgeDocumentId: 'doc-1', documentType: 'RULEBOOK', originalFilename: 'phb.pdf', status: 'ACCEPTED' },
    { knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK', originalFilename: 'campaign.md', status: 'VALIDATION_FAILED', failureReason: 'unsupported format' },
  ]

  private listCalls = 0

  constructor(includeFailedDocument = true, indexedDocuments = false, initialStatus: KnowledgeDocumentView['status'] | null = null) {
    const documentStatus = initialStatus ?? (indexedDocuments ? 'INDEXED' : 'EXTRACTED')
    this.knowledgeDocuments = [
      { knowledgeDocumentId: 'doc-1', documentType: 'STORYBOOK', originalFilename: 'phb.txt', status: documentStatus, format: 'TXT' as const, progress: documentStatus === 'PROCESSING' ? { stage: 'INDEXING' as const, percent: 50 } : undefined },
      { knowledgeDocumentId: 'doc-3', documentType: 'STORYBOOK', originalFilename: 'castle.pdf', status: indexedDocuments ? 'INDEXED' as const : 'EXTRACTED' as const, format: 'PDF' as const },
      ...(includeFailedDocument
        ? [{ knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK' as const, originalFilename: 'campaign.md', status: 'FAILED' as const, format: 'TXT' as const, failureReason: 'indexer timeout' }]
        : []),
    ]
  }

  async uploadRulebooks(documents: RulebookUploadDraft[], ownerId: string) {
    this.uploadCalls.push({ ownerId, documents: documents.map(document => document.file.name), types: documents.map(document => document.documentType) })
    if (this.uploadError) throw new Error(this.uploadError)
    return this.results
  }
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async getRulebookStatus(_rulebookId: string) {
    return { rulebookId: 'phb', status: 'INDEXED' as const }
  }
  async retryKnowledgeDocument(knowledgeDocumentId: string) {
    this.knowledgeDocuments = this.knowledgeDocuments.map(document => document.knowledgeDocumentId === knowledgeDocumentId
      ? { ...document, status: 'QUEUED' as const, failureReason: null }
      : document)
    return { rulebookId: knowledgeDocumentId, status: 'QUEUED' as const }
  }
  async getSourcePreview(knowledgeDocumentId: string) {
    if (knowledgeDocumentId !== this.preview.knowledgeDocumentId) throw new Error('not found')
    return this.preview
  }
  async uploadScenario(file: File) { return { id: 'scenario-1', name: file.name } }
  async migrateLegacyScenario(): Promise<LegacyScenarioMigrationView> { throw new Error('not used') }
  async reuploadLegacyScenario(): Promise<LegacyScenarioMigrationView> { throw new Error('not used') }
  async createScenarioBundle() { return bundle('bundle-1', 1, []) }
  async reviseScenarioBundle() { return bundle('bundle-1', 2, []) }
  async getScenarioBundle() { return bundle('bundle-1', 1, []) }
  async createCharacterSheet() {
    return {
      characterSheetId: 'sheet-1',
      adventureId: 'adventure-1',
      edition: 'DND_5E_2024',
      characterName: 'Aria',
      level: 1,
      inspiration: false,
      version: 0,
    }
  }
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

describe('rulebook and adventure setup', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('uploads user documents as storybooks only', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('자료 파일'), {
      target: { files: [
        new File(['rules'], 'phb.pdf', { type: 'application/pdf' }),
        new File(['story'], 'campaign.md', { type: 'text/markdown' }),
      ] },
    })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))

    expect(api.uploadCalls[0].types).toEqual(['STORYBOOK', 'STORYBOOK'])
    expect(screen.queryByLabelText('phb.pdf 유형')).not.toBeInTheDocument()

    expect(await screen.findByRole('checkbox', { name: 'phb.pdf' })).not.toBeChecked()
    const uploadStatus = within(screen.getByRole('list', { name: '자료 처리 상태' }))
    expect(uploadStatus.getByText('phb.pdf')).toBeInTheDocument()
    expect(uploadStatus.getByText(/검증 실패/)).toBeInTheDocument()
    expect(uploadStatus.queryByText(/사용 준비 완료/)).not.toBeInTheDocument()
  })

  it('displays upload error', async () => {
    const api = new FakeSetupApi()
    api.uploadError = '지원하지 않거나 손상된 파일입니다.'
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [new File(['bad'], 'bad.pdf')] } })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(api.uploadError))
  })

  it('shows document status and retries only the failed document', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)

    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [new File(['story'], 'campaign.md')] } })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))

    const statusList = await screen.findByRole('list', { name: '문서 상태 목록' })
    const failedDocument = within(statusList).getByText('campaign.md')
    expect(within(failedDocument.closest('li')!).getByText(/indexer timeout/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 처리' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '다시 처리' }))

    const retriedDocument = within(screen.getByRole('list', { name: '문서 상태 목록' })).getByText('campaign.md').closest('li')!
    expect(within(retriedDocument).queryByText(/indexer timeout/)).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('다시 처리했습니다.')
  })

  it('renders server progress and resumes polling for a pre-existing processing document', async () => {
    const api = new FakeSetupApi(false, false, 'PROCESSING')
    render(<RulebookSetup api={api} playerId="p1" />)

    expect(await screen.findByRole('progressbar', { name: '전체 자료 준비 진행률' })).toHaveAttribute('aria-valuenow', '50')
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'phb.txt 모험 자료 선택' })).toBeEnabled(), { timeout: 2500 })
  })

  it('shows a source preview for an extracted TXT document', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)

    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [new File(['rules'], 'phb.pdf')] } })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))

    const txtRow = within(await screen.findByRole('list', { name: '문서 상태 목록' })).getByText('phb.txt')
    await user.click(within(txtRow.closest('li')!).getByRole('button', { name: '미리보기' }))

    expect(await screen.findByRole('heading', { name: 'phb.txt 미리보기' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '원문 줄 미리보기' })).toHaveTextContent('LINE · line 1')
  })

  it('keeps one document visible after the same file is uploaded again', async () => {
    const api = new FakeSetupApi(false)
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)

    const file = new File(['rules'], 'phb.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [file] } })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))
    expect(await screen.findAllByText('phb.pdf')).toHaveLength(1)

    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [file] } })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))

    expect(api.uploadCalls).toHaveLength(2)
    expect(screen.getAllByText('phb.pdf')).toHaveLength(1)
    expect(screen.getAllByRole('checkbox', { name: 'phb.pdf' })).toHaveLength(1)
  })

  it('saves a scenario bundle', async () => {
    const api = new FakeSetupApi(false, true)
    const user = userEvent.setup()
    vi.stubGlobal('fetch', async () => new Response(JSON.stringify([{
      catalogRevisionId: 'catalog-1',
      edition: 'DND_5E_2014',
      displayName: 'D&D 5e',
      rulebookId: 'doc-1',
      revisionNumber: 1,
      status: 'READY',
    }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    render(<RulebookSetup api={api} playerId="p1" />)

    await user.click(await screen.findByRole('checkbox', { name: 'D&D 5e 선택' }))
    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [new File(['story'], 'campaign.md')] } })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))
    await user.click(screen.getByRole('checkbox', { name: 'phb.txt 모험 자료 선택' }))
    await user.click(screen.getByRole('button', { name: '모험 자료 저장' }))
    expect(await screen.findByText('이름 없는 모험 자료')).toBeInTheDocument()
    expect(screen.queryByText('모험 자료 저장 완료: bundle-1 v1')).not.toBeInTheDocument()
  })
})

function bundle(bundleId: string, currentRevision: number, documents: ScenarioBundleView['documents']): ScenarioBundleView {
  return { bundleId, ownerPlayerId: 'p1', currentRevision, documents }
}
