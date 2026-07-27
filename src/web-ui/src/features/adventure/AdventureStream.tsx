import { type FormEvent, useEffect, useState } from 'react'
import type { AdventureApi } from './AdventureApi'

export function AdventureStream({ adventureId, api, controlMode = 'DIRECT', turnIndex = 0 }: { adventureId: string; api: AdventureApi; controlMode?: 'DIRECT' | 'AGENT'; turnIndex?: number }) {
  const [messages, setMessages] = useState<{ speaker: string; text: string }[]>([])
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const [agentTurnIndex, setAgentTurnIndex] = useState(turnIndex)

  useEffect(() => {
    if (controlMode !== 'AGENT' || !api.runAgentTurn) return
    let cancelled = false
    setSending(true)
    void api.runAgentTurn(adventureId, agentTurnIndex).then(response => {
      if (cancelled) return
      setMessages(current => [...current, { speaker: '에이전트 캐릭터', text: response.narration }])
      setAgentTurnIndex(current => current + 1)
    }).catch(() => {
      if (!cancelled) setNotice('에이전트 턴을 실행하지 못했습니다.')
    }).finally(() => { if (!cancelled) setSending(false) })
    return () => { cancelled = true }
  }, [adventureId, agentTurnIndex, api, controlMode])

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const text = String(form.get('message')).trim()
    if (!text) return
    const command = createRuntimeCommandIdentity()
    setNotice('')
    setSending(true)
    setMessages(current => [...current, { speaker: '플레이어', text }])
    try {
      const response = await api.sendMessage(adventureId, text, command)
      setMessages(current => [...current, { speaker: 'AI 게임 마스터', text: response.narration }])
    } catch {
      setNotice('메시지를 전송하지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  return (
    <section aria-labelledby="conversation-heading">
      <h2 id="conversation-heading">모험 대화</h2>
      <p role="status">{controlMode === 'AGENT' ? '에이전트 캐릭터 차례 — 자동 진행 중' : '직접 플레이 입력 대기 중'}</p>
      <ol aria-label="대화 기록">
        {messages.map((message, index) => (
          <li key={index}>
            <strong>{message.speaker}</strong>: {message.text}
          </li>
        ))}
      </ol>
      <p role="alert">{notice}</p>
      <form onSubmit={send} aria-disabled={controlMode === 'AGENT'}>
        <label>행동 또는 대화<input name="message" required /></label>
        <button type="submit" disabled={sending || controlMode === 'AGENT'}>보내기</button>
      </form>
    </section>
  )
}

function createRuntimeCommandIdentity() {
  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return { turnId: globalThis.crypto.randomUUID(), commandId: globalThis.crypto.randomUUID() }
  }
  const fallback = `${Date.now()}-${Math.random()}`
  return { turnId: `turn-${fallback}`, commandId: `command-${fallback}` }
}
