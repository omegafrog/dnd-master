import { type FormEvent, useEffect, useRef, useState } from 'react'
import { AdventureRequestError, type AdventureApi } from './AdventureApi'

export function AdventureStream({ adventureId, api, controlMode = 'DIRECT', expectedVersion = 0 }: { adventureId: string; api: AdventureApi; controlMode?: 'DIRECT' | 'AGENT'; expectedVersion?: number }) {
  const [messages, setMessages] = useState<{ speaker: string; text: string }[]>([])
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const [agentVersion, setAgentVersion] = useState(expectedVersion)
  const [activeControlMode, setActiveControlMode] = useState(controlMode)
  const [projectionStatus, setProjectionStatus] = useState<'idle' | 'processing' | 'failed'>('idle')
  const [retry, setRetry] = useState<{ text: string; command: { turnId: string; commandId: string } } | null>(null)
  const projectionVersion = useRef(expectedVersion)
  const committedVersion = useRef(-1)
  useEffect(() => {
    if (!api.readConversation) return
    let cancelled = false
    void api.readConversation(adventureId).then(response => {
      if (cancelled) return
      projectionVersion.current = Math.max(projectionVersion.current, response.version)
      setMessages(current => current.length === 0
        ? response.entries.map(entry => ({ speaker: speakerLabel(entry.speaker), text: entry.content }))
        : current)
    }).catch(() => { if (!cancelled) setNotice('대화 기록을 불러오지 못했습니다.') })
    return () => { cancelled = true }
  }, [adventureId, api])

  useEffect(() => {
    if (!api.subscribeEvents) return
    return api.subscribeEvents(adventureId, projectionVersion.current, event => {
      if (event.type !== 'GM_TURN_FAILED') {
        projectionVersion.current = Math.max(projectionVersion.current, event.version)
      }
      if (event.type === 'GM_TURN_FAILED' && event.version <= committedVersion.current) return
      if (event.type === 'GM_TURN_COMMITTED') committedVersion.current = Math.max(committedVersion.current, event.version)
      setSending(false)
      setProjectionStatus(event.type === 'GM_TURN_FAILED' ? 'failed' : 'idle')
      setNotice(event.type === 'GM_TURN_FAILED' ? '턴 처리가 실패했습니다.' : '')
    }, () => { setSending(false); setProjectionStatus('failed'); setNotice('실시간 모험 이벤트 연결이 끊겼습니다.') })
  }, [adventureId, api])
  const previousControlMode = useRef(controlMode)

  useEffect(() => {
    if (previousControlMode.current !== controlMode) {
      previousControlMode.current = controlMode
      setActiveControlMode(controlMode)
    }
  }, [controlMode])

  useEffect(() => {
    if (activeControlMode !== 'AGENT' || !api.runAgentTurn) return
    let cancelled = false
    setSending(true)
    void api.runAgentTurn(adventureId, agentVersion).then(response => {
      if (cancelled) return
      setMessages(current => [...current, { speaker: '에이전트 캐릭터', text: response.narration }])
      setNotice(groundingNotice(response.warnings))
      setAgentVersion(response.version)
      setActiveControlMode(response.nextControlMode ?? 'DIRECT')
    }).catch(() => {
      if (!cancelled) setNotice('에이전트 턴을 실행하지 못했습니다.')
    }).finally(() => { if (!cancelled) setSending(false) })
    return () => { cancelled = true }
  }, [adventureId, agentVersion, activeControlMode, api])

  async function submit(text: string, command: { turnId: string; commandId: string }, appendPlayer: boolean) {
    setNotice('')
    setSending(true)
    setProjectionStatus('processing')
    if (appendPlayer) setMessages(current => [...current, { speaker: '플레이어', text }])
    try {
      const response = await api.sendMessage(adventureId, text, command, projectionVersion.current)
      projectionVersion.current = Math.max(projectionVersion.current, response.version)
      setMessages(current => [...current, { speaker: 'AI 게임 마스터', text: response.narration }])
      setNotice(groundingNotice(response.warnings))
      setRetry(null)
    } catch (error) {
      setProjectionStatus('failed')
      setRetry(error instanceof AdventureRequestError && !error.failure.retryable ? null : { text, command })
      setNotice(error instanceof AdventureRequestError ? error.failure.safeMessage : '메시지를 전송하지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = String(new FormData(event.currentTarget).get('message')).trim()
    if (text) await submit(text, createRuntimeCommandIdentity(), true)
  }

  return (
    <section className="adventure-stream" aria-labelledby="conversation-heading">
      <h2 id="conversation-heading">모험 대화</h2>
      <p role="status">{projectionStatus === 'processing' ? '턴 처리 중' : projectionStatus === 'failed' ? '턴 처리 실패' : activeControlMode === 'AGENT' ? '에이전트 캐릭터 차례 — 자동 진행 중' : '직접 플레이 입력 대기 중'}</p>
      <ol aria-label="대화 기록">
        {messages.map((message, index) => (
          <li key={index}>
            <strong>{message.speaker}</strong>: {message.text}
          </li>
        ))}
      </ol>
      <p role="alert">{notice}</p>
      {retry ? <button type="button" onClick={() => void submit(retry.text, retry.command, false)} disabled={sending}>다시 시도</button> : null}
      <form onSubmit={send} aria-disabled={activeControlMode === 'AGENT'}>
        <label>행동 또는 대화<input name="message" required /></label>
        <button type="submit" disabled={sending || activeControlMode === 'AGENT'}>보내기</button>
      </form>
    </section>
  )
}

function groundingNotice(warnings: string[]) {
  return warnings.some(warning => warning.startsWith('degraded-mode:'))
    ? '근거가 부족해 안전한 대기 응답을 표시했습니다.'
    : ''
}

function speakerLabel(speaker: string) {
  if (speaker === 'AI_GAME_MASTER') return 'AI 게임 마스터'
  if (speaker === 'PLAYER') return '플레이어'
  return speaker
}

function createRuntimeCommandIdentity() {
  if (globalThis.crypto && 'randomUUID' in globalThis.crypto) {
    return { turnId: globalThis.crypto.randomUUID(), commandId: globalThis.crypto.randomUUID() }
  }
  const fallback = `${Date.now()}-${Math.random()}`
  return { turnId: `turn-${fallback}`, commandId: `command-${fallback}` }
}
