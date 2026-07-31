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

export type CampaignPlanDocumentRevision = {
  knowledgeDocumentId: string
  extractionVersion: number
  originalFilename: string
}

export type CampaignPlanEvidence = {
  evidenceId: string
  knowledgeDocumentId: string
  extractionVersion: number
  locator: string
  excerpt: string
}

export type CampaignPlanStage = {
  order: number
  scene: string
  goal: string
  conflict: string
  cluesAndNpcs: string[]
  transitionCondition: string
  evidenceIds: string[]
}

export type CampaignPlanView = {
  planId: string
  sessionId: string
  scenarioPackageId: string
  scenarioPackageRevision: number
  revision: number
  overview: string
  documents: CampaignPlanDocumentRevision[]
  characterSheetIds: string[]
  evidence: CampaignPlanEvidence[]
  stages: CampaignPlanStage[]
}

export class AdventureSessionApi {
  private readonly startKeys = new Map<string, string>()
  constructor(private readonly token: string) {}

  private headers(extra: HeadersInit = {}): HeadersInit { return { Authorization: `Bearer ${this.token}`, ...extra } }
  private async request<T>(url: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, { ...init, headers: this.headers(init.headers) })
    if (!response.ok) {
      const body = await response.text()
      let message = body
      try {
        const parsed = JSON.parse(body) as { code?: string; message?: string }
        if (parsed.message) message = parsed.code ? `${parsed.code}: ${parsed.message}` : parsed.message
      } catch {
        // Keep the original response body for non-JSON errors.
      }
      throw new Error(message || `HTTP ${response.status}`)
    }
    return response.json() as Promise<T>
  }
  read(sessionId: string) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}`) }
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
  prepareCampaignPlan(sessionId: string) {
    return this.request<CampaignPlanView>(`/api/v1/adventure-sessions/${sessionId}/campaign-plan`, { method: 'POST' })
  }
  readCampaignPlan(sessionId: string) {
    return this.request<CampaignPlanView>(`/api/v1/adventure-sessions/${sessionId}/campaign-plan`)
  }
  start(sessionId: string, version: number, adventureId: string) {
    const requestId = this.startKeys.get(sessionId) ?? crypto.randomUUID()
    this.startKeys.set(sessionId, requestId)
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/start`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version), 'Idempotency-Key': requestId }, body: JSON.stringify({ adventureId }) })
  }
  complete(sessionId: string, version: number) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/complete`, { method: 'POST', headers: { 'If-Match-Version': String(version) } }) }
  delete(sessionId: string, version: number) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}`, { method: 'DELETE', headers: { 'If-Match-Version': String(version) } }) }
}

export type CreateAdventureSessionRequest = {
  scenarioPackageId: string
  blueprintId: string
  blueprintRevision: number
  runtimeConfiguration?: AdventureSessionView['runtimeConfiguration']
}
