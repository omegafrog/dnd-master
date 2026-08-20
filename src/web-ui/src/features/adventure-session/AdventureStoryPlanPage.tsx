import { useEffect, useState } from 'react'
import type { AdventureSessionApi, AdventureSessionView, AdventureStoryPlanView } from './AdventureSessionApi'

type StoryPlanApi = Pick<AdventureSessionApi, 'read' | 'readStoryPlan' | 'generateStoryPlan' | 'retryStoryPlan' | 'start' | 'recoverStart' | 'saveAppliedRuleSet'>
type AdventureLength = 'SHORT' | 'STANDARD' | 'LONG'

export function AdventureStoryPlanPage({ api, sessionId }: { api: StoryPlanApi; sessionId: string }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [plan, setPlan] = useState<AdventureStoryPlanView | null>(null)
  const [message, setMessage] = useState('')
  const [endingCount, setEndingCount] = useState(2)
  const [adventureLength, setAdventureLength] = useState<AdventureLength>('STANDARD')
  const [loadingPlan, setLoadingPlan] = useState(false)
  const [recovering, setRecovering] = useState(false)
  const [adventureId] = useState(crypto.randomUUID())

  useEffect(() => {
    let active = true
    void api.read(sessionId).then(next => {
      if (!active) return
      setSession(next)
      return api.readStoryPlan(sessionId).catch(() => null)
    }).then(nextPlan => {
      if (!active || !nextPlan) return
      setPlan(nextPlan)
      setEndingCount(nextPlan.endingCount)
      setAdventureLength(nextPlan.adventureLength)
    }).catch(error => { if (active) setMessage(error instanceof Error ? error.message : '모험 계획을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [api, sessionId])

  async function retry() {
    setMessage('')
    setLoadingPlan(true)
    try { setPlan(await api.retryStoryPlan(sessionId, { endingCount, adventureLength })) } catch (error) { setMessage(error instanceof Error ? error.message : '모험 계획을 다시 생성하지 못했습니다.') }
    finally { setLoadingPlan(false) }
  }

  async function generate() {
    setMessage('')
    setLoadingPlan(true)
    try { setPlan(await api.generateStoryPlan(sessionId, { endingCount, adventureLength })) } catch (error) { setMessage(error instanceof Error ? error.message : '모험 계획을 생성하지 못했습니다.') }
    finally { setLoadingPlan(false) }
  }

  async function start() {
    if (!session || !plan || plan.status !== 'READY') return
    try {
      const configuration = session.runtimeConfiguration
      if (!configuration || configuration.rulebookIds.length === 0) throw new Error('공유 룰북을 하나 이상 선택하세요.')
      const catalog = await fetch('/api/v1/rulebook-catalog').then(response => response.ok ? response.json() : []) as Array<{ rulebookId?: string; edition?: string }>
      const edition = catalog.find(item => item.rulebookId && configuration.rulebookIds.includes(item.rulebookId))?.edition ?? 'DND_5E_2014'
      await api.saveAppliedRuleSet(adventureId, configuration.ruleSetId, edition, configuration.rulebookIds)
      const started = await api.start(sessionId, session.version, adventureId)
      if (started.adventureId) window.location.hash = `#/adventures/${started.adventureId}`
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험을 시작하지 못했습니다.')
      void api.read(sessionId).then(setSession).catch(() => undefined)
    }
  }

  async function recoverStart() {
    if (!session) return
    setRecovering(true)
    try { setSession(await api.recoverStart(sessionId, session.version)); setMessage('실패한 시작 시도를 복구했습니다. 블루프린트를 수정한 뒤 다시 시작할 수 있습니다.') }
    catch (error) { setMessage(error instanceof Error ? error.message : '시작 시도를 복구하지 못했습니다.') }
    finally { setRecovering(false) }
  }

  if (!session) return <section aria-labelledby="story-plan-title"><h1 id="story-plan-title">모험 계획 준비 중</h1><p role="status">{message || '파티와 모험 자료를 불러오고 있습니다.'}</p></section>
  if (!plan) return <section className="story-plan-page" aria-labelledby="story-plan-title">
    <div className="page-heading"><div><p className="eyebrow">ADVENTURE STORY PLAN</p><h1 id="story-plan-title">모험 계획 설정</h1><p>모험의 길이와 결말 수를 정하면 룰북·스토리북 근거를 바탕으로 플레이용 골격을 만듭니다.</p></div></div>
    <div className="story-plan-config" aria-label="모험 계획 설정">
      <label htmlFor="ending-count">분기 결말 수: <strong>{endingCount}개</strong></label>
      <input id="ending-count" type="range" min="1" max="4" step="1" value={endingCount} onChange={event => setEndingCount(Number(event.currentTarget.value))} />
      <div className="range-hint"><span>선형</span><span>다중 결말</span></div>
      <label htmlFor="adventure-length">모험 길이</label>
      <select id="adventure-length" value={adventureLength} onChange={event => setAdventureLength(event.currentTarget.value as AdventureLength)}>
        <option value="SHORT">짧게 · 1~2회</option>
        <option value="STANDARD">보통 · 3~5회</option>
        <option value="LONG">길게 · 6~8회</option>
      </select>
      <p>확정 파티 {session.party.length}명 · 룰북과 스토리북 근거는 생성 결과에 함께 연결됩니다.</p>
      <button type="button" onClick={() => void generate()} disabled={loadingPlan}>{loadingPlan ? '계획 생성 중…' : '모험 계획 생성'}</button>
    </div>
    {message && <p role="alert">{message}</p>}
  </section>
  const ready = plan.status === 'READY'
  const blocked = plan.status === 'BLOCKED'
  return <section className="story-plan-page" aria-labelledby="story-plan-title">
    <div className="page-heading"><div><p className="eyebrow">ADVENTURE STORY PLAN</p><h1 id="story-plan-title">모험 계획 준비</h1><p>전체 줄거리와 결말은 공개하지 않습니다. 플레이에 필요한 준비 상태만 표시합니다.</p></div><span className="status-chip">{plan.status}</span></div>
    <ol aria-label="모험 계획 생성 단계" className="story-plan-stages"><li className={ready ? 'complete' : 'active'}>모험 자료 분석</li><li className={ready ? 'complete' : 'active'}>파티 구성 분석</li><li className={ready ? 'complete' : 'active'}>주요 모험 단계 구성</li><li className={ready ? 'complete' : 'active'}>분기와 결말 구성</li><li className={ready ? 'complete' : 'active'}>출처와 규칙 검증</li><li className={ready ? 'complete' : 'active'}>플레이 준비 완료</li></ol>
    <p>확정 파티 {session.party.length}명 · 계획 revision {plan.planRevision} · 결말 {plan.endingCount}개 · {plan.adventureLength}</p>
    {ready && <ol className="story-plan-node-list" aria-label="모험 단계 요약">
      {plan.stages.map(stage => <li key={`${stage.position}-${stage.title}`} className="story-plan-node">
        <div className="story-plan-node-heading"><span>{stage.position}</span><div><small>{stage.stageType} · {stage.location}</small><h2>{stage.title}</h2></div></div>
        <p>{stage.goal}</p>
      </li>)}
    </ol>}
    {(blocked || plan.status === 'FAILED') && <><p role="alert">{plan.failureReason || (blocked ? '근거 검증을 통과하지 못해 모험 시작이 차단되었습니다.' : '계획 생성에 실패했습니다.')}</p><button type="button" onClick={() => void retry()} disabled={loadingPlan}>{loadingPlan ? '다시 생성 중…' : '다시 생성'}</button></>}
    <button type="button" onClick={() => void start()} disabled={!ready || !session.runtimeConfiguration}>모험 시작</button>
    {session.status === 'STARTING' && <button type="button" onClick={() => void recoverStart()} disabled={recovering}>{recovering ? '복구 중…' : '실패한 시작 복구'}</button>}
    {!session.runtimeConfiguration && <p role="alert">런타임 설정이 없어 시작할 수 없습니다.</p>}
    {message && <p role="status">{message}</p>}
  </section>
}
