import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
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
  uploadCalls: Array<{ ownerId: string; documents: string[] }> = []
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

  constructor(includeFailedDocument = true) {
    this.knowledgeDocuments = [
      { knowledgeDocumentId: 'doc-1', documentType: 'RULEBOOK', originalFilename: 'phb.txt', status: 'EXTRACTED' as const, format: 'TXT' as const },
      { knowledgeDocumentId: 'doc-3', documentType: 'STORYBOOK', originalFilename: 'castle.pdf', status: 'EXTRACTED' as const, format: 'PDF' as const },
      ...(includeFailedDocument
        ? [{ knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK' as const, originalFilename: 'campaign.md', status: 'FAILED' as const, format: 'TXT' as const, failureReason: 'indexer timeout' }]
        : []),
    ]
  }

  async uploadRulebooks(documents: RulebookUploadDraft[], ownerId: string) {
    this.uploadCalls.push({ ownerId, documents: documents.map(document => document.file.name) })
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
  async saveRuleSet() {}
  async listKnowledgeDocuments(ownerId: string) {
    void ownerId
    return this.knowledgeDocuments
  }
}

describe('rulebook and adventure setup', () => {
  it('uploads mixed documents and shows per-file status', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('자료 파일'), {
      target: { files: [
        new File(['rules'], 'phb.pdf', { type: 'application/pdf' }),
        new File(['story'], 'campaign.md', { type: 'text/markdown' }),
      ] },
    })
    await user.selectOptions(screen.getByLabelText('phb.pdf 유형'), 'RULEBOOK')
    await user.selectOptions(screen.getByLabelText('campaign.md 유형'), 'STORYBOOK')
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))

    expect(await screen.findByRole('checkbox', { name: 'phb.pdf' })).toBeChecked()
    expect(screen.getByText((_, element) => element?.tagName === 'LI' && element.textContent?.includes('사용 준비 완료') === true)).toBeInTheDocument()
    expect(screen.getByText((_, element) => element?.tagName === 'LI' && element.textContent?.includes('검증 실패') === true)).toBeInTheDocument()
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

    const failedDocument = await screen.findByText('campaign.md')
    expect(within(failedDocument.closest('li')!).getByText(/indexer timeout/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 처리' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '다시 처리' }))

    const retriedDocument = screen.getByText('campaign.md').closest('li')!
    expect(within(retriedDocument).queryByText(/indexer timeout/)).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('다시 처리했습니다.')
  })

  it('shows a source preview for an extracted TXT document', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)

    const txtRow = await screen.findByText('phb.txt')
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
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    await screen.findByText('campaign.md')
    await user.click(screen.getByRole('button', { name: '시나리오 번들 저장' }))
    expect(await screen.findByText('번들 저장 완료: bundle-1 v1')).toBeInTheDocument()
  })
})

function bundle(bundleId: string, currentRevision: number, documents: ScenarioBundleView['documents']): ScenarioBundleView {
  return { bundleId, ownerPlayerId: 'p1', currentRevision, documents }
}
