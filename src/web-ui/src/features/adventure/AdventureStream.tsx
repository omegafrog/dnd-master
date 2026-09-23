import { type Dispatch, type FormEvent, type SetStateAction, useEffect, useRef, useState } from 'react'
import { AdventureRequestError, type AdventureApi, type AdventureMessageResponse } from './AdventureApi'
import type { PlayerRollRequest } from './AdventureApi'

type ChatMessageEntry = { speaker: string; text: string }
type LocalTurn = { action: ChatMessageEntry; response: ChatMessageEntry[]; expectedVersion: number; committedVersion?: number }
type ClientLogEntry = { id: number; kind: '요청' | '응답' | '대기' | '오류'; text: string }

const TURN_RECOVERY_INTERVAL_MS = 1000
const TURN_RECOVERY_ATTEMPTS = 60

export function AdventureStream({ adventureId, api, expectedVersion, onTurnCommitted }: { adventureId: string; api: AdventureApi; expectedVersion?: number | null; onTurnCommitted?: () => void }) {
  const [messages, setMessages] = useState<ChatMessageEntry[]>([])
  const [notice, setNotice] = useState('')
  const [sending, setSending] = useState(false)
  const [projectionStatus, setProjectionStatus] = useState<'idle' | 'processing' | 'failed'>('idle')
  const [rollRequest, setRollRequest] = useState<PlayerRollRequest | null>(null)
  const [rollValue, setRollValue] = useState('')
  const [clientLogs, setClientLogs] = useState<ClientLogEntry[]>([])
  const [conversationHydrated, setConversationHydrated] = useState(() => !api.readConversation)
  const [eventSubscriptionReady, setEventSubscriptionReady] = useState(() => !api.readConversation)
  const hydrationPending = Boolean(api.readConversation) && !conversationHydrated
  // The session version and the adventure conversation version are separate
  // optimistic-lock streams. Once conversation hydration is available, start
  // from its version instead of carrying the session version into opening.
  const projectionVersion = useRef<number | null>(api.readConversation ? null : (expectedVersion ?? 0))
  const committedVersion = useRef(-1)
  const localTurn = useRef<LocalTurn | null>(null)
  useEffect(() => {
    if (!api.readConversation) {
      setConversationHydrated(true)
      setEventSubscriptionReady(true)
      return
    }
    setConversationHydrated(false)
    setEventSubscriptionReady(false)
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
      setEventSubscriptionReady(true)
    }).catch(() => {
      if (cancelled) return
      projectionVersion.current = knownVersion
      setConversationHydrated(true)
      setEventSubscriptionReady(true)
      setNotice('대화 기록을 불러오지 못했습니다.')
    })
    return () => { cancelled = true }
  }, [adventureId, api])

  // Opening narration is an ordinary GM message. Do not split it into a
  // separate "opening scene" card, which duplicated the conversation and
  // encouraged internal stage metadata to leak into the player view.
  const historyMessages = messages

  useEffect(() => {
    if (!api.subscribeEvents || !eventSubscriptionReady) return
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
  }, [adventureId, api, eventSubscriptionReady])

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
    appendClientLog(setClientLogs, '요청', `행동 요청: ${text} · 기준 버전 ${currentVersion}`)
    setMessages(current => [...current, action])
    try {
      let response: AdventureMessageResponse
      try {
        response = await api.sendMessage(adventureId, text, command, currentVersion)
      } catch (error) {
        // The provider gateway can time out after the server has already
        // accepted the command. Never submit a second command: the original
        // request may still commit and a new idempotency key would race it.
        if (!(error instanceof AdventureRequestError) || error.status !== 502 || !api.readConversation) throw error
        appendClientLog(setClientLogs, '대기', '첫 요청의 처리 결과를 대화 기록에서 확인하는 중입니다. 같은 행동을 다시 보내지 않습니다.')
        response = await recoverCommittedTurn(api, adventureId, text, currentVersion, setClientLogs)
      }
      if (response.rollRequest) {
        setRollRequest(response.rollRequest)
        setProjectionStatus('idle')
        return
      }
      const responseEntries = responseMessages(response.narration, (response as AdventureMessageResponse & { judgment?: string }).judgment ?? '')
      localTurn.current = { action, response: responseEntries, expectedVersion: localTurn.current?.expectedVersion ?? projectionVersion.current, committedVersion: response.version }
      projectionVersion.current = Math.max(projectionVersion.current ?? 0, response.version)
      committedVersion.current = Math.max(committedVersion.current, response.version)
      // sendMessage resolves only after the synchronous command endpoint has
      // committed the turn. SSE may race with, or be missed after, that commit;
      // it must not keep the direct-input surface disabled indefinitely.
      setProjectionStatus('idle')
      setMessages(current => [...current, ...responseEntries])
      appendClientLog(setClientLogs, '응답', `응답 수신: 버전 ${response.version} · ${response.narration}`)
      onTurnCommitted?.()
    } catch (error) {
      localTurn.current = null
      setMessages(current => current.filter(entry => entry !== action))
      setProjectionStatus('failed')
      appendClientLog(setClientLogs, '오류', error instanceof AdventureRequestError && error.diagnostic ? error.diagnostic : '행동 요청을 완료하지 못했습니다.')
      setNotice(error instanceof AdventureRequestError && error.diagnostic
        ? error.diagnostic
        : '메시지를 전송하지 못했습니다.')
    } finally {
      setSending(false)
    }
  }

  async function submitRoll(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!rollRequest || !api.submitPlayerRoll) return
    const result = Number(rollValue)
    if (!Number.isInteger(result) || result < 1 || result > 20) {
      setNotice('d20 결과는 1에서 20 사이여야 합니다.')
      return
    }
    setSending(true); setProjectionStatus('processing'); setNotice('')
    try {
      const response = await api.submitPlayerRoll(adventureId, rollRequest.pendingTurnId, result, rollRequest.expectedVersion)
      setRollRequest(null); setRollValue('')
      const responseEntries = responseMessages(response.narration, '')
      setMessages(current => [...current, ...responseEntries])
      projectionVersion.current = Math.max(projectionVersion.current ?? 0, response.version)
      committedVersion.current = Math.max(committedVersion.current, response.version)
      setProjectionStatus('idle'); onTurnCommitted?.()
    } catch {
      setProjectionStatus('failed'); setNotice('주사위 결과를 제출하지 못했습니다.')
    } finally { setSending(false) }
  }

  return (
    <section className="adventure-stream" aria-labelledby="conversation-heading">
      <h2 id="conversation-heading">모험 대화</h2>
      {messages.length > 0 && <h3 className="sr-only">첫 장면</h3>}
      <p role="status" aria-busy={hydrationPending || projectionStatus === 'processing'}>{hydrationPending ? '대화 기록 불러오는 중' : projectionStatus === 'processing' ? '턴 처리 중' : projectionStatus === 'failed' ? '턴 처리 실패' : '직접 플레이 입력 대기 중'}</p>
      <ol className={historyMessages.length === 0 ? 'opening-only' : undefined} aria-label="대화 기록">
        {historyMessages.map((message, index) => (
          <li key={index} className={`adventure-chat-message ${message.speaker === '플레이어' ? 'player' : 'gm'}`}>
            <span className="adventure-chat-speaker">{message.speaker}</span>
            <ChatMessage text={playerNarration(message.text)} />
          </li>
        ))}
      </ol>
      <p role="alert">{notice}</p>
      <section className="adventure-client-log" aria-label="요청·응답 로그">
        <h3>요청·응답 로그</h3>
        {clientLogs.length === 0 ? <p>아직 요청 기록이 없습니다.</p> : <ol>{clientLogs.map(log => <li key={log.id}><strong>{log.kind}</strong><span>{log.text}</span></li>)}</ol>}
      </section>
      {rollRequest && <form onSubmit={submitRoll} aria-label="주사위 굴림 요청">
        <p><strong>{rollRequest.label}</strong>: {rollRequest.prompt}</p>
        <label>d20 결과<input type="number" min="1" max="20" step="1" value={rollValue} onChange={event => setRollValue(event.target.value)} disabled={sending} required /></label>
        <button type="submit" disabled={sending}>결과 제출</button>
      </form>}
      <form onSubmit={send} aria-disabled={hydrationPending || projectionVersion.current == null || rollRequest !== null} aria-busy={hydrationPending}>
        <label>무엇을 하시겠어요?<input name="message" required disabled={hydrationPending || projectionVersion.current == null || sending || rollRequest !== null} /></label>
        <button type="submit" disabled={hydrationPending || projectionVersion.current == null || sending || rollRequest !== null}>행동 보내기</button>
      </form>
    </section>
  )
}

