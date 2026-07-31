import { useEffect, useState } from 'react'
import type { AdventureSessionApi, AdventureSessionView, CampaignPlanView, SessionControlMode } from './AdventureSessionApi'

type SessionApi =
  Pick<AdventureSessionApi, 'read' | 'addMember' | 'removeMember' | 'start' | 'complete' | 'delete'>
  & Partial<Pick<AdventureSessionApi, 'prepareCampaignPlan' | 'readCampaignPlan'>>

export function AdventureSessionPanel({ api, sessionId }: { api: SessionApi; sessionId: string }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [plan, setPlan] = useState<CampaignPlanView | null>(null)
  const [sheetId, setSheetId] = useState('')
  const [mode, setMode] = useState<SessionControlMode>('DIRECT')
  const [mutable, setMutable] = useState({ name: false, race: false, characterClass: false, background: false, startingAbilities: false, level: false })
  const [adventureId] = useState(crypto.randomUUID())
  const [message, setMessage] = useState('')
  const frozen = session?.status !== 'DRAFT'

  const load = () => {
    void api.read(sessionId).then(setSession).catch(error => setMessage(error instanceof Error ? error.message : '세션을 불러오지 못했습니다.'))
    if (api.readCampaignPlan) void api.readCampaignPlan(sessionId).then(setPlan).catch(() => undefined)
  }
  useEffect(load, [api, sessionId])

  async function addMember() {
    if (!session || frozen || !sheetId.trim()) return
    try {
      setSession(await api.addMember(sessionId, session.version, {
        characterSheetId: sheetId.trim(),
        controlMode: mode,
        nameMutableAfterStart: mutable.name,
        raceMutableAfterStart: mutable.race,
        characterClassMutableAfterStart: mutable.characterClass,
        backgroundMutableAfterStart: mutable.background,
        startingAbilitiesMutableAfterStart: mutable.startingAbilities,
        levelMutableAfterStart: mutable.level,
      }))
      setPlan(null)
      setSheetId('')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '파티를 변경하지 못했습니다.')
    }
  }

  async function preparePlan() {
    if (!api.prepareCampaignPlan) return null
    try {
      const prepared = await api.prepareCampaignPlan(sessionId)
      setPlan(prepared)
      setMessage(`캠페인 계획 ${prepared.revision}판을 준비했습니다.`)
      return prepared
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '캠페인 계획을 준비하지 못했습니다.')
      return null
    }
  }

  async function start() {
    if (!session || frozen || session.party.length === 0) return
    if (api.prepareCampaignPlan) {
      const prepared = await preparePlan()
      if (!prepared) return
    }
    try {
      setSession(await api.start(sessionId, session.version, adventureId))
      setMessage('모험 시작 완료. 캠페인 계획과 파티가 고정되었습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험을 시작하지 못했습니다.')
    }
  }

  async function finish(action: 'complete' | 'delete') {
    if (!session || session.status !== 'STARTED') return
    try {
      setSession(await api[action](sessionId, session.version))
      setMessage('세션이 종료되었습니다. 캐릭터 시트 정리를 요청했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '세션을 종료하지 못했습니다.')
    }
  }

  if (!session) return <p role="status">{message || '세션 불러오는 중...'}</p>
  return <section aria-labelledby="session-party-heading">
    <h2 id="session-party-heading">모험 파티</h2>
    <p>상태: {session.status} · {session.party.length}/{session.characterLimit}</p>
    <ul>{session.party.map(member => <li key={member.characterSheetId}>{member.characterSheetId} · {member.controlMode} {!frozen && <button type="button" onClick={() => void api.removeMember(sessionId, session.version, member.characterSheetId).then(value => { setSession(value); setPlan(null) })}>제거</button>}</li>)}</ul>
    {!frozen && <div><label>캐릭터 시트 ID <input value={sheetId} onChange={event => setSheetId(event.target.value)} /></label><label>제어 방식 <select value={mode} onChange={event => setMode(event.target.value as SessionControlMode)}><option value="DIRECT">직접 플레이</option><option value="AGENT">에이전트</option></select></label><fieldset><legend>시작 속성 변경 허용</legend>{Object.entries(mutable).map(([key, checked]) => <label key={key}><input type="checkbox" checked={checked} onChange={event => setMutable(previous => ({ ...previous, [key]: event.target.checked }))} />{key}</label>)}</fieldset><button type="button" onClick={() => void addMember()} disabled={!sheetId.trim() || session.party.length >= session.characterLimit}>파티에 추가</button></div>}
    {!frozen && api.prepareCampaignPlan && <button type="button" onClick={() => void preparePlan()} disabled={session.party.length === 0}>캠페인 계획 준비</button>}
    {plan && <section aria-labelledby="campaign-plan-heading">
      <h3 id="campaign-plan-heading">캠페인 단계 계획</h3>
      <p>{plan.overview}</p>
      <p>계획 revision {plan.revision} · STORYBOOK {plan.documents.length}개 · 캐릭터 {plan.characterSheetIds.length}명</p>
      <ol>{plan.stages.map(stage => <li key={stage.order}>
        <h4>{stage.order}. {stage.scene}</h4>
        <p>목표: {stage.goal}</p>
        <p>갈등: {stage.conflict}</p>
        <p>전환: {stage.transitionCondition}</p>
        <details><summary>근거 연결</summary><ul>{stage.evidenceIds.map(id => {
          const evidence = plan.evidence.find(item => item.evidenceId === id)
          return <li key={id}>{evidence ? `${evidence.locator} · ${evidence.excerpt}` : id}</li>
        })}</ul></details>
      </li>)}</ol>
    </section>}
    {!frozen && <button type="button" onClick={() => void start()} disabled={session.party.length === 0}>모험 시작</button>}
    {frozen && <p>시작 후 파티와 제어 방식은 변경할 수 없습니다. 종료된 세션의 시트는 비활성화됩니다.</p>}
    {session.status === 'STARTED' && <div><button type="button" onClick={() => void finish('complete')}>세션 완료</button><button type="button" onClick={() => void finish('delete')}>세션 삭제</button></div>}
    <p role="status">{message}</p>
  </section>
}
