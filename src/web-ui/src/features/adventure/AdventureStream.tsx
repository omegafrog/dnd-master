import { type FormEvent, useEffect, useRef, useState } from 'react'
import type { AdventureApi } from './AdventureApi'

export function AdventureStream({ adventureId, api, controlMode = 'DIRECT', expectedVersion = 0 }: { adventureId: string; api: AdventureApi; controlMode?: 'DIRECT' | 'AGENT'; expectedVersion?: number }) {
  const [messages, setMessages] = useState<{ speaker: string; text: string }[]>([])
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const [agentVersion, setAgentVersion] = useState(expectedVersion)
  const [activeControlMode, setActiveControlMode] = useState(controlMode)
  const [projectionStatus, setProjectionStatus] = useState<'idle' | 'processing' | 'failed'>('idle')
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

  // Opening narration is an ordinary GM message. Do not split it into a
  // separate "opening scene" card, which duplicated the conversation and
  // encouraged internal stage metadata to leak into the player view.
  const historyMessages = messages

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
      setAgentVersion(response.version)
      setActiveControlMode(response.nextControlMode ?? 'DIRECT')
    }).catch(() => {
      if (!cancelled) setNotice('에이전트 턴을 실행하지 못했습니다.')
    }).finally(() => { if (!cancelled) setSending(false) })
    return () => { cancelled = true }
  }, [adventureId, agentVersion, activeControlMode, api])

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const text = String(form.get('message')).trim()
    if (!text) return
    const command = createRuntimeCommandIdentity()
    setNotice('')
    setSending(true)
    setProjectionStatus('processing')
    setMessages(current => [...current, { speaker: '플레이어', text }])
    try {
      const response = await api.sendMessage(adventureId, text, command, projectionVersion.current)
      setMessages(current => [...current, { speaker: 'AI 게임 마스터', text: response.narration }])
    } catch {
      setProjectionStatus('failed')
      setNotice('메시지를 전송하지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  return (
    <section className="adventure-stream" aria-labelledby="conversation-heading">
      <h2 id="conversation-heading">모험 대화</h2>
      {messages.length > 0 && <h3 className="sr-only">첫 장면</h3>}
      <p role="status">{projectionStatus === 'processing' ? '턴 처리 중' : projectionStatus === 'failed' ? '턴 처리 실패' : activeControlMode === 'AGENT' ? '에이전트 캐릭터 차례 — 자동 진행 중' : '직접 플레이 입력 대기 중'}</p>
      <ol className={historyMessages.length === 0 ? 'opening-only' : undefined} aria-label="대화 기록">
        {historyMessages.map((message, index) => (
          <li key={index} className={`adventure-chat-message ${message.speaker === '플레이어' ? 'player' : 'gm'}`}>
            <span className="adventure-chat-speaker">{message.speaker}</span>
            <ChatMessage text={playerNarration(message.text)} />
          </li>
        ))}
      </ol>
      <p role="alert">{notice}</p>
      <form onSubmit={send} aria-disabled={activeControlMode === 'AGENT'}>
        <label>무엇을 하시겠어요?<input name="message" required /></label>
        <button type="submit" disabled={sending || activeControlMode === 'AGENT'}>행동 보내기</button>
      </form>
    </section>
  )
}

function playerNarration(text: string) {
  if (!text.includes('## Stage')) return text
  const visible = text.split('## Stage')[0].trim()
  return visible || '주변을 둘러보니 뭔가 심상치 않은 일이 벌어지고 있어요. 어떻게 움직일까요?'
}

function ChatMessage({ text }: { text: string }) {
  const match = text.match(/^(.*?)(?:\n\s*\n)?선택지:\s*\n((?:\s*\d+[.)]\s+.+(?:\n|$))+)/s)
  if (!match) return <p>{text}</p>
  const choices = match[2].trim().split(/\n/).map(line => line.replace(/^\s*\d+[.)]\s+/, '').trim()).filter(Boolean)
  return <>
    <p>{match[1].trim()}</p>
    <div className="adventure-choice-block"><strong>선택지</strong><ol aria-label="선택지">{choices.map((choice, index) => <li key={`${index}-${choice}`}>{choice}</li>)}</ol></div>
  </>
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
