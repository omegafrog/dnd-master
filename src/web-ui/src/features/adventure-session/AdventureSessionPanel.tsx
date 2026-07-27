import { useEffect, useState } from 'react'
import type { AdventureSessionApi, AdventureSessionView, SessionControlMode } from './AdventureSessionApi'

export function AdventureSessionPanel({ api, sessionId }: { api: AdventureSessionApi; sessionId: string }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [sheetId, setSheetId] = useState('')
  const [mode, setMode] = useState<SessionControlMode>('DIRECT')
  const [adventureId] = useState(crypto.randomUUID())
  const [message, setMessage] = useState('')
  const frozen = session?.status !== 'DRAFT'
  const load = () => void api.read(sessionId).then(setSession).catch(error => setMessage(error instanceof Error ? error.message : '세션을 불러오지 못했습니다.'))
  useEffect(load, [sessionId])

  async function addMember() {
    if (!session || frozen || !sheetId.trim()) return
    try { setSession(await api.addMember(sessionId, session.version, { characterSheetId: sheetId.trim(), controlMode: mode, nameMutableAfterStart: false, raceMutableAfterStart: false, characterClassMutableAfterStart: false, backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false })); setSheetId('') }
    catch (error) { setMessage(error instanceof Error ? error.message : '파티를 변경하지 못했습니다.') }
  }
  async function start() {
    if (!session || frozen || session.party.length === 0) return
    try { setSession(await api.start(sessionId, session.version, adventureId)); setMessage('모험 시작 완료. 파티가 고정되었습니다.') }
    catch (error) { setMessage(error instanceof Error ? error.message : '모험을 시작하지 못했습니다.') }
  }

  if (!session) return <p role="status">{message || '세션 불러오는 중...'}</p>
  return <section aria-labelledby="session-party-heading">
    <h2 id="session-party-heading">모험 파티</h2>
    <p>상태: {session.status} · {session.party.length}/{session.characterLimit}</p>
    <ul>{session.party.map(member => <li key={member.characterSheetId}>{member.characterSheetId} · {member.controlMode} {!frozen && <button type="button" onClick={() => void api.removeMember(sessionId, session.version, member.characterSheetId).then(setSession)}>제거</button>}</li>)}</ul>
    {!frozen && <div><label>캐릭터 시트 ID <input value={sheetId} onChange={event => setSheetId(event.target.value)} /></label><label>제어 방식 <select value={mode} onChange={event => setMode(event.target.value as SessionControlMode)}><option value="DIRECT">직접 플레이</option><option value="AGENT">에이전트</option></select></label><button type="button" onClick={() => void addMember()} disabled={!sheetId.trim() || session.party.length >= session.characterLimit}>파티에 추가</button></div>}
    {!frozen && <button type="button" onClick={() => void start()} disabled={session.party.length === 0}>모험 시작</button>}
    {frozen && <p>시작 후 파티와 제어 방식은 변경할 수 없습니다.</p>}
    <p role="status">{message}</p>
  </section>
}
