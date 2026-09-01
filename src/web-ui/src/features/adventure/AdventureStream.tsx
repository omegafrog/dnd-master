import { type FormEvent, useEffect, useRef, useState } from 'react'
import type { AdventureApi } from './AdventureApi'

type ChatMessageEntry = { speaker: string; text: string }
type LocalTurn = { action: ChatMessageEntry; response: ChatMessageEntry[]; expectedVersion: number; committedVersion?: number }

export function AdventureStream({ adventureId, api, controlMode = 'DIRECT', expectedVersion, onTurnCommitted }: { adventureId: string; api: AdventureApi; controlMode?: 'DIRECT' | 'AGENT'; expectedVersion?: number | null; onTurnCommitted?: () => void }) {
  const [messages, setMessages] = useState<ChatMessageEntry[]>([])
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const [agentVersion, setAgentVersion] = useState(expectedVersion ?? 0)
  const [activeControlMode, setActiveControlMode] = useState(controlMode)
  const [projectionStatus, setProjectionStatus] = useState<'idle' | 'processing' | 'failed'>('idle')
  const [conversationHydrated, setConversationHydrated] = useState(() => !api.readConversation)
  const hydrationPending = Boolean(api.readConversation) && !conversationHydrated
  const projectionVersion = useRef<number | null>(expectedVersion ?? (api.readConversation ? null : 0))
  const committedVersion = useRef(-1)
  const localTurn = useRef<LocalTurn | null>(null)
  useEffect(() => {
    if (!api.readConversation) {
      setConversationHydrated(true)
      return
    }
    setConversationHydrated(false)
    let cancelled = false
    const knownVersion = projectionVersion.current ?? 0
    void api.readConversation(adventureId).then(response => {
      if (cancelled) return
      projectionVersion.current = Math.max(knownVersion, response.version)
      setMessages(current => reconcileHydratedMessages(
        response.entries.map(entry => ({ speaker: speakerLabel(entry.speaker), text: entry.content })),
        response.version,
        current,
        localTurn.current,
      ))
      setConversationHydrated(true)
    }).catch(() => {
      if (cancelled) return
      setConversationHydrated(false)
      setNotice('대화 기록을 불러오지 못했습니다.')
    })
    return () => { cancelled = true }
  }, [adventureId, api])

  // Opening narration is an ordinary GM message. Do not split it into a
  // separate "opening scene" card, which duplicated the conversation and
  // encouraged internal stage metadata to leak into the player view.
  const historyMessages = messages

  useEffect(() => {
    if (!api.subscribeEvents) return
    if (projectionVersion.current == null) return
    const subscribedVersion = projectionVersion.current
    return api.subscribeEvents(adventureId, subscribedVersion, event => {
      if (event.type !== 'GM_TURN_FAILED') {
        projectionVersion.current = Math.max(projectionVersion.current ?? 0, event.version)
      }
      if (event.type === 'GM_TURN_FAILED' && event.version <= committedVersion.current) return
      if (event.type === 'GM_TURN_COMMITTED') committedVersion.current = Math.max(committedVersion.current, event.version)
      setSending(false)
      setProjectionStatus(event.type === 'GM_TURN_FAILED' ? 'failed' : 'idle')
      setNotice(event.type === 'GM_TURN_FAILED' ? '턴 처리가 실패했습니다.' : '')
      if (event.type === 'GM_TURN_COMMITTED' && api.readConversation) {
        void api.readConversation(adventureId).then(response => {
          projectionVersion.current = Math.max(projectionVersion.current ?? 0, response.version, event.version)
          setMessages(current => reconcileHydratedMessages(
            response.entries.map(entry => ({ speaker: speakerLabel(entry.speaker), text: entry.content })),
            response.version,
            current,
            localTurn.current,
            true,
          ))
        }).catch(() => {
          // The event has already been acknowledged. Keep the optimistic view
          // and let the next hydration/event reconcile it with the projection.
        })
      }
    }, () => {
      setSending(false); setProjectionStatus('failed'); setNotice('실시간 모험 이벤트 연결이 끊겼습니다.')
      setConversationHydrated(false)
      void api.readConversation?.(adventureId).then(response => {
        projectionVersion.current = response.version
        setConversationHydrated(true)
      }).catch(() => undefined)
    })
  }, [adventureId, api])
  const previousControlMode = useRef(controlMode)

  useEffect(() => {
    if (previousControlMode.current !== controlMode) {
      previousControlMode.current = controlMode
      setActiveControlMode(controlMode)
    }
  }, [controlMode])

  useEffect(() => {
    if (hydrationPending || activeControlMode !== 'AGENT' || !api.runAgentTurn) return
    let cancelled = false
    setSending(true)
    void api.runAgentTurn(adventureId, agentVersion).then(response => {
      if (cancelled) return
      setMessages(current => [...current, { speaker: '에이전트 캐릭터', text: response.narration }])
      setAgentVersion(response.version)
      setActiveControlMode(response.nextControlMode ?? 'DIRECT')
      onTurnCommitted?.()
    }).catch(() => {
      if (!cancelled) setNotice('에이전트 턴을 실행하지 못했습니다.')
    }).finally(() => { if (!cancelled) setSending(false) })
    return () => { cancelled = true }
  }, [adventureId, agentVersion, activeControlMode, api, hydrationPending, onTurnCommitted])

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const text = String(form.get('message')).trim()
    if (!text) return
    const command = createRuntimeCommandIdentity()
    const action = { speaker: '플레이어', text }
    let currentVersion = projectionVersion.current
    if (api.readConversation) {
      try {
        const latest = await api.readConversation(adventureId)
        currentVersion = latest.version
        projectionVersion.current = latest.version
        setConversationHydrated(true)
      } catch {
        setConversationHydrated(false)
        setNotice('최신 모험 상태를 확인하지 못해 행동을 보내지 않았습니다.')
        return
      }
    }
    if (currentVersion == null) {
      setNotice('최신 모험 상태를 확인한 뒤 다시 시도해주세요.')
      return
    }
    localTurn.current = { action, response: [], expectedVersion: currentVersion }
    setNotice('')
    setSending(true)
    setProjectionStatus('processing')
    setMessages(current => [...current, action])
    try {
      const response = await api.sendMessage(adventureId, text, command, currentVersion)
      const responseEntries = responseMessages(response.narration, response.judgment)
      localTurn.current = { action, response: responseEntries, expectedVersion: localTurn.current?.expectedVersion ?? projectionVersion.current, committedVersion: response.version }
      projectionVersion.current = Math.max(projectionVersion.current ?? 0, response.version)
      committedVersion.current = Math.max(committedVersion.current, response.version)
      setProjectionStatus('idle')
      setMessages(current => [...current, ...responseEntries])
      onTurnCommitted?.()
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
      <p role="status" aria-busy={hydrationPending || projectionStatus === 'processing'}>{hydrationPending ? '대화 기록 불러오는 중' : projectionStatus === 'processing' ? '턴 처리 중' : projectionStatus === 'failed' ? '턴 처리 실패' : activeControlMode === 'AGENT' ? '에이전트 캐릭터 차례 — 자동 진행 중' : '직접 플레이 입력 대기 중'}</p>
      <ol className={historyMessages.length === 0 ? 'opening-only' : undefined} aria-label="대화 기록">
        {historyMessages.map((message, index) => (
          <li key={index} className={`adventure-chat-message ${message.speaker === '플레이어' ? 'player' : 'gm'}`}>
            <span className="adventure-chat-speaker">{message.speaker}</span>
            <ChatMessage text={playerNarration(message.text)} />
          </li>
        ))}
      </ol>
      <p role="alert">{notice}</p>
      <form onSubmit={send} aria-disabled={hydrationPending || projectionVersion.current == null || activeControlMode === 'AGENT'} aria-busy={hydrationPending}>
        <label>무엇을 하시겠어요?<input name="message" required disabled={hydrationPending || projectionVersion.current == null || sending || activeControlMode === 'AGENT'} /></label>
        <button type="submit" disabled={hydrationPending || projectionVersion.current == null || sending || activeControlMode === 'AGENT'}>행동 보내기</button>
      </form>
    </section>
  )
}

