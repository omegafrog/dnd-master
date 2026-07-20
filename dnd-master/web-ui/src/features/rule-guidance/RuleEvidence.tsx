import { type FormEvent, useState } from 'react'
import type { CandidateRule, RuleGuidance, RuleGuidanceApi, SourceLocation } from './RuleGuidanceApi'

function Sources({ values }: { values: SourceLocation[] }) {
  return <ul aria-label="출처 위치">{values.map(source =>
    <li key={`${source.rulebook}-${source.locator}`}>{source.rulebook} — {source.locator}</li>)}</ul>
}

export function RuleEvidence({ adventureId, api }: { adventureId: string; api: RuleGuidanceApi }) {
  const [guidance, setGuidance] = useState<RuleGuidance | null>(null)
  const [message, setMessage] = useState('')
  const [selected, setSelected] = useState('')

  async function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const situation = String(new FormData(event.currentTarget).get('situation')).trim()
    try { setGuidance(await api.ask(adventureId, situation)); setMessage('') }
    catch (error) { setMessage(error instanceof Error ? error.message : '룰 안내를 가져오지 못했습니다.') }
  }

  async function choose(candidate: CandidateRule) {
    if (!guidance || !guidance.candidates.some(item => item.id === candidate.id)) return
    await api.selectFinalRule(guidance.inquiryId, candidate.id)
    setSelected(candidate.text)
  }

  const authoritative = guidance?.status === 'SUFFICIENT' && guidance.sources.length > 0
  return (
    <section aria-labelledby="evidence-heading">
      <h2 id="evidence-heading">룰 근거 확인</h2>
      <p role="status">{message}</p>
      <form onSubmit={ask}>
        <label>상황<input name="situation" required /></label><button type="submit">룰 확인</button>
      </form>
      {guidance && authoritative && <article><h3>근거가 충분한 답변</h3><p>{guidance.answer}</p><Sources values={guidance.sources} /></article>}
      {guidance?.status === 'SUFFICIENT' && !authoritative &&
        <p role="alert">출처가 없는 응답은 확정된 룰 답변으로 표시할 수 없습니다.</p>}
      {guidance && guidance.status !== 'SUFFICIENT' && (
        <article>
          <h3>{guidance.status === 'INSUFFICIENT' ? '근거 부족' : '근거 충돌'}</h3>
          <p>아래 공개 후보 중 최종 적용 규칙을 선택하세요.</p>
          <ul aria-label="후보 규칙">{guidance.candidates.map(candidate => <li key={candidate.id}>
            <p>{candidate.text}</p><Sources values={candidate.sources} />
            <button type="button" onClick={() => void choose(candidate)}>이 규칙 선택</button>
          </li>)}</ul>
        </article>
      )}
      {selected && <p>최종 적용 규칙: {selected}</p>}
    </section>
  )
}
