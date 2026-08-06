import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ScenarioSetup, serializeBlueprintValues } from './ScenarioSetup'
import type { CharacterInputNodeView } from '../rulebooks/SetupApi'
import type {
  CreatedCharacterSheetView,
  CharacterCreationBlueprintView,
  KnowledgeDocumentView,
  LegacyScenarioMigrationView,
  PlayPreparationView,
  RuntimeOptionsView,
  ScenarioBundleView,
  ScenarioCompilationView,
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
  async migrateLegacyScenario(): Promise<LegacyScenarioMigrationView> { throw new Error('not used') }
  async reuploadLegacyScenario(): Promise<LegacyScenarioMigrationView> { throw new Error('not used') }
  async saveRuleSet() {}
  async listKnowledgeDocuments() { return this.documents }
  async createScenarioBundle() {
    return bundle('bundle-1', 1, [
      { knowledgeDocumentId: 'doc-1', documentType: 'STORYBOOK', originalFilename: 'main.pdf', status: 'EXTRACTED', role: 'MAIN_SCENARIO', extractionVersion: 3 },
      { knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK', originalFilename: 'handout.pdf', status: 'PARTIAL_CONFIRMED', role: 'HANDOUT', extractionVersion: 7 },
      { knowledgeDocumentId: 'doc-3', documentType: 'RULEBOOK', originalFilename: 'rules.pdf', status: 'EXTRACTED', role: 'RULEBOOK', extractionVersion: 1 },
    ])
  }
  async reviseScenarioBundle() { return bundle('bundle-1', 2, [
    { knowledgeDocumentId: 'doc-1', documentType: 'STORYBOOK', originalFilename: 'main.pdf', status: 'EXTRACTED', role: 'REFERENCE', extractionVersion: 3 },
  ]) }
  async getScenarioBundle() { return bundle('bundle-1', 1, []) }
  async createCharacterSheet(): Promise<CreatedCharacterSheetView> {
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
  async getScenarioPackage() {
    return {
      packageId: 'package-1',
      bundleId: 'bundle-1',
      bundleRevision: 1,
      inputFingerprint: 'fp',
      reportStatus: 'COMPLETE' as const,
      warnings: [],
      characterLimit: {
        maximumCharacters: 2,
        source: { documentId: 'doc-1', extractionVersion: 3, locator: 'page:1' },
        sourceQuote: '최대 2명',
      },
      units: [{
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
          steps: [],
          outcomes: [],
          randomTable: [],
          tableCoverage: null,
        },
        sourceRefs: [{ documentId: 'doc-1', extractionVersion: 3, locator: 'page:4' }],
      }],
    }
  }
  async getPlayPreparation(): Promise<PlayPreparationView> {
    const blueprint: CharacterCreationBlueprintView = {
      available: true,
      summary: 'CharacterCreationBlueprint: STORYBOOK 1개, RULEBOOK 런타임 세트 별도',
      rulebookDocumentCount: 0,
      storybookDocumentCount: 1,
      diagnostics: [],
    }
    return {
      scenarioPackageId: 'package-1',
      bundleId: 'bundle-1',
      bundleRevision: 1,
      status: 'READY',
      blockers: [],
      characterCreationBlueprint: blueprint,
      characterLimit: {
        maximumCharacters: 2,
        source: { documentId: 'doc-1', extractionVersion: 3, locator: 'page:1' },
        sourceQuote: '최대 2명',
      },
    }
  }
  async resolveBlueprint() {}
  async getRuntimeOptions(): Promise<RuntimeOptionsView> {
    return {
      defaultEngineId: 'ollama',
      defaultToolIds: ['search', 'move'],
      engines: [
        { id: 'ollama', label: 'Ollama', selectedByDefault: true },
        { id: 'openai', label: 'OpenAI', selectedByDefault: false },
      ],
      tools: [
        { id: 'search', label: 'Search', selectedByDefault: true },
        { id: 'move', label: 'Move', selectedByDefault: true },
        { id: 'note', label: 'Note', selectedByDefault: false },
      ],
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

  it('preserves nested blueprint paths in legacy starting abilities', () => {
    const node = (id: string, key: string, children: CharacterInputNodeView[] = []): CharacterInputNodeView => ({
      id,
      parentId: null,
      key,
      label: key,
      inputMode: 'FREE_TEXT',
      value: null,
      options: [],
      suggestions: [],
      status: 'EXTRACTED',
      allowUserAddChild: false,
      confidence: 'HIGH',
      sourceQuote: '',
      diagnostics: [],
      sourceEvidence: [],
      children,
    })

    expect(serializeBlueprintValues([
      node('str-1', 'str'),
      node('group-a', 'group', [node('str-2', 'str')]),
    ], { 'str-1': '12', 'str-2': '14' })).toEqual(['str=12', 'group.str=14'])
  })

  it('lets the owner save a bundle and compile it without rendering play prep in setup', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<ScenarioSetup api={api} playerId="owner-1" onError={() => {}} />)

    expect(await screen.findByText('main.pdf')).toBeInTheDocument()
    expect(screen.getByText('rules.pdf')).toBeInTheDocument()
    expect(screen.getByLabelText('rules.pdf 역할')).toHaveValue('RULEBOOK')
    expect(screen.getByText('handout.pdf: 추출 경고가 있어 컴파일 위험이 있습니다.')).toBeInTheDocument()
    expect(screen.getByText('failed-main.pdf: 컴파일 위험 — document parser stopped')).toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('main.pdf 역할'), 'MAIN_SCENARIO')
    await user.selectOptions(screen.getByLabelText('handout.pdf 역할'), 'HANDOUT')
    await user.click(screen.getByRole('button', { name: '시나리오 번들 저장' }))

    expect(await screen.findByText('번들 저장 완료: bundle-1 v1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '시나리오 패키지 컴파일' }))

    expect(await screen.findByText('패키지 package-1 · COMPLETE')).toBeInTheDocument()
    expect(screen.getByText('캐릭터 한도: 2명')).toBeInTheDocument()
    expect(screen.getByText('한도 근거: page:1 · 최대 2명')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '캐릭터 생성 시작' })).toBeInTheDocument()
    expect(screen.queryByText('플레이 준비')).not.toBeInTheDocument()
    expect(screen.getByLabelText('런타임 엔진')).toHaveValue('ollama')
    expect(screen.getByLabelText('search')).toBeChecked()
    expect(screen.getByLabelText('move')).toBeChecked()
    expect(screen.queryByLabelText('모험 ID')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('룰북 ID 목록')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('캐릭터 시트 ID')).not.toBeInTheDocument()
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
  }, 10000)

})
