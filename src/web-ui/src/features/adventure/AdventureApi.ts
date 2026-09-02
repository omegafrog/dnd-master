export interface AdventureApi {
  subscribeEvents?(adventureId: string, afterVersion: number, onEvent: (event: AdventureSessionEvent) => void, onError?: () => void): () => void
  readConversation?(adventureId: string): Promise<AdventureConversationResponse>
  sendMessage(
    adventureId: string,
    message: string,
    command?: { turnId: string; commandId: string },
    expectedVersion?: number,
  ): Promise<AdventureMessageResponse>
  runAgentTurn?(adventureId: string, expectedVersion: number): Promise<AdventureMessageResponse>
}

export type AdventureSessionEvent = { version: number; type: string; payload: string }

export type AdventureConversationEntry = { sequence: number; speaker: string; content: string }
export type AdventureConversationResponse = { adventureId: string; version: number; entries: AdventureConversationEntry[] }

export interface AdventureMessageResponse {
  narration: string
  currentScene: string
  visibleFacts?: string[]
  version: number
  nextTurnIndex?: number
  nextControlMode?: 'DIRECT' | 'AGENT'
}

export class HttpAdventureApi implements AdventureApi {
  private readonly getToken: () => string
  private readonly getPlayerId: () => string

  constructor(getToken: () => string, getPlayerId: () => string) {
    this.getToken = getToken
    this.getPlayerId = getPlayerId
  }

  subscribeEvents(adventureId: string, afterVersion: number, onEvent: (event: AdventureSessionEvent) => void, onError?: () => void): () => void {
    const controller = new AbortController()
    void (async () => {
      try {
        const response = await fetch(`/api/v1/adventures/${adventureId}/events?afterVersion=${afterVersion}`, {
          headers: { Authorization: `Bearer ${this.getToken()}` }, signal: controller.signal,
        })
        if (!response.ok || !response.body) throw new Error('event stream failed')
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let eventType = 'message'; let eventId = ''; let data = ''
        while (!controller.signal.aborted) {
          const chunk = await reader.read(); if (chunk.done) break
          buffer += decoder.decode(chunk.value, { stream: true })
          const lines = buffer.split('\n'); buffer = lines.pop() ?? ''
          for (const line of lines) {
            if (line.startsWith('id:')) eventId = line.slice(3).trim()
            else if (line.startsWith('event:')) eventType = line.slice(6).trim()
            else if (line.startsWith('data:')) data += `${line.slice(5).trim()}\n`
            else if (line === '') { if (data) onEvent({ version: Number(eventId), type: eventType, payload: data.trimEnd() }); eventType = 'message'; eventId = ''; data = '' }
          }
        }
      } catch { if (!controller.signal.aborted) onError?.() }
    })()
    return () => controller.abort()
  }

  async readConversation(adventureId: string): Promise<AdventureConversationResponse> {
    const response = await fetch(`/api/v1/adventures/${adventureId}/conversation`, { headers: { Authorization: `Bearer ${this.getToken()}` } })
    if (!response.ok) throw new Error('대화 기록을 불러오지 못했습니다.')
    return response.json() as Promise<AdventureConversationResponse>
  }

  async sendMessage(
    adventureId: string,
    message: string,
    command?: { turnId: string; commandId: string },
    expectedVersion?: number,
  ): Promise<AdventureMessageResponse> {
    if (expectedVersion == null) throw new Error('최신 모험 버전이 필요합니다.')
    const identity = command ?? createRuntimeCommandIdentity()
    const response = await fetch(`/api/v1/adventures/${adventureId}/turns`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.getToken()}`,
        'Idempotency-Key': identity.commandId,
        'If-Match-Version': String(expectedVersion),
      },
      body: JSON.stringify({
        turnId: identity.turnId,
        input: { type: 'TEXT', text: message },
      }),
    })
    if (!response.ok) throw new Error('모험 메시지를 전송하지 못했습니다.')
    return response.json() as Promise<AdventureMessageResponse>
  }

  async runAgentTurn(adventureId: string, expectedVersion: number): Promise<AdventureMessageResponse> {
    const response = await fetch(`/api/v1/adventures/${adventureId}/agent-turns`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.getToken()}` },
      body: JSON.stringify({ playerId: this.getPlayerId(), expectedVersion }),
    })
    if (!response.ok) throw new Error('에이전트 턴을 실행하지 못했습니다.')
    return response.json() as Promise<AdventureMessageResponse>
  }
}

function createRuntimeCommandIdentity() {
  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return { turnId: globalThis.crypto.randomUUID(), commandId: globalThis.crypto.randomUUID() }
  }
  const fallback = `${Date.now()}-${Math.random()}`
  return { turnId: `turn-${fallback}`, commandId: `command-${fallback}` }
}
