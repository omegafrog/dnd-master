export type SessionControlMode = 'DIRECT' | 'AGENT'
export type AdventureSessionStatus = 'DRAFT' | 'STARTING' | 'STARTED' | 'COMPLETED' | 'DELETED'

export type SessionPartyMember = {
  characterSheetId: string
  controlMode: SessionControlMode
  nameMutableAfterStart: boolean
  raceMutableAfterStart: boolean
  characterClassMutableAfterStart: boolean
  backgroundMutableAfterStart: boolean
  startingAbilitiesMutableAfterStart: boolean
  levelMutableAfterStart: boolean
}

export type CharacterSheetSummary = {
  characterSheetId: string
  characterName: string
  level: number
  race: string
  characterClass: string
  background: string
}

export type AiCompanionCandidate = {
  candidateId: string
  name: string
  race: string
  characterClass: string
  sheetSummary: string
}

export type AdventureSessionView = {
  sessionId: string
  scenarioPackageId?: string
  scenarioPackageRevision?: number
  blueprintId?: string
  blueprintRevision?: number
  characterEdition?: 'DND_5E_2014' | 'DND_5E_2024'
  characterLimit: number
  version: number
  status: AdventureSessionStatus
  adventureId: string | null
  runtimeConfiguration: {
    scenarioId: string
    ruleSetId: string
    rulebookIds: string[]
    engineId: string
    toolIds: string[]
    initialScene: string
  } | null
  party: SessionPartyMember[]
}

export type CreateAdventureSessionRequest = {
  scenarioPackageId: string
  blueprintId: string
  blueprintRevision: number
  runtimeConfiguration?: AdventureSessionView['runtimeConfiguration']
  partySize?: number
}

export type AdventureStoryPlanView = {
  status: 'GENERATING' | 'READY' | 'BLOCKED' | 'FAILED'
  currentStage: number
  planRevision: number
  endingCount: number
  adventureLength: 'SHORT' | 'STANDARD' | 'LONG'
  stages: Array<{
    position: number
    title: string
    stageType: string
    location: string
    goal: string
    mapDefinitionId?: string | null
  }>
  failureReason: string | null
}

export type AdventureStoryPlanGenerationJobView = {
  jobId: string
  sessionId: string
  status: 'QUEUED' | 'RUNNING' | 'COMPLETE' | 'FAILED'
  progress: number
  stage: string
  message: string | null
  updatedAt: string
}

export type StageMapActivation = { stagePosition: number; mapDefinitionId: string; assetId: string; assetLocator: string; combatMapId: string }
export type TacticalScenePreparationView = {
  jobId: string | null
  sessionId: string
  stagePosition: number
  stageName: string
  status: 'NOT_REQUIRED' | 'REQUIRED_PENDING' | 'PREPARING' | 'READY' | 'FAILED_RETRYABLE'
  progress: number
  attempts: number
  mapRequired: boolean
  message: string
  failureReason: string | null
  updatedAt: string
}

export type GmProviderView = {
  sessionId: string
  provider: string
  model: string
  reasoning: string
  version: number
  turnInProgress: boolean
}

export class AdventureSessionApi {
  private readonly startKeys = new Map<string, string>()
  constructor(private readonly token: string) {}

