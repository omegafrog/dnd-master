export type RulebookStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'PROCESSING'
  | 'INDEXED'
  | 'FAILED'
  | 'NEEDS_INPUT'
  | 'UPLOADED'
  | 'EXTRACTED'
  | 'PARTIAL_AWAITING_CONFIRMATION'
  | 'PARTIAL_CONFIRMED'
  | 'REJECTED'

export type RulebookView = {
  rulebookId: string
  status: RulebookStatus
}

export type DocumentType = 'RULEBOOK' | 'STORYBOOK'

export type BatchRulebookStatus = 'ACCEPTED' | 'VALIDATION_FAILED'

export type BatchRulebookView = {
  knowledgeDocumentId: string | null
  documentType: DocumentType
  originalFilename: string
  status: BatchRulebookStatus
  failureReason?: string | null
}

export type KnowledgeDocumentStatus = 'UPLOADED' | 'NEEDS_INPUT' | 'QUEUED' | 'PROCESSING' | 'FAILED' | 'EXTRACTED' | 'INDEXED' | 'PARTIAL_AWAITING_CONFIRMATION' | 'PARTIAL_CONFIRMED' | 'REJECTED'

export type KnowledgeDocumentView = {
  knowledgeDocumentId: string
  documentType: DocumentType
  originalFilename: string
  status: KnowledgeDocumentStatus
  format: 'PDF' | 'DOCX' | 'TXT' | 'IMAGE'
  extractionVersion?: number
  warnings?: string[]
  failureReason?: string | null
}

export type ScenarioBundleRole =
  | 'MAIN_SCENARIO'
  | 'MAP'
  | 'HANDOUT'
  | 'APPENDIX'
  | 'REFERENCE'
  | 'CHARACTER_SHEET'
  | 'UNDETERMINED'

export type ScenarioBundleDocumentView = {
  knowledgeDocumentId: string
  documentType: DocumentType
  originalFilename: string
  status: KnowledgeDocumentStatus
  role: ScenarioBundleRole
  extractionVersion: number
}

export type ScenarioBundleView = {
  bundleId: string
  ownerPlayerId: string
  currentRevision: number
  documents: ScenarioBundleDocumentView[]
}

export type ScenarioPackageView = {
  packageId: string
  bundleId: string
  bundleRevision: number
  inputFingerprint: string
  reportStatus: 'COMPLETE' | 'PARTIAL' | 'INVALID'
  warnings: string[]
  characterLimit: CharacterLimitView
  units: Array<{
    kind: string | null
    status: 'COMPLETE' | 'PARTIAL' | 'INVALID'
    abilityOrSkill: string | null
    dc: number | null
    diceExpression: string | null
    visibility: string | null
    sourceQuote: string
    provenance: string
    validationMessages: string[]
    runtimeCapabilities: string[]
    detail: {
      triggerCondition: string | null
      actor: string | null
      roller: string | null
      instructionVisibility: string | null
      resultVisibility: string | null
      modifiers: string[]
      advantageState: string | null
      reroll: string | null
      steps: Array<{
        id: string
        kind: string | null
        abilityOrSkill: string | null
        dc: number | null
        diceExpression: string | null
        condition: string | null
        nextStepIds: string[]
        successOutcomeIds: string[]
        failureOutcomeIds: string[]
        sourceRefs: Array<{ documentId: string; extractionVersion: number; locator: string }>
      }>
      outcomes: Array<{
        id: string
        label: string | null
        description: string | null
        sourceRefs: Array<{ documentId: string; extractionVersion: number; locator: string }>
      }>
      randomTable: Array<{
        range: string | null
        outcome: string | null
        sourceRefs: Array<{ documentId: string; extractionVersion: number; locator: string }>
      }>
      tableCoverage: string | null
    }
    sourceRefs: Array<{ documentId: string; extractionVersion: number; locator: string }>
  }>
}

export type CharacterLimitView = {
  maximumCharacters: number
  source: { documentId: string; extractionVersion: number; locator: string } | null
  sourceQuote: string
}

export type PlayPreparationStatus = 'READY' | 'BLOCKED'

