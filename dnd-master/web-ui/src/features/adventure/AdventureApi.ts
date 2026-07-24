export interface AdventureApi {
  sendMessage(adventureId: string, message: string): Promise<AdventureMessageResponse>
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

  async sendMessage(adventureId: string, message: string): Promise<AdventureMessageResponse> {
    const response = await fetch(`/api/v1/adventures/${adventureId}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.getToken()}`,
      },
      body: JSON.stringify({ playerId: this.getPlayerId(), action: message }),
    })
    if (!response.ok) throw new Error('모험 메시지를 전송하지 못했습니다.')
    return response.json() as Promise<AdventureMessageResponse>
  }
}
