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

export type DocumentPreparationStage = 'QUEUED' | 'EXTRACTING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXING' | 'READY' | 'FAILED'

export type DocumentPreparationProgress = {
  stage: DocumentPreparationStage
  percent: number
  completedUnits?: number
  totalUnits?: number
  error?: string | null
}

export type KnowledgeDocumentView = {
  knowledgeDocumentId: string
  documentType: DocumentType
  originalFilename: string
  status: KnowledgeDocumentStatus
  format: 'PDF' | 'DOCX' | 'TXT' | 'IMAGE'
  extractionVersion?: number
  warnings?: string[]
  failureReason?: string | null
  progress?: DocumentPreparationProgress
}

export type ScenarioBundleRole =
  | 'RULEBOOK'
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
  ownerPlayerId?: string
  name?: string
  rulebookEdition?: 'DND_5E_2014' | 'DND_5E_2024' | null
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
  status?: 'DRAFT' | 'NEEDS_REVIEW' | 'READY' | 'PUBLISHED' | 'UNAVAILABLE'
  edition?: 'DND_5E_2014' | 'DND_5E_2024'
  fields?: Array<{
    key: string
    options: string[]
    required: boolean
    sourceType: string
    inputStatus: string
    inputMode?: 'FREE_TEXT' | 'SINGLE_SELECT' | 'MULTI_SELECT' | 'FIXED_VALUE'
    value?: string | null
    suggestions?: string[]
    diagnostics: string[]
    constraints?: string[]
    evidence?: Array<{ knowledgeDocumentId: string; extractionVersion: number; locator: string }>
    sourceQuote?: string
  }>
  roots?: CharacterInputNodeView[]
  characterSheetTree?: CharacterInputNodeView[]
  baseSchema?: RulebookBaseSchemaView
  storybookProposals?: StorybookProposalView[]
  storybookExtractionState?: StorybookExtractionState
}

export type RulebookBaseSchemaView = {
  edition: string
  fields: NonNullable<CharacterCreationBlueprintView['fields']>
}

export type StorybookExtractionState = 'NO_PROPOSALS' | 'PROPOSALS_AVAILABLE' | 'EXTRACTION_FAILED' | 'INSUFFICIENT_EVIDENCE' | 'EXTRACTION_PARTIAL_AWAITING_CONFIRMATION' | 'EXTRACTION_PARTIAL_CONFIRMED' | 'EXTRACTION_MIXED'

export type StorybookProposalView = {
  proposalId: string
  key: string
  label: string
  description: string
  sourceDocument: { knowledgeDocumentId: string; originalFilename: string; extractionVersion: number } | null
  sourceQuote: string
  evidence: Array<{ locator: string; excerpt: string }>
  decisionState: 'UNDECIDED' | 'APPLIED' | 'EXCLUDED' | 'NEEDS_EVIDENCE'
  readinessState: 'READY' | 'INSUFFICIENT_EVIDENCE'
}

