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

export type AdventureSessionView = {
  sessionId: string
  scenarioPackageId?: string
  scenarioPackageRevision?: number
  blueprintId?: string
  blueprintRevision?: number
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

export type AdventureStoryPlanView = {
  planId: string
  packageRevision: number
  partyRevision: number
  version: number
  status: 'GENERATING' | 'READY' | 'FAILED'
  currentStage: number
  stageCount: number
  failureReason: string | null
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
  removeMember(sessionId: string, version: number, characterSheetId: string) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/party/${characterSheetId}`, { method: 'DELETE', headers: { 'If-Match-Version': String(version) } })
  }
  start(sessionId: string, version: number, adventureId: string) {
    const requestId = this.startKeys.get(sessionId) ?? crypto.randomUUID()
    this.startKeys.set(sessionId, requestId)
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/start`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version), 'Idempotency-Key': requestId }, body: JSON.stringify({ adventureId }) })
  }
  complete(sessionId: string, version: number) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/complete`, { method: 'POST', headers: { 'If-Match-Version': String(version) } }) }
  delete(sessionId: string, version: number) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}`, { method: 'DELETE', headers: { 'If-Match-Version': String(version) } }) }
  readStoryPlan(sessionId: string) { return this.request<AdventureStoryPlanView>(`/api/v1/adventure-sessions/${sessionId}/story-plan`) }
  generateStoryPlan(sessionId: string) { return this.request<AdventureStoryPlanView>(`/api/v1/adventure-sessions/${sessionId}/story-plan`, { method: 'POST' }) }
  retryStoryPlan(sessionId: string) { return this.request<AdventureStoryPlanView>(`/api/v1/adventure-sessions/${sessionId}/story-plan/retry`, { method: 'POST' }) }
}

export type CreateAdventureSessionRequest = {
  scenarioPackageId: string
  blueprintId: string
  blueprintRevision: number
  partySize?: number
  runtimeConfiguration?: AdventureSessionView['runtimeConfiguration']
}
