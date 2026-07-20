import { type FormEvent, useState } from 'react'
import type { AdventureApi } from './AdventureApi'

type Message = { speaker: '플레이어' | 'AI 게임 마스터'; text: string; complete: boolean }

export function AdventureStream({ adventureId, api }: { adventureId: string; api: AdventureApi }) {
  const [messages, setMessages] = useState<Message[]>([])
  const [notice, setNotice] = useState('')
  const [streaming, setStreaming] = useState(false)

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const text = String(form.get('message')).trim()
    if (!text) return
    setNotice('')
    setStreaming(true)
    setMessages(current => [...current, { speaker: '플레이어', text, complete: true },
      { speaker: 'AI 게임 마스터', text: '', complete: false }])
    try {
      for await (const chunk of api.streamMessage(adventureId, text)) {
        setMessages(current => current.map((item, index) => index === current.length - 1
          ? { ...item, text: item.text + chunk } : item))
      }
      setMessages(current => current.map((item, index) => index === current.length - 1
        ? { ...item, complete: true } : item))
    } catch {
      setNotice('응답 스트림이 중단되었습니다. 임시 내용은 확정된 진행이 아닙니다.')
    } finally {
      setStreaming(false)
    }
  }

  return (
    <section aria-labelledby="conversation-heading">
      <h2 id="conversation-heading">모험 대화</h2>
      <ol aria-label="대화 기록">
        {messages.map((message, index) => (
          <li key={index} data-complete={message.complete}>
            <strong>{message.speaker}</strong>: {message.text || '응답 준비 중…'}
            {!message.complete && message.text && <em> (임시 응답)</em>}
          </li>
        ))}
      </ol>
      <p role="alert">{notice}</p>
      <form onSubmit={send}>
        <label>행동 또는 대화<input name="message" required /></label>
        <button type="submit" disabled={streaming}>보내기</button>
      </form>
    </section>
  )
}
