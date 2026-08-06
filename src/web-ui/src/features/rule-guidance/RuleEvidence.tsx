import { type FormEvent, useState } from 'react'
import type { RuleGuidanceApi, RuleInquiryResponse } from './RuleGuidanceApi'

export function RuleEvidence({ adventureId, api }: { adventureId: string; api: RuleGuidanceApi }) {
  const [result, setResult] = useState<RuleInquiryResponse | null>(null)
  const [message, setMessage] = useState('')
  const [asking, setAsking] = useState(false)

  async function ask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const situation = String(new FormData(event.currentTarget).get('situation')).trim()
    setAsking(true)
    try {
      setResult(await api.ask(adventureId, situation))
      setMessage('')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '룰 안내를 가져오지 못했습니다.')
    } finally {
      setAsking(false)
    }
  }

  return (
    <section className="adventure-tool evidence-panel" aria-labelledby="evidence-heading">
      <h2 id="evidence-heading">룰 근거 확인</h2>
      <p role="status">{message}</p>
      <form onSubmit={ask}>
        <label>상황<input name="situation" required /></label>
        <button type="submit" disabled={asking}>{asking ? '확인 중…' : '룰 확인'}</button>
      </form>
      {result && (
        <article>
          <h3>질의 결과</h3>
          <p>질의 ID: {result.inquiryId}</p>
          <p>상태: {result.status}</p>
        </article>
      )}
    </section>
  )
}