export type CharacterInputNodeView = {
  id: string
  parentId: string | null
  key: string
  label: string
  inputMode: 'FREE_TEXT' | 'SINGLE_SELECT' | 'MULTI_SELECT' | 'FIXED_VALUE'
  value: string | null
  options: string[]
  optionDetails?: Array<{ value: string; label: string; description: string; sourceQuote: string }>
  suggestions: string[]
  status: 'EXTRACTED' | 'PARTIALLY_EXTRACTED' | 'CONFLICT_REVIEW' | 'USER_ADDED' | 'REVIEWED'
  allowUserAddChild: boolean
  confidence: string
  sourceQuote: string
  diagnostics: string[]
  sourceEvidence: Array<{ knowledgeDocumentId: string; extractionVersion: number; locator: string }>
  children: CharacterInputNodeView[]
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
  sessionId?: string
  ownerPlayerId?: string
  edition: 'DND_5E_2014' | 'DND_5E_2024'
  characterName: string
  level: number
  inspiration: boolean
  race?: string
  characterClass?: string
  background?: string
  startingAbilities?: string
  derivedStatistics?: string
  characterBuild?: string
  characterState?: string
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

export type AgentEndpointPreflightView = {
  configured: boolean
  connected: boolean
  state: 'LOGIN_REQUIRED' | 'CONNECTED' | 'EXPIRED' | 'FAILED' | 'NOT_CONFIGURED'
  provider?: 'OLLAMA' | 'OPENAI_COMPATIBLE' | 'CODEX_CLI'
  detail?: string | null
}

export type ScenarioBundleDraft = {
  knowledgeDocumentId: string
  role: ScenarioBundleRole
}

export type ScenarioBundleContract = {
  name: string
  rulebookEdition: 'DND_5E_2014' | 'DND_5E_2024'
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
  deleteKnowledgeDocument?(knowledgeDocumentId: string): Promise<void>
  getSourcePreview(knowledgeDocumentId: string): Promise<SourcePreviewView>
  uploadScenario?(file: File): Promise<{
    id: string
    name: string
    deprecated?: boolean
    deprecationMessage?: string
    legacyScenarioId?: string
    sunset?: string | null
  }>
  migrateLegacyScenario?(scenarioId: string): Promise<LegacyScenarioMigrationView>
  reuploadLegacyScenario?(scenarioId: string, file: File): Promise<LegacyScenarioMigrationView>
  createScenarioBundle(ownerId: string, documents: ScenarioBundleDraft[], contract?: ScenarioBundleContract): Promise<ScenarioBundleView>
  reviseScenarioBundle(bundleId: string, ownerId: string, documents: ScenarioBundleDraft[], contract?: ScenarioBundleContract): Promise<ScenarioBundleView>
  getScenarioBundle(bundleId: string): Promise<ScenarioBundleView>
  listScenarioPackages?(bundleId: string): Promise<ScenarioPackageView[]>
  deleteScenarioBundle?(bundleId: string): Promise<void>
  listScenarioBundles?(): Promise<ScenarioBundleView[]>
  startScenarioCompilation?(bundleId: string, ownerId: string, inputFingerprint: string): Promise<ScenarioCompilationView>
  getScenarioCompilation?(compilationId: string): Promise<ScenarioCompilationView>
  preflightAgentEndpoint?(): Promise<AgentEndpointPreflightView>
  getScenarioPackage?(packageId: string): Promise<ScenarioPackageView>
  getPlayPreparation?(scenarioPackageId: string): Promise<PlayPreparationView>
  generateBlueprintDraft?(scenarioPackageId: string, catalogRulebookId?: string, catalogExtractionVersion?: number): Promise<CharacterCreationBlueprintView>
  resolveBlueprint?(scenarioPackageId: string, fieldKey: string, value: string, expectedRevision?: number): Promise<unknown>
  addBlueprintChild?(scenarioPackageId: string, expectedRevision: number, parentId: string, key: string, label: string): Promise<unknown>
  addBlueprintOption?(scenarioPackageId: string, expectedRevision: number, fieldKey: string, option: string): Promise<unknown>
  publishBlueprint?(scenarioPackageId: string): Promise<unknown>
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
  if (!response.ok) {
    const detail = await response.text()
    throw new Error(detail || `요청을 처리하지 못했습니다. (HTTP ${response.status})`)
  }
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
    })))], { type: 'application/json' }), 'documents.json')
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

  deleteKnowledgeDocument(knowledgeDocumentId: string) {
    return request<void>(`/api/v1/rulebooks/${knowledgeDocumentId}`, {
      method: 'DELETE',
      headers: this.authHeaders(),
    }, '자료를 삭제하지 못했습니다.')
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

  createScenarioBundle(ownerId: string, documents: ScenarioBundleDraft[], contract: ScenarioBundleContract = { name: 'Unnamed adventure', rulebookEdition: 'DND_5E_2014' }) {
    return request<ScenarioBundleView>('/api/v1/adventures/scenario-bundles', {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, ...contract, documents }),
    }, '모험 자료를 저장하지 못했습니다.')
  }

  reviseScenarioBundle(bundleId: string, ownerId: string, documents: ScenarioBundleDraft[], contract: ScenarioBundleContract = { name: 'Unnamed adventure', rulebookEdition: 'DND_5E_2014' }) {
    return request<ScenarioBundleView>(`/api/v1/adventures/scenario-bundles/${bundleId}/revisions`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, ...contract, documents }),
    }, '모험 자료를 저장하지 못했습니다.')
  }

  getScenarioBundle(bundleId: string) {
    return request<ScenarioBundleView>(`/api/v1/adventures/scenario-bundles/${bundleId}`, {
      headers: this.authHeaders(),
    })
  }

  deleteScenarioBundle(bundleId: string) {
    return request<void>(`/api/v1/adventures/scenario-bundles/${bundleId}`, {
      method: 'DELETE',
      headers: this.authHeaders(),
    }, '모험 자료를 삭제하지 못했습니다.')
  }

  listScenarioBundles() {
    return request<ScenarioBundleView[]>('/api/v1/adventures/scenario-bundles', {
      headers: this.authHeaders(),
    })
  }

  startScenarioCompilation(bundleId: string, ownerId: string, inputFingerprint: string) {
    return request<ScenarioCompilationView>(`/api/v1/adventures/scenario-bundles/${bundleId}/compilation-jobs`, {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ playerId: ownerId, inputFingerprint }),
    }, '게임 준비를 시작하지 못했습니다.')
  }

  getScenarioCompilation(compilationId: string) {
    return request<ScenarioCompilationView>(`/api/v1/adventures/compilations/${compilationId}`, {
      headers: this.authHeaders(),
    }, '게임 준비 상태를 불러오지 못했습니다.')
  }

  async preflightAgentEndpoint(): Promise<AgentEndpointPreflightView> {
    const endpoints = await request<Array<{ id: string; provider: AgentEndpointPreflightView['provider']; active: boolean }>>(
      '/api/v1/profile/agent-endpoints', { headers: this.authHeaders() }, 'AI 엔드포인트 상태를 확인하지 못했습니다.')
    const active = endpoints.find(endpoint => endpoint.active)
    if (!active) return { configured: false, connected: false, state: 'NOT_CONFIGURED', detail: 'AI 엔드포인트를 먼저 설정하세요.' }
    const health = await request<{ healthy: boolean; detail?: string | null }>(
      `/api/v1/profile/agent-endpoints/${active.id}/health`, { method: 'POST', headers: this.authHeaders() }, 'AI 엔드포인트 상태를 확인하지 못했습니다.')
    if (health.healthy) return { configured: true, connected: true, state: 'CONNECTED', provider: active.provider, detail: null }
    return { configured: true, connected: false, state: active.provider === 'CODEX_CLI' ? 'LOGIN_REQUIRED' : 'FAILED', provider: active.provider, detail: health.detail ?? 'AI 엔드포인트에 연결할 수 없습니다.' }
  }

  getScenarioPackage(packageId: string) {
    return request<ScenarioPackageView>(`/api/v1/adventures/scenario-packages/${packageId}`, {
      headers: this.authHeaders(),
    }, '모험 준비 결과를 불러오지 못했습니다.')
  }

  listScenarioPackages(bundleId: string) {
    return request<ScenarioPackageView[]>(`/api/v1/adventures/scenario-bundles/${bundleId}/packages`, {
      headers: this.authHeaders(),
    }, '모험 준비 결과를 불러오지 못했습니다.')
  }

  getPlayPreparation(scenarioPackageId: string) {
    return request<PlayPreparationView>(`/api/v1/scenario-packages/${scenarioPackageId}/play-preparation`, {
      headers: this.authHeaders(),
    }, '플레이 준비 상태를 불러오지 못했습니다.')
  }

  generateBlueprintDraft(scenarioPackageId: string, catalogRulebookId?: string, catalogExtractionVersion?: number) {
    const params = new URLSearchParams()
    if (catalogRulebookId) params.set('catalogRulebookId', catalogRulebookId)
    if (catalogExtractionVersion) params.set('catalogExtractionVersion', String(catalogExtractionVersion))
    const suffix = params.size > 0 ? `?${params}` : ''
    return request<CharacterCreationBlueprintView>(`/api/v1/scenario-packages/${scenarioPackageId}/character-blueprint/draft${suffix}`, {
      method: 'POST', headers: this.authHeaders(),
    }, '인덱스에서 캐릭터 시트 초안을 생성하지 못했습니다.')
  }

  resolveBlueprint(scenarioPackageId: string, fieldKey: string, value: string, expectedRevision = 0) {
    return request<unknown>(`/api/v1/scenario-packages/${scenarioPackageId}/character-blueprint/resolve`, {
      method: 'POST', headers: { ...this.authHeaders(), 'Content-Type': 'application/json' }, body: JSON.stringify({ expectedRevision, fieldKey, value }),
    }, 'Blueprint 검토를 저장하지 못했습니다.')
  }

  addBlueprintChild(scenarioPackageId: string, expectedRevision: number, parentId: string, key: string, label: string) {
    return request<unknown>(`/api/v1/scenario-packages/${scenarioPackageId}/character-blueprint/children`, {
      method: 'POST', headers: { ...this.authHeaders(), 'Content-Type': 'application/json' }, body: JSON.stringify({ expectedRevision, parentId, key, label }),
    }, 'Blueprint 하위 필드를 추가하지 못했습니다.')
  }

  addBlueprintOption(scenarioPackageId: string, expectedRevision: number, fieldKey: string, option: string) {
    return request<unknown>(`/api/v1/scenario-packages/${scenarioPackageId}/character-blueprint/options`, {
      method: 'POST', headers: { ...this.authHeaders(), 'Content-Type': 'application/json' }, body: JSON.stringify({ expectedRevision, fieldKey, option }),
    }, 'Blueprint 선택지를 추가하지 못했습니다.')
  }

  publishBlueprint(scenarioPackageId: string) {
    return request<unknown>(`/api/v1/scenario-packages/${scenarioPackageId}/character-blueprint/publish`, {
      method: 'POST', headers: this.authHeaders(),
    }, 'Blueprint 게시에 실패했습니다.')
  }

  getRuntimeOptions() {
    return request<RuntimeOptionsView>('/api/v1/runtime-options', {
      headers: this.authHeaders(),
    }, '런타임 옵션을 불러오지 못했습니다.')
  }

  createCharacterSheet(draft: CharacterCreationDraft) {
    const path = draft.sessionId
      ? `/internal/v1/adventure-sessions/${draft.sessionId}/character-sheets`
      : '/internal/v1/character-sheets'
    return request<CreatedCharacterSheetView>(path, {
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
