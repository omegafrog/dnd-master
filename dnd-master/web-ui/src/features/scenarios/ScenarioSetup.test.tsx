import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ScenarioSetup } from './ScenarioSetup'
import type {
  KnowledgeDocumentView,
  ScenarioBundleView,
  SetupApi,
  SourcePreviewView,
} from '../rulebooks/SetupApi'

class FakeSetupApi implements SetupApi {
  private readonly documents: KnowledgeDocumentView[] = [
    {
      knowledgeDocumentId: 'doc-1',
      documentType: 'STORYBOOK',
      originalFilename: 'main.pdf',
      status: 'EXTRACTED',
      format: 'PDF',
      extractionVersion: 3,
      warnings: [],
    },
    {
      knowledgeDocumentId: 'doc-2',
      documentType: 'STORYBOOK',
      originalFilename: 'handout.pdf',
      status: 'PARTIAL_CONFIRMED',
      format: 'PDF',
      extractionVersion: 7,
      warnings: ['page 3 failed'],
    },
    {
      knowledgeDocumentId: 'doc-3',
      documentType: 'RULEBOOK',
      originalFilename: 'rules.pdf',
      status: 'EXTRACTED',
      format: 'PDF',
      extractionVersion: 1,
      warnings: [],
    },
    {
      knowledgeDocumentId: 'doc-4',
      documentType: 'STORYBOOK',
      originalFilename: 'failed-main.pdf',
      status: 'FAILED',
      format: 'PDF',
      extractionVersion: undefined,
      warnings: [],
      failureReason: 'document parser stopped',
    },
  ]

  async uploadRulebooks() { return [] }
  async getRulebookStatus() { return { rulebookId: 'rulebook', status: 'INDEXED' as const } }
  async retryKnowledgeDocument() { return { rulebookId: 'rulebook', status: 'INDEXED' as const } }
  async getSourcePreview(): Promise<SourcePreviewView> { throw new Error('not used') }
  async uploadScenario() { return { id: 'legacy', name: 'legacy.pdf' } }
  async saveRuleSet() {}
  async listKnowledgeDocuments() { return this.documents }
  async createScenarioBundle() {
    return bundle('bundle-1', 1, [
      { knowledgeDocumentId: 'doc-1', documentType: 'STORYBOOK', originalFilename: 'main.pdf', status: 'EXTRACTED', role: 'MAIN_SCENARIO', extractionVersion: 3 },
      { knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK', originalFilename: 'handout.pdf', status: 'PARTIAL_CONFIRMED', role: 'HANDOUT', extractionVersion: 7 },
    ])
  }
  async reviseScenarioBundle() { return bundle('bundle-1', 2, [
    { knowledgeDocumentId: 'doc-1', documentType: 'STORYBOOK', originalFilename: 'main.pdf', status: 'EXTRACTED', role: 'REFERENCE', extractionVersion: 3 },
  ]) }
  async getScenarioBundle() { return bundle('bundle-1', 1, []) }
  async compileScenarioBundle() {
    return { packageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, inputFingerprint: 'fp', reportStatus: 'COMPLETE' as const, warnings: [], units: [] }
  }
}

function bundle(id: string, revision: number, documents: ScenarioBundleView['documents']): ScenarioBundleView {
  return {
    bundleId: id,
    ownerPlayerId: 'owner-1',
    currentRevision: revision,
    documents,
  }
}

describe('ScenarioSetup', () => {
  it('lets the owner assign roles to owned STORYBOOK documents and save a bundle revision', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<ScenarioSetup api={api} playerId="owner-1" onError={() => {}} />)

    expect(await screen.findByText('main.pdf')).toBeInTheDocument()
    expect(screen.queryByText('rules.pdf')).not.toBeInTheDocument()
    expect(screen.getByText('handout.pdf: 추출 경고가 있어 컴파일 위험이 있습니다.')).toBeInTheDocument()
    expect(screen.getByText('failed-main.pdf: 컴파일 위험 — document parser stopped')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('main.pdf 역할'), 'MAIN_SCENARIO')
    await user.selectOptions(screen.getByLabelText('handout.pdf 역할'), 'HANDOUT')
    await user.click(screen.getByRole('button', { name: '시나리오 번들 저장' }))

    expect(await screen.findByText('번들 저장 완료: bundle-1 v1')).toBeInTheDocument()
    expect(screen.getByText('main.pdf · MAIN_SCENARIO')).toBeInTheDocument()
    expect(screen.getByText('handout.pdf · HANDOUT')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '시나리오 패키지 컴파일' }))
    expect(await screen.findByText('패키지 package-1 · COMPLETE')).toBeInTheDocument()
  })

  it('can revise an existing bundle', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<ScenarioSetup api={api} playerId="owner-1" onError={() => {}} />)

    await screen.findByText('main.pdf')
    await user.click(screen.getByRole('button', { name: '시나리오 번들 저장' }))
    await user.click(screen.getByRole('button', { name: '시나리오 번들 다시 저장' }))

    expect(await screen.findByText('번들 저장 완료: bundle-1 v2')).toBeInTheDocument()
  })
})
