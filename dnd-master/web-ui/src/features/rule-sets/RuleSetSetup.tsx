import { type FormEvent, useState } from 'react'
import type { SetupApi } from '../rulebooks/SetupApi'

export function RuleSetSetup({ api }: { api: SetupApi }) {
  const [message, setMessage] = useState('')
  const [rulebookId, setRulebookId] = useState('')
  const [status, setStatus] = useState('')

  async function checkStatus(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!rulebookId.trim()) {
      setMessage('룰북 ID를 입력하세요.')
      return
    }
    try {
      const result = await api.getRulebookStatus(rulebookId.trim())
      setStatus(result.status)
      setMessage('')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '상태를 확인하지 못했습니다.')
    }
  }

  return (
    <section aria-labelledby="rules-heading">
      <h2 id="rules-heading">룰북 상태 확인</h2>
      <form onSubmit={checkStatus}>
        <label>룰북 ID<input name="rulebookId" value={rulebookId}
          onChange={event => setRulebookId(event.currentTarget.value)} required /></label>
        <button type="submit">상태 조회</button>
      </form>
      <p role="status">{message}</p>
      {status && <p>상태: {status}</p>}
    </section>
  )
}