  private headers(extra: HeadersInit = {}): HeadersInit { return { Authorization: `Bearer ${this.token}`, ...extra } }
  private async request<T>(url: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, { ...init, headers: this.headers(init.headers) })
    if (!response.ok) throw new Error(await response.text() || `HTTP ${response.status}`)
    return response.json() as Promise<T>
  }
  read(sessionId: string) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}`) }
  readGmProvider(sessionId: string) { return this.request<GmProviderView>(`/api/v1/adventure-sessions/${sessionId}/gm-provider`) }
  switchGmProvider(sessionId: string, version: number, selection: Pick<GmProviderView, 'provider' | 'model' | 'reasoning'>) {
    return this.request<GmProviderView>(`/api/v1/adventure-sessions/${sessionId}/gm-provider`, { method: 'PUT', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version) }, body: JSON.stringify(selection) })
  }
  listOwnedCharacters(ownerPlayerId: string) { return this.request<CharacterSheetSummary[]>(`/internal/v1/character-sheets?ownerPlayerId=${encodeURIComponent(ownerPlayerId)}`) }
  copyOwnedCharacter(sessionId: string, characterSheetId: string, ownerPlayerId: string) {
    return this.request<{ characterSheetId: string }>(`/internal/v1/adventure-sessions/${sessionId}/character-sheets/${characterSheetId}/copy`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ownerPlayerId }) })
  }
  listByScenarioPackage(scenarioPackageId: string) { return this.request<AdventureSessionView[]>(`/api/v1/adventure-sessions?scenarioPackageId=${encodeURIComponent(scenarioPackageId)}`) }
  create(request: CreateAdventureSessionRequest) {
    return this.request<AdventureSessionView>('/api/v1/adventure-sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    })
  }
  addMember(sessionId: string, version: number, member: SessionPartyMember) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/party`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version) }, body: JSON.stringify(member) })
  }
  generateAiCandidate(sessionId: string) {
    return this.request<AiCompanionCandidate>(`/api/v1/adventure-sessions/${sessionId}/party/ai-candidates`, { method: 'POST' })
  }
  adoptAiCandidate(sessionId: string, version: number, candidate: AiCompanionCandidate, controlMode: SessionControlMode) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/party/ai-candidates/adopt`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version) }, body: JSON.stringify({ ...candidate, controlMode }) })
  }
  replaceMember(sessionId: string, version: number, characterSheetId: string, member: SessionPartyMember) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/party/${characterSheetId}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version) }, body: JSON.stringify(member) })
  }
  removeMember(sessionId: string, version: number, characterSheetId: string) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/party/${characterSheetId}`, { method: 'DELETE', headers: { 'If-Match-Version': String(version) } })
  }
  start(sessionId: string, version: number, adventureId: string) {
    const requestId = this.startKeys.get(sessionId) ?? crypto.randomUUID()
    this.startKeys.set(sessionId, requestId)
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/start`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version), 'Idempotency-Key': requestId }, body: JSON.stringify({ adventureId }) })
  }
  saveAppliedRuleSet(adventureId: string, ruleSetId: string, edition: string, rulebookIds: string[]) {
    return this.request<{ ruleSetId: string }>(`/api/v1/adventures/${adventureId}/applied-rule-set`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ruleSetId, edition, rulebookIds }) })
  }
  complete(sessionId: string, version: number) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/complete`, { method: 'POST', headers: { 'If-Match-Version': String(version) } }) }
  recoverStart(sessionId: string, version: number) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/start/recover`, { method: 'POST', headers: { 'If-Match-Version': String(version) } }) }
  delete(sessionId: string, version: number) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}`, { method: 'DELETE', headers: { 'If-Match-Version': String(version) } }) }
  readStoryPlan(sessionId: string) { return this.request<AdventureStoryPlanView>(`/api/v1/adventure-sessions/${sessionId}/story-plan`) }
  startStoryPlanGeneration(sessionId: string, configuration: { endingCount: number; adventureLength: AdventureStoryPlanView['adventureLength'] }) { return this.request<AdventureStoryPlanGenerationJobView>(`/api/v1/adventure-sessions/${sessionId}/story-plan`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(configuration) }) }
  retryStoryPlan(sessionId: string, configuration: { endingCount: number; adventureLength: AdventureStoryPlanView['adventureLength'] }) { return this.request<AdventureStoryPlanGenerationJobView>(`/api/v1/adventure-sessions/${sessionId}/story-plan/retry`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(configuration) }) }
  readStoryPlanGeneration(jobId: string, sessionId: string) { return this.request<AdventureStoryPlanGenerationJobView>(`/api/v1/adventure-sessions/${sessionId}/story-plan/generation/${jobId}`) }
  activateStageMap(sessionId: string, position: number) { return this.request<StageMapActivation>(`/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${position}/activate-map`, { method: 'POST' }) }
  prepareTacticalScene(sessionId: string, position: number) { return this.request<TacticalScenePreparationView>(`/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${position}/tactical-scene/prepare`, { method: 'POST' }) }
  readTacticalScenePreparation(sessionId: string, position: number) { return this.request<TacticalScenePreparationView>(`/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${position}/tactical-scene/prepare`) }
  retryTacticalScene(sessionId: string, position: number) { return this.request<TacticalScenePreparationView>(`/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${position}/tactical-scene/retry`, { method: 'POST' }) }
}
