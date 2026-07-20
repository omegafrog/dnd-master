export type RulebookStatus = 'EXTRACTING' | 'PARTIAL' | 'INDEXING' | 'READY' | 'FAILED'

export type RulebookView = {
  id: string
  name: string
  status: RulebookStatus
  warnings: string[]
  owned: boolean
}

export interface SetupApi {
  uploadRulebook(file: File): Promise<RulebookView>
  refreshRulebook(id: string): Promise<RulebookView>
  confirmPartialExtraction(id: string): Promise<RulebookView>
  uploadScenario(file: File): Promise<{ id: string; name: string }>
  saveRuleSet(edition: '2014' | '2024', rulebookIds: string[]): Promise<void>
}

async function publicRequest<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 400) throw new Error('지원하지 않거나 손상된 파일입니다.')
  if (response.status === 403) throw new Error('다른 플레이어의 자료는 사용할 수 없습니다.')
  if (!response.ok) throw new Error('자료 설정 요청을 처리하지 못했습니다.')
  return response.json() as Promise<T>
}

export class HttpSetupApi implements SetupApi {
  uploadRulebook(file: File) {
    const body = new FormData()
    body.append('file', file)
    return publicRequest<RulebookView>('/api/public/rulebooks', { method: 'POST', body })
  }

  refreshRulebook(id: string) {
    return publicRequest<RulebookView>(`/api/public/rulebooks/${id}`, { method: 'GET' })
  }

  confirmPartialExtraction(id: string) {
    return publicRequest<RulebookView>(`/api/public/rulebooks/${id}/partial-extraction-confirmations`, { method: 'POST' })
  }

  uploadScenario(file: File) {
    const body = new FormData()
    body.append('file', file)
    return publicRequest<{ id: string; name: string }>('/api/public/adventures/scenarios', { method: 'POST', body })
  }

  async saveRuleSet(edition: '2014' | '2024', rulebookIds: string[]) {
    await publicRequest<unknown>('/api/public/adventures/rule-sets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ edition, rulebookIds }),
    })
  }
}
