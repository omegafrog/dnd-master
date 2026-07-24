import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ScenarioSetup } from './ScenarioSetup'
import type {
  ScenarioCompilationView,
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
  async getScenarioPackage() {
    return {
      packageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, inputFingerprint: 'fp', reportStatus: 'COMPLETE' as const,
      warnings: [], units: [{
        kind: 'SAVING_THROW' as const,
        status: 'PARTIAL' as const,
        abilityOrSkill: 'Dexterity',
        dc: 15,
        diceExpression: null,
        visibility: 'GM_REFERENCE',
        sourceQuote: 'Dexterity save DC 15; on a failure take 4d6 fire damage, half on a success.',
        provenance: 'ai-v2',
        validationMessages: ['roller is missing'],
        runtimeCapabilities: ['ATTACK_OR_SAVE', 'DAMAGE'],
        detail: {
          triggerCondition: 'Touching the trapped idol',
          actor: null,
          roller: null,
          instructionVisibility: null,
          resultVisibility: null,
          modifiers: ['creature touching idol'],
          advantageState: null,
          reroll: null,
          steps: [
            { id: 'save', kind: 'SAVING_THROW', abilityOrSkill: 'Dexterity', dc: 15, diceExpression: null, condition: null, nextStepIds: ['damage'], successOutcomeIds: ['half'], failureOutcomeIds: ['full'], sourceRefs: [{ documentId: 'doc-1', extractionVersion: 3, locator: 'page:4' }] },
            { id: 'damage', kind: 'DAMAGE_ROLL', abilityOrSkill: null, dc: null, diceExpression: '4d6', condition: null, nextStepIds: [], successOutcomeIds: ['full', 'half'], failureOutcomeIds: [], sourceRefs: [{ documentId: 'doc-1', extractionVersion: 3, locator: 'page:4' }] },
          ],
          outcomes: [
            { id: 'full', label: 'FAILURE', description: 'Take 4d6 fire damage.', sourceRefs: [{ documentId: 'doc-1', extractionVersion: 3, locator: 'page:4' }] },
            { id: 'half', label: 'SUCCESS', description: 'Take half damage.', sourceRefs: [{ documentId: 'doc-1', extractionVersion: 3, locator: 'page:4' }] },
          ],
          randomTable: [],
          tableCoverage: null,
        },
        sourceRefs: [{ documentId: 'doc-1', extractionVersion: 3, locator: 'page:4' }],
      }],
    }
  }
  async startScenarioCompilation() {
    return {
      compilationId: 'compilation-1',
      bundleId: 'bundle-1',
      bundleRevision: 1,
      status: 'REQUESTED' as const,
      attempt: 0,
      packageId: null,
      failureReason: null,
    }
  }
  async getScenarioCompilation(): Promise<ScenarioCompilationView> {
    return {
      compilationId: 'compilation-1',
      bundleId: 'bundle-1',
      bundleRevision: 1,
      status: 'PUBLISHED',
      attempt: 1,
      packageId: 'package-1',
      failureReason: null,
    }
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
  afterEach(() => {
    vi.useRealTimers()
  })

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
    expect(await screen.findByText('컴파일 상태 REQUESTED · 시도 0')).toBeInTheDocument()
    expect(await screen.findByText('패키지 package-1 · COMPLETE')).toBeInTheDocument()
    expect(screen.getByText('SAVING_THROW · Dexterity · DC 15 · PARTIAL')).toBeInTheDocument()
    expect(screen.getByText('visibility: GM_REFERENCE · 근거: Dexterity save DC 15; on a failure take 4d6 fire damage, half on a success. · provenance: ai-v2')).toBeInTheDocument()
    expect(screen.getByText('runtime: ATTACK_OR_SAVE, DAMAGE')).toBeInTheDocument()
    expect(screen.getByText('trigger: Touching the trapped idol')).toBeInTheDocument()
    expect(screen.getByText('step save: SAVING_THROW · Dexterity · DC 15')).toBeInTheDocument()
    expect(screen.getByText('step damage: DAMAGE_ROLL · 4d6')).toBeInTheDocument()
    expect(screen.getByText('outcome full: FAILURE · Take 4d6 fire damage.')).toBeInTheDocument()
    expect(screen.getByText('source: doc-1 v3 · page:4')).toBeInTheDocument()
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

  it('polls compilation status until the package is published', async () => {
    const api = new FakeSetupApi()
    const statuses: ScenarioCompilationView[] = [
      {
        compilationId: 'compilation-1',
        bundleId: 'bundle-1',
        bundleRevision: 1,
        status: 'WAITING_RETRY',
        attempt: 1,
        packageId: null,
        failureReason: 'AI timeout',
      },
      {
        compilationId: 'compilation-1',
        bundleId: 'bundle-1',
        bundleRevision: 1,
        status: 'RUNNING',
        attempt: 1,
        packageId: null,
        failureReason: null,
      },
      {
        compilationId: 'compilation-1',
        bundleId: 'bundle-1',
        bundleRevision: 1,
        status: 'PUBLISHED',
        attempt: 1,
        packageId: 'package-1',
        failureReason: null,
      },
    ]
    api.getScenarioCompilation = vi.fn(async () => statuses.shift() ?? statuses[statuses.length - 1])
    const user = userEvent.setup()
    render(<ScenarioSetup api={api} playerId="owner-1" onError={() => {}} />)

    await screen.findByText('main.pdf')
    await user.click(screen.getByRole('button', { name: '시나리오 번들 저장' }))
    await user.click(screen.getByRole('button', { name: '시나리오 패키지 컴파일' }))

    expect(await screen.findByText('컴파일 상태 REQUESTED · 시도 0')).toBeInTheDocument()
    expect(await screen.findByText('컴파일 상태 WAITING_RETRY · 시도 1')).toBeInTheDocument()
    expect(await screen.findByText('패키지 package-1 · COMPLETE')).toBeInTheDocument()
    expect(api.getScenarioCompilation).toHaveBeenCalled()
    expect(screen.getByText('컴파일 상태 PUBLISHED · 시도 1')).toBeInTheDocument()
  }, 10000)
})
