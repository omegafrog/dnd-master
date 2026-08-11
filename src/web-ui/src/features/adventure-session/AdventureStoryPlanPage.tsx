import { useEffect, useState } from 'react'
import type { AdventureSessionApi, AdventureSessionView, AdventureStoryPlanView } from './AdventureSessionApi'

type StoryPlanApi = Pick<AdventureSessionApi, 'read' | 'readStoryPlan' | 'generateStoryPlan' | 'retryStoryPlan' | 'start' | 'saveAppliedRuleSet'>

export function AdventureStoryPlanPage({ api, sessionId }: { api: StoryPlanApi; sessionId: string }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [plan, setPlan] = useState<AdventureStoryPlanView | null>(null)
  const [message, setMessage] = useState('')
  const [adventureId] = useState(crypto.randomUUID())

  useEffect(() => {
    let active = true
    void api.read(sessionId).then(next => {
      if (!active) return
      setSession(next)
      return api.readStoryPlan(sessionId).catch(() => api.generateStoryPlan(sessionId))
    }).then(nextPlan => { if (active && nextPlan) setPlan(nextPlan) }).catch(error => { if (active) setMessage(error instanceof Error ? error.message : '모험 계획을 생성하지 못했습니다.') })
    return () => { active = false }
  }, [api, sessionId])

  async function retry() {
    setMessage('')
    try { setPlan(await api.retryStoryPlan(sessionId)) } catch (error) { setMessage(error instanceof Error ? error.message : '모험 계획을 다시 생성하지 못했습니다.') }
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
    } catch (error) { setMessage(error instanceof Error ? error.message : '모험을 시작하지 못했습니다.') }
  }

  if (!session || !plan) return <section aria-labelledby="story-plan-title"><h1 id="story-plan-title">모험 계획 준비 중</h1><p role="status">{message || '파티와 모험 자료를 분석하고 있습니다.'}</p></section>
  const ready = plan.status === 'READY'
  return <section className="story-plan-page" aria-labelledby="story-plan-title">
    <div className="page-heading"><div><p className="eyebrow">ADVENTURE STORY PLAN</p><h1 id="story-plan-title">모험 계획 준비</h1><p>전체 줄거리와 결말은 공개하지 않습니다. 플레이에 필요한 준비 상태만 표시합니다.</p></div><span className="status-chip">{plan.status}</span></div>
    <ol aria-label="모험 계획 생성 단계" className="story-plan-stages"><li className={ready ? 'complete' : 'active'}>모험 자료 분석</li><li className={ready ? 'complete' : 'active'}>파티 구성 분석</li><li className={ready ? 'complete' : 'active'}>주요 모험 단계 구성</li><li className={ready ? 'complete' : 'active'}>분기와 결말 구성</li><li className={ready ? 'complete' : 'active'}>출처와 규칙 검증</li><li className={ready ? 'complete' : 'active'}>플레이 준비 완료</li></ol>
    <p>번들 revision v{plan.packageRevision} · 확정 파티 {session.party.length}명 · 계획 version {plan.version}</p>
    {plan.status === 'FAILED' && <><p role="alert">{plan.failureReason || '계획 생성에 실패했습니다.'}</p><button type="button" onClick={() => void retry()}>다시 생성</button></>}
    {ready && <button type="button" onClick={() => void start()} disabled={!session.runtimeConfiguration}>모험 시작</button>}
    {!session.runtimeConfiguration && <p role="alert">런타임 설정이 없어 시작할 수 없습니다.</p>}
    {message && <p role="status">{message}</p>}
  </section>
}
