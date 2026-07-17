export type SourceLocation = { rulebook: string; locator: string }
export type CandidateRule = { id: string; text: string; sources: SourceLocation[] }
export type RuleGuidance = {
  inquiryId: string
  status: 'SUFFICIENT' | 'INSUFFICIENT' | 'CONFLICTING'
  answer?: string
  sources: SourceLocation[]
  candidates: CandidateRule[]
}

export interface RuleGuidanceApi {
  ask(adventureId: string, situation: string): Promise<RuleGuidance>
  selectFinalRule(inquiryId: string, candidateId: string): Promise<void>
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) throw new Error('룰 안내 요청을 처리하지 못했습니다.')
  return response.json() as Promise<T>
}

export class HttpRuleGuidanceApi implements RuleGuidanceApi {
  ask(adventureId: string, situation: string) {
    return request<RuleGuidance>(`/api/public/adventures/${adventureId}/rule-inquiries`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ situation }),
    })
  }
  async selectFinalRule(inquiryId: string, candidateId: string) {
    await request(`/api/public/rule-inquiries/${inquiryId}/selected-rule`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ candidateId }),
    })
  }
}
