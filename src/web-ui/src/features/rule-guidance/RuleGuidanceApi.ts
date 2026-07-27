export type SourceEvidence = { rulebookId: string; locator: string; excerpt: string }
export type RuleInquiryResponse = { inquiryId: string; status: string }

export interface RuleGuidanceApi {
  ask(adventureId: string, situation: string): Promise<RuleInquiryResponse>
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) throw new Error('룰 안내 요청을 처리하지 못했습니다.')
  return response.json() as Promise<T>
}

export class HttpRuleGuidanceApi implements RuleGuidanceApi {
  private readonly getToken: () => string
  private readonly getPlayerId: () => string

  constructor(getToken: () => string, getPlayerId: () => string) {
    this.getToken = getToken
    this.getPlayerId = getPlayerId
  }

  ask(adventureId: string, situation: string) {
    return request<RuleInquiryResponse>(`/api/v1/adventures/${adventureId}/rule-inquiries`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.getToken()}`,
      },
      body: JSON.stringify({
        inquiryId: crypto.randomUUID(),
        ruleSetId: null,
        playerId: this.getPlayerId(),
        situation,
      }),
    })
  }
}