function reconcileHydratedMessages(
  persisted: ChatMessageEntry[],
  version: number,
  current: ChatMessageEntry[],
  turn: LocalTurn | null,
  replaceCurrent = false,
) {
  if (!turn) return replaceCurrent || current.length === 0 ? persisted : current

  // A stale initial read can precede persistence of the turn. Keep the local
  // turn in that case; once the returned projection contains it, use the
  // persisted sequence as the source of truth and retain only missing output.
  const canMatchTurn = turn.committedVersion === undefined || version >= turn.committedVersion
  let actionIndex = -1
  if (canMatchTurn) {
    for (let index = persisted.length - 1; index >= 0; index -= 1) {
      if (sameEntry(persisted[index], turn.action)) {
        actionIndex = index
        break
      }
    }
  }
  if (actionIndex < 0) return [...persisted, turn.action, ...turn.response]

  const persistedResponse = persisted.slice(actionIndex + 1, actionIndex + 1 + turn.response.length)
  const responseAlreadyPersisted = turn.response.every((entry, index) => sameEntry(entry, persistedResponse[index]))
  if (responseAlreadyPersisted) return persisted
  return [...persisted.slice(0, actionIndex + 1), ...turn.response, ...persisted.slice(actionIndex + 1)]
}

function sameEntry(left: ChatMessageEntry | undefined, right: ChatMessageEntry | undefined) {
  return left?.speaker === right?.speaker && left?.text === right?.text
}

function responseMessages(narration: string, judgment: string) {
  const messages = [{ speaker: 'AI 게임 마스터', text: narration }]
  const visibleJudgment = judgment.trim()
  if (visibleJudgment) messages.push({ speaker: 'AI 게임 마스터', text: visibleJudgment })
  return messages
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
