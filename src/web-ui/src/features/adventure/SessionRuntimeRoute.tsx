import { useCallback, useEffect, useState } from 'react'
import type { AdventureApi } from './AdventureApi'
import { SessionRuntime, SpatialTurnRuntime, type RuntimePartyCharacter } from './SessionRuntime'
import type { RuntimeHandout } from './SessionRuntime'
import { CombatScreen } from '../combat/CombatScreen'
import type { CombatApi, CombatFinalSummary, CombatSnapshot } from '../combat/CombatApi'
import { CombatMapView } from '../combat-map/CombatMapView'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import type { SetupApi } from '../rulebooks/SetupApi'
import type { AdventureConversationEntry } from './AdventureApi'
import { AdventureCredits } from './AdventureCredits'

export function SessionRuntimeRoute({ sessionId, sessionApi, adventureApi, playApi, setupApi, combatApi }: { sessionId: string; sessionApi: AdventureSessionApi; adventureApi: AdventureApi; playApi: AdventurePlayApi; setupApi: SetupApi; combatApi: CombatApi }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [completionEntries, setCompletionEntries] = useState<AdventureConversationEntry[] | null>(null)
  const [completionMessage, setCompletionMessage] = useState('')
  const [combatSnapshot, setCombatSnapshot] = useState<CombatSnapshot | null>(null)
  const [combatFinalSummary, setCombatFinalSummary] = useState<CombatFinalSummary | null>(null)
  const [mapRefreshToken, setMapRefreshToken] = useState(0)
  const [partyCharacters, setPartyCharacters] = useState<RuntimePartyCharacter[]>([])
  const [handouts, setHandouts] = useState<RuntimeHandout[]>([])
  const [handoutsLoading, setHandoutsLoading] = useState(false)
  const [handoutsMessage, setHandoutsMessage] = useState('')
  const [message, setMessage] = useState('')
  const getHandoutPreview = useCallback((knowledgeDocumentId: string) => setupApi.getSourcePreview(knowledgeDocumentId), [setupApi])
  const refreshCombat = useCallback((adventureId: string) => {
    void Promise.all([combatApi.readSnapshot(adventureId), combatApi.readFinalSummary?.(adventureId) ?? Promise.resolve(null)])
      .then(([snapshot, summary]) => { setCombatSnapshot(snapshot); setCombatFinalSummary(snapshot ? null : summary) })
      .catch(() => undefined)
  }, [combatApi])
  const onTurnCommitted = useCallback(() => {
    setMapRefreshToken(current => current + 1)
    if (session?.adventureId) refreshCombat(session.adventureId)
    void sessionApi.read(sessionId).then(setSession).catch(() => undefined)
  }, [refreshCombat, session?.adventureId, sessionApi, sessionId])
  const completedAdventureId = session?.status === 'COMPLETED' ? session.adventureId : null
  const combatEventCursor = combatSnapshot?.eventCursor

  useEffect(() => {
    let active = true
    setMessage('')
    void sessionApi.read(sessionId).then(next => { if (active) setSession(next) }).catch(error => { if (active) setMessage(error instanceof Error ? error.message : '세션을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [sessionApi, sessionId])

  useEffect(() => {
    if (!completedAdventureId || !adventureApi.readConversation) return
    let active = true
    setCompletionEntries(null)
    setCompletionMessage('')
    void adventureApi.readConversation(completedAdventureId)
      .then(conversation => { if (active) setCompletionEntries(conversation.entries) })
      .catch(error => { if (active) setCompletionMessage(error instanceof Error ? error.message : '완료 기록을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [adventureApi, completedAdventureId])

  useEffect(() => {
    if (!session) return
    let active = true
    void Promise.all(session.party.map(async member => {
      try {
        const sheet = await playApi.getCharacter(member.characterSheetId)
        return {
          characterSheetId: member.characterSheetId,
          name: sheet.name,
          controlMode: member.controlMode,
        } satisfies RuntimePartyCharacter
      } catch {
        return {
          characterSheetId: member.characterSheetId,
          name: '캐릭터',
          controlMode: member.controlMode,
        } satisfies RuntimePartyCharacter
      }
    })).then(characters => { if (active) setPartyCharacters(characters) })
    return () => { active = false }
  }, [playApi, session])

  useEffect(() => {
    if (!session?.adventureId) return
    refreshCombat(session.adventureId)
  }, [refreshCombat, session?.adventureId])

  useEffect(() => {
    if (!session?.adventureId || combatEventCursor === undefined || !combatApi.subscribeEvents) return
    const adventureId = session.adventureId
    return combatApi.subscribeEvents(adventureId, combatEventCursor, () => refreshCombat(adventureId), () => undefined)
  }, [combatApi, combatEventCursor, refreshCombat, session?.adventureId])

  useEffect(() => {
    if (!session?.scenarioPackageId || !setupApi.getScenarioPackage) {
      setHandouts([])
      setHandoutsLoading(false)
      setHandoutsMessage('')
      return
    }
    let active = true
    setHandouts([])
    setHandoutsLoading(true)
    setHandoutsMessage('')
    void setupApi.getScenarioPackage(session.scenarioPackageId).then(scenarioPackage => setupApi.getScenarioBundle(scenarioPackage.bundleId)).then(bundle => {
      if (!active) return
      setHandouts(bundle.documents.filter(document => document.role === 'HANDOUT').map(document => ({ knowledgeDocumentId: document.knowledgeDocumentId, originalFilename: document.originalFilename })))
    }).catch(error => {
      if (active) setHandoutsMessage(error instanceof Error ? error.message : '핸드아웃을 불러오지 못했습니다.')
    }).finally(() => {
      if (active) setHandoutsLoading(false)
    })
    return () => { active = false }
  }, [session, setupApi])

  if (!session) return <section className="workspace-page workspace-state" aria-busy="true"><p className="eyebrow">SESSION</p><h1>{message ? '세션을 열 수 없습니다' : '세션을 불러오는 중입니다'}</h1><p role={message ? 'alert' : 'status'}>{message || '잠시만 기다려 주세요.'}</p></section>
  if (!session.adventureId) return <section className="workspace-page workspace-state workspace-state-error"><p className="eyebrow">SESSION</p><h1>아직 시작되지 않은 세션입니다</h1><p>파티와 자료를 확인한 뒤 세션을 시작하세요.</p><a className="text-link" href={`#/sessions/${encodeURIComponent(sessionId)}`}>준비 화면으로 돌아가기</a></section>
  if (session.status === 'COMPLETED') {
    if (completionMessage) return <section className="workspace-page workspace-state workspace-state-error"><p className="eyebrow">ADVENTURE COMPLETE</p><h1>완료 기록을 불러오지 못했습니다</h1><p role="alert">{completionMessage}</p><a className="text-link" href={`#/adventures/${encodeURIComponent(session.adventureId)}?tab=sessions`}>모험 기록으로 돌아가기</a></section>
    if (!completionEntries) return <section className="workspace-page workspace-state" aria-busy="true"><p className="eyebrow">ADVENTURE COMPLETE</p><h1>모험 크레딧을 준비하고 있습니다</h1><p role="status">저장된 여정을 불러오는 중입니다.</p></section>
    return <AdventureCredits entries={completionEntries} party={partyCharacters} adventureId={session.adventureId} />
  }
  if (combatSnapshot && combatSnapshot.status !== 'ENDED') return <>
    <SpatialTurnRuntime adventureId={session.adventureId} playApi={playApi} combatSnapshot={combatSnapshot} />
    <CombatScreen
      snapshot={combatSnapshot}
      api={combatApi}
      onCommandCommitted={() => refreshCombat(session.adventureId!)}
      map={<CombatMapView adventureId={session.adventureId} api={playApi} refreshToken={mapRefreshToken} compact />}
    />
  </>
  return <SessionRuntime
    adventureId={session.adventureId}
    adventureApi={adventureApi}
    expectedVersion={undefined}
    playApi={playApi}
    mapRefreshToken={mapRefreshToken}
    onTurnCommitted={onTurnCommitted}
    sessionLabel={`Session · ${session.status === 'STARTED' ? '진행 중' : session.status}`}
    initialScene={session.runtimeConfiguration?.initialScene}
    partyCharacters={partyCharacters}
    handouts={handouts}
    handoutsLoading={handoutsLoading}
    handoutsMessage={handoutsMessage}
    getHandoutPreview={getHandoutPreview}
  >{combatFinalSummary && <section className="combat-final-summary" aria-labelledby="combat-final-summary-title"><p className="eyebrow">전투 종료</p><h2 id="combat-final-summary-title">전투 결과</h2><p>{combatFinalSummary.summary}</p><p>이 결과는 모험 기록에 반영되었습니다.</p></section>}</SessionRuntime>
}