export type CharacterCreationBlueprintView = {
  available: boolean
  summary: string | null
  rulebookDocumentCount: number
  storybookDocumentCount: number
  diagnostics: string[]
  revision?: number
  status?: 'DRAFT' | 'NEEDS_REVIEW' | 'READY' | 'PUBLISHED'
  fields?: Array<{
    key: string
    options: string[]
    required: boolean
    sourceType: string
    inputStatus: string
    diagnostics: string[]
  }>
}

export type PlayPreparationView = {
  scenarioPackageId: string
  bundleId: string
  bundleRevision: number
  status: PlayPreparationStatus
  blockers: string[]
  characterCreationBlueprint: CharacterCreationBlueprintView
  characterLimit: CharacterLimitView
}

export type CreatedCharacterSheetView = {
  characterSheetId: string
  adventureId: string
  edition: string
  characterName: string
  level: number
  inspiration: boolean
  version: number
}

export type CharacterCreationDraft = {
  edition: 'DND_5E_2014' | 'DND_5E_2024'
  characterName: string
  level: number
  inspiration: boolean
  blueprintRevision?: number
  blueprintValues?: Record<string, string>
}

export type RuntimeOptionView = {
  id: string
  label: string
  selectedByDefault: boolean
}

export type RuntimeOptionsView = {
  defaultEngineId: string
  defaultToolIds: string[]
  engines: RuntimeOptionView[]
  tools: RuntimeOptionView[]
}

export type RuntimeBindingStatus = 'PLAYABLE' | 'PLAYABLE_WITH_LIMITS' | 'BLOCKED'

export type RuntimeSourceContextCandidateView = {
  knowledgeDocumentId: string
  extractionVersion: number
  locator: string
  excerpt: string
  score: number
  reason: string
}

export type ActiveSourceContextView = {
  knowledgeDocumentId: string
  extractionVersion: number
  locator: string
  excerpt: string
}

export type PlayabilityReportView = {
  status: RuntimeBindingStatus
  warnings: string[]
  blockers: string[]
  limits: string[]
  candidates: RuntimeSourceContextCandidateView[]
}

export type RuntimeBindingView = {
  adventureId: string
  bindingVersion: number
  scenarioPackageId: string
  scenarioPackageRevision: number
  rulebookIds: string[]
  party: RuntimePartyMemberView[]
  engineId: string
  toolIds: string[]
  playabilityReport: PlayabilityReportView
  activeSourceContext: ActiveSourceContextView | null
}

export type RuntimePartyMemberView = {
  characterSheetId: string
  controlMode: 'DIRECT' | 'AGENT'
  nameMutableAfterStart: boolean
  raceMutableAfterStart: boolean
  characterClassMutableAfterStart: boolean
  backgroundMutableAfterStart: boolean
  startingAbilitiesMutableAfterStart: boolean
  levelMutableAfterStart: boolean
}

export type LegacyScenarioMigrationView = {
  scenarioId: string
  bundleId: string | null
  packageId: string | null
  knowledgeDocumentId: string | null
  requiresReupload: boolean
  reupload: boolean
  sourceFilename: string
  message: string
}

export type ScenarioCompilationStatus = 'REQUESTED' | 'RUNNING' | 'WAITING_RETRY' | 'PUBLISHED' | 'FAILED'

export type ScenarioCompilationView = {
  compilationId: string
  bundleId: string
  bundleRevision: number
  status: ScenarioCompilationStatus
  attempt: number
  packageId?: string | null
  failureReason?: string | null
}

export type ScenarioBundleDraft = {
  knowledgeDocumentId: string
  role: ScenarioBundleRole
}

export type RuntimeBindingDraft = {
  playerId: string
  scenarioPackageId: string
  rulebookIds: string[]
  engineId: string
  toolIds: string[]
}

export type SourceSpanView = {
  kind: string
  path: string[]
  pageNumber: number | null
  bounds: {
    left: number
    top: number
    right: number
    bottom: number
  } | null
  lineNumber: number
  startInclusive: number
  endExclusive: number
  text: string
  locator: string
  sourceMethod?: string | null
  confidence?: number | null
}