function appendClientLog(setLogs: Dispatch<SetStateAction<ClientLogEntry[]>>, kind: ClientLogEntry['kind'], text: string) {
  setLogs(current => [...current, { id: Date.now() + current.length, kind, text }].slice(-50))
}

async function recoverCommittedTurn(
  api: AdventureApi,
  adventureId: string,
  actionText: string,
  expectedVersion: number,
  setLogs: Dispatch<SetStateAction<ClientLogEntry[]>>,
): Promise<AdventureMessageResponse> {
  for (let attempt = 0; attempt < TURN_RECOVERY_ATTEMPTS; attempt += 1) {
    const conversation = await api.readConversation!(adventureId)
    const actionIndex = [...conversation.entries].map((entry, index) => ({ entry, index }))
      .reverse().find(item => item.entry.speaker === 'PLAYER' && item.entry.content === actionText)?.index ?? -1
    if (actionIndex >= 0) {
      const responseEntries = conversation.entries.slice(actionIndex + 1).filter(entry => entry.speaker !== 'PLAYER')
      if (responseEntries.length > 0 && conversation.version > expectedVersion) {
        return {
          narration: responseEntries.map(entry => entry.content).join('\n\n'),
          currentScene: '',
          version: conversation.version,
        }
      }
    }
    if (attempt < TURN_RECOVERY_ATTEMPTS - 1) {
      appendClientLog(setLogs, '대기', `처리 중… ${attempt + 1}/${TURN_RECOVERY_ATTEMPTS}`)
      await new Promise(resolve => window.setTimeout(resolve, TURN_RECOVERY_INTERVAL_MS))
    }
  }
  throw new AdventureRequestError('모험 메시지를 전송하지 못했습니다.', 502, '요청은 접수됐지만 제한 시간 안에 완료 결과를 확인하지 못했습니다.')
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
