import { type FormEvent, useState } from 'react'
import type { AdventureApi } from './AdventureApi'

export function AdventureStream({ adventureId, api }: { adventureId: string; api: AdventureApi }) {
  const [messages, setMessages] = useState<{ speaker: string; text: string }[]>([])
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const text = String(form.get('message')).trim()
    if (!text) return
    setNotice('')
    setSending(true)
    setMessages(current => [...current, { speaker: '플레이어', text }])
    try {
      await api.sendMessage(adventureId, text)
      setMessages(current => [...current, { speaker: 'AI 게임 마스터', text: '(응답 전송됨)' }])
    } catch {
      setNotice('메시지를 전송하지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  return (
    <section aria-labelledby="conversation-heading">
      <h2 id="conversation-heading">모험 대화</h2>
      <ol aria-label="대화 기록">
        {messages.map((message, index) => (
          <li key={index}>
            <strong>{message.speaker}</strong>: {message.text}
          </li>
        ))}
      </ol>
      <p role="alert">{notice}</p>
      <form onSubmit={send}>
        <label>행동 또는 대화<input name="message" required /></label>
        <button type="submit" disabled={sending}>보내기</button>
      </form>
    </section>
  )
}