export type SourceAssetView = {
  kind: string
  locator: string
  contentType?: string | null
  pageNumber?: number | null
}

export type SourcePreviewView = {
  rulebookId: string
  knowledgeDocumentId: string
  documentType: DocumentType
  originalFilename: string
  format: 'PDF' | 'DOCX' | 'TXT' | 'IMAGE'
  status: string
  content: string
  extractionVersion: number
  warnings: string[]
  spans: SourceSpanView[]
  assets: SourceAssetView[]
}

export type StorySourceEvidenceView = {
  knowledgeDocumentId: string
  extractionVersion: number
  locator: string
  excerpt: string
  score: number
}

export type RulebookUploadDraft = {
  file: File
  documentType: DocumentType
  idempotencyKey: string
}

export interface SetupApi {
  uploadRulebooks(documents: RulebookUploadDraft[], ownerId: string): Promise<BatchRulebookView[]>
  getRulebookStatus(rulebookId: string): Promise<RulebookView>
  retryKnowledgeDocument(knowledgeDocumentId: string): Promise<RulebookView>
  getSourcePreview(knowledgeDocumentId: string): Promise<SourcePreviewView>
  uploadScenario(file: File): Promise<{
    id: string
    name: string
    deprecated?: boolean
    deprecationMessage?: string
    legacyScenarioId?: string
    sunset?: string | null
  }>
  migrateLegacyScenario(scenarioId: string): Promise<LegacyScenarioMigrationView>
  reuploadLegacyScenario(scenarioId: string, file: File): Promise<LegacyScenarioMigrationView>
  createScenarioBundle(ownerId: string, documents: ScenarioBundleDraft[]): Promise<ScenarioBundleView>
  reviseScenarioBundle(bundleId: string, ownerId: string, documents: ScenarioBundleDraft[]): Promise<ScenarioBundleView>
  getScenarioBundle(bundleId: string): Promise<ScenarioBundleView>
  compileScenarioBundle?(bundleId: string, ownerId: string): Promise<ScenarioPackageView>
  startScenarioCompilation?(bundleId: string, ownerId: string, inputFingerprint: string): Promise<ScenarioCompilationView>
  getScenarioCompilation?(compilationId: string): Promise<ScenarioCompilationView>
  getScenarioPackage?(packageId: string): Promise<ScenarioPackageView>
  getPlayPreparation?(scenarioPackageId: string): Promise<PlayPreparationView>
  getRuntimeOptions?(): Promise<RuntimeOptionsView>
  createCharacterSheet?(draft: CharacterCreationDraft): Promise<CreatedCharacterSheetView>
  bindRuntimeBinding?(adventureId: string, ownerId: string, draft: RuntimeBindingDraft): Promise<RuntimeBindingView>
  getRuntimeBinding?(adventureId: string, ownerId: string): Promise<RuntimeBindingView>
  switchRuntimePackage?(adventureId: string, ownerId: string, bindingVersion: number, scenarioPackageId: string): Promise<RuntimeBindingView>
  selectRuntimeSourceContext?(adventureId: string, ownerId: string, bindingVersion: number, locator: string): Promise<RuntimeBindingView>
  saveRuleSet(rulebookIds: string[]): Promise<void>
  listKnowledgeDocuments(ownerId: string): Promise<KnowledgeDocumentView[]>
  searchStorySources?(ownerId: string, documents: StorySourceScopeView[], situation: string, activeLocators?: string[]): Promise<StorySourceEvidenceView[]>
}

export type StorySourceScopeView = {
  documentId: string
  extractionVersion: number
}

