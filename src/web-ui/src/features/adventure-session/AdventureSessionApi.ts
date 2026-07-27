export type SessionControlMode = 'DIRECT' | 'AGENT'
export type AdventureSessionStatus = 'DRAFT' | 'STARTING' | 'STARTED'

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

export class AdventureSessionApi {
  constructor(private readonly token: string) {}

  private headers(extra: HeadersInit = {}): HeadersInit { return { Authorization: `Bearer ${this.token}`, ...extra } }
  private async request<T>(url: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, { ...init, headers: this.headers(init.headers) })
    if (!response.ok) throw new Error(await response.text() || `HTTP ${response.status}`)
    return response.json() as Promise<T>
  }
  read(sessionId: string) { return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}`) }
  addMember(sessionId: string, version: number, member: SessionPartyMember) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/party`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version) }, body: JSON.stringify(member) })
  }
  removeMember(sessionId: string, version: number, characterSheetId: string) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/party/${characterSheetId}`, { method: 'DELETE', headers: { 'If-Match-Version': String(version) } })
  }
  start(sessionId: string, version: number, adventureId: string) {
    return this.request<AdventureSessionView>(`/api/v1/adventure-sessions/${sessionId}/start`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'If-Match-Version': String(version), 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ adventureId }) })
  }
}
