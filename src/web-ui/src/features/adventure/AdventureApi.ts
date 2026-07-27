export interface AdventureApi {
  sendMessage(
    adventureId: string,
    message: string,
    command?: { turnId: string; commandId: string },
  ): Promise<AdventureMessageResponse>
  runAgentTurn?(adventureId: string, turnIndex: number): Promise<AdventureMessageResponse>
}

export interface AdventureMessageResponse {
  narration: string
  judgment: string
  currentScene: string
  sourceRefs: string[]
  warnings: string[]
  version: number
}

export class HttpAdventureApi implements AdventureApi {
  private readonly getToken: () => string
  private readonly getPlayerId: () => string

  constructor(getToken: () => string, getPlayerId: () => string) {
    this.getToken = getToken
    this.getPlayerId = getPlayerId
  }

  async sendMessage(
    adventureId: string,
    message: string,
    command?: { turnId: string; commandId: string },
  ): Promise<AdventureMessageResponse> {
    const identity = command ?? createRuntimeCommandIdentity()
    const response = await fetch(`/api/v1/adventures/${adventureId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.getToken()}`,
      },
      body: JSON.stringify({
        playerId: this.getPlayerId(),
        turnId: identity.turnId,
        commandId: identity.commandId,
        action: message,
      }),
    })
    if (!response.ok) throw new Error('모험 메시지를 전송하지 못했습니다.')
    return response.json() as Promise<AdventureMessageResponse>
  }

  async runAgentTurn(adventureId: string, turnIndex: number): Promise<AdventureMessageResponse> {
    const response = await fetch(`/api/v1/adventures/${adventureId}/agent-turns`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.getToken()}` },
      body: JSON.stringify({ playerId: this.getPlayerId(), turnIndex }),
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