async function request<T>(path: string, init: RequestInit, badRequestMessage = '지원하지 않거나 손상된 파일입니다.'): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 400) throw new Error(badRequestMessage)
  if (response.status === 409) throw new Error('재처리할 수 없는 상태입니다.')
  if (!response.ok) throw new Error('요청을 처리하지 못했습니다.')
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export class HttpSetupApi implements SetupApi {
  private readonly getToken: () => string

  constructor(getToken: () => string) {
    this.getToken = getToken
  }

  private authHeaders(): Record<string, string> {
    return { Authorization: `Bearer ${this.getToken()}` }
  }

  uploadRulebooks(documents: RulebookUploadDraft[], ownerId: string) {
    const body = new FormData()
    body.append('documents', new Blob([JSON.stringify(documents.map(document => ({
      idempotencyKey: document.idempotencyKey,
      documentType: document.documentType,
      originalFilename: document.file.name,
    })))], { type: 'application/json' }))
    documents.forEach(document => body.append('files', document.file, document.file.name))
    return request<{ documents: BatchRulebookView[] }>(`/api/v1/rulebooks?ownerPlayerId=${ownerId}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.getToken()}` },
      body,
    }).then(response => response.documents)
  }

  getRulebookStatus(rulebookId: string) {
    return request<RulebookView>(`/api/v1/rulebooks/${rulebookId}`, {
      headers: this.authHeaders(),
    })
  }

  retryKnowledgeDocument(knowledgeDocumentId: string) {
    return request<RulebookView>(`/api/v1/rulebooks/${knowledgeDocumentId}/retry`, {
      method: 'POST',
      headers: this.authHeaders(),
    })
  }

  getSourcePreview(knowledgeDocumentId: string) {
    return request<SourcePreviewView>(`/api/v1/rulebooks/${knowledgeDocumentId}/source-preview`, {
      headers: this.authHeaders(),
    })
  }

  uploadScenario(file: File) {
    const body = new FormData()
    body.append('file', file)
    return fetch('/api/v1/adventures/scenarios', {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.getToken()}` },
      body,
    }).then(async response => {
      if (response.status === 400) throw new Error('지원하지 않거나 손상된 파일입니다.')
      if (!response.ok) throw new Error('요청을 처리하지 못했습니다.')
      const legacyScenarioId = response.headers.get('X-Legacy-Scenario-Id') ?? undefined
      if (!legacyScenarioId) throw new Error('레거시 시나리오 식별자가 없습니다.')
      const deprecationWarning = response.headers.get('Warning') ?? undefined
      return {
        id: legacyScenarioId,
        name: file.name,
        deprecated: response.headers.get('Deprecation') === 'true',
        deprecationMessage: deprecationWarning?.replace(/^299 [^ ]+\s+"/, '').replace(/"$/, '') ?? undefined,
        legacyScenarioId,
        sunset: response.headers.get('Sunset'),
      }
    })
  }

  migrateLegacyScenario(scenarioId: string) {
    return request<LegacyScenarioMigrationView>(`/api/v1/adventures/legacy-scenarios/${scenarioId}/migrate`, {
      method: 'POST',
      headers: this.authHeaders(),
    }, '레거시 시나리오를 마이그레이션하지 못했습니다.')
  }

  reuploadLegacyScenario(scenarioId: string, file: File) {
    const body = new FormData()
    body.append('file', file)
    return request<LegacyScenarioMigrationView>(`/api/v1/adventures/legacy-scenarios/${scenarioId}/reupload`, {
      method: 'POST',
      headers: this.authHeaders(),
      body,
    }, '레거시 시나리오를 재업로드하지 못했습니다.')
  }

  createScenarioBundle(ownerId: string, documents: ScenarioBundleDraft[]) {
    return request<ScenarioBundleView>('/api/v1/adventures/scenario-bundles', {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, documents }),
    }, '시나리오 번들을 저장하지 못했습니다.')
  }

  reviseScenarioBundle(bundleId: string, ownerId: string, documents: ScenarioBundleDraft[]) {
    return request<ScenarioBundleView>(`/api/v1/adventures/scenario-bundles/${bundleId}/revisions`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, documents }),
    }, '시나리오 번들을 저장하지 못했습니다.')
  }

  getScenarioBundle(bundleId: string) {
    return request<ScenarioBundleView>(`/api/v1/adventures/scenario-bundles/${bundleId}`, {
      headers: this.authHeaders(),
    })
  }

  compileScenarioBundle(bundleId: string, ownerId: string) {
    return request<ScenarioPackageView>(`/api/v1/adventures/scenario-bundles/${bundleId}/compilations`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, candidates: [] }),
    }, '시나리오 패키지 컴파일에 실패했습니다.')
  }

  startScenarioCompilation(bundleId: string, ownerId: string, inputFingerprint: string) {
    return request<ScenarioCompilationView>(`/api/v1/adventures/scenario-bundles/${bundleId}/compilation-jobs`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, inputFingerprint }),
    }, '시나리오 패키지 컴파일을 시작하지 못했습니다.')
  }

  getScenarioCompilation(compilationId: string) {
    return request<ScenarioCompilationView>(`/api/v1/adventures/compilations/${compilationId}`, {
      headers: this.authHeaders(),
    }, '시나리오 패키지 컴파일 상태를 불러오지 못했습니다.')
  }

  getScenarioPackage(packageId: string) {
    return request<ScenarioPackageView>(`/api/v1/adventures/scenario-packages/${packageId}`, {
      headers: this.authHeaders(),
    }, '시나리오 패키지를 불러오지 못했습니다.')
  }

  getPlayPreparation(scenarioPackageId: string) {
    return request<PlayPreparationView>(`/api/v1/scenario-packages/${scenarioPackageId}/play-preparation`, {
      headers: this.authHeaders(),
    }, '플레이 준비 상태를 불러오지 못했습니다.')
  }

  getRuntimeOptions() {
    return request<RuntimeOptionsView>('/api/v1/runtime-options', {
      headers: this.authHeaders(),
    }, '런타임 옵션을 불러오지 못했습니다.')
  }

  createCharacterSheet(draft: CharacterCreationDraft) {
    return request<CreatedCharacterSheetView>('/internal/v1/character-sheets', {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(draft),
    }, '캐릭터 시트를 생성하지 못했습니다.')
  }

  bindRuntimeBinding(adventureId: string, _ownerId: string, draft: RuntimeBindingDraft) {
    void _ownerId
    return request<RuntimeBindingView>(`/api/v1/adventures/${adventureId}/runtime-bindings`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify(draft),
    }, '런타임 바인딩을 저장하지 못했습니다.')
  }

  getRuntimeBinding(adventureId: string, _ownerId: string) {
    void _ownerId
    return request<RuntimeBindingView>(`/api/v1/adventures/${adventureId}/runtime-bindings`, {
      headers: this.authHeaders(),
    }, '런타임 바인딩을 불러오지 못했습니다.')
  }

  switchRuntimePackage(adventureId: string, ownerId: string, bindingVersion: number, scenarioPackageId: string) {
    return request<RuntimeBindingView>(`/api/v1/adventures/${adventureId}/runtime-bindings/${bindingVersion}/package-switch`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, scenarioPackageId }),
    }, '런타임 바인딩 패키지를 전환하지 못했습니다.')
  }

  selectRuntimeSourceContext(adventureId: string, ownerId: string, bindingVersion: number, locator: string) {
    return request<RuntimeBindingView>(`/api/v1/adventures/${adventureId}/runtime-bindings/${bindingVersion}/source-context`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, locator }),
    }, '시작 원문 구간을 선택하지 못했습니다.')
  }

  saveRuleSet(rulebookIds: string[]) {
    return request<void>('/api/v1/rulebooks/rule-set', {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ knowledgeDocumentIds: rulebookIds }),
    })
  }

  listKnowledgeDocuments(ownerId: string) {
    return request<{ ownerId: string; rulebooks: KnowledgeDocumentView[] }>(`/internal/v1/rulebooks?ownerId=${ownerId}`, {
      headers: this.authHeaders(),
    }).then(response => response.rulebooks)
  }

  searchStorySources(ownerId: string, documents: StorySourceScopeView[], situation: string, activeLocators: string[] = []) {
    return request<{ ownerId: string; evidence: StorySourceEvidenceView[] }>('/internal/v1/story-sources/search', {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ ownerId, documents, activeLocators, situation }),
    }, '시나리오 원문 검색에 실패했습니다.').then(response => response.evidence)
  }
}
