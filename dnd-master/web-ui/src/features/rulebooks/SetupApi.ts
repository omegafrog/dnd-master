export type RulebookStatus = 'PENDING' | 'INDEXED' | 'FAILED'

export type RulebookView = {
  rulebookId: string
  status: RulebookStatus
}

export interface SetupApi {
  uploadRulebook(file: File, ownerId: string): Promise<RulebookView>
  getRulebookStatus(rulebookId: string): Promise<RulebookView>
  uploadScenario(file: File): Promise<{ id: string; name: string }>
  saveRuleSet(rulebookIds: string[]): Promise<void>
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 400) throw new Error('지원하지 않거나 손상된 파일입니다.')
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

  uploadRulebook(file: File, ownerId: string) {
    const body = new FormData()
    body.append('file', file)
    return request<RulebookView>(`/api/v1/rulebooks?ownerPlayerId=${ownerId}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.getToken()}` },
      body,
    })
  }

  getRulebookStatus(rulebookId: string) {
    return request<RulebookView>(`/api/v1/rulebooks/${rulebookId}`, {
      headers: this.authHeaders(),
    })
  }

  uploadScenario(file: File) {
    const body = new FormData()
    body.append('file', file)
    return request<{ id: string; name: string }>('/api/v1/adventures/scenarios', {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.getToken()}` },
      body,
    })
  }

  saveRuleSet(rulebookIds: string[]) {
    return request<void>('/api/v1/rulebooks/rule-set', {
      method: 'POST',
      headers: { ...this.authHeaders(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ rulebookIds }),
    })
  }
}
