import { useCallback, useEffect, useState } from 'react'
import type { AdventureApi } from './AdventureApi'
import { SessionRuntime, type RuntimePartyCharacter } from './SessionRuntime'
import type { RuntimeHandout } from './SessionRuntime'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import type { SetupApi } from '../rulebooks/SetupApi'

export function SessionRuntimeRoute({ sessionId, sessionApi, adventureApi, playApi, setupApi }: { sessionId: string; sessionApi: AdventureSessionApi; adventureApi: AdventureApi; playApi: AdventurePlayApi; setupApi: SetupApi }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [mapRefreshToken, setMapRefreshToken] = useState(0)
  const [partyCharacters, setPartyCharacters] = useState<RuntimePartyCharacter[]>([])
  const [handouts, setHandouts] = useState<RuntimeHandout[]>([])
  const [handoutsLoading, setHandoutsLoading] = useState(false)
  const [handoutsMessage, setHandoutsMessage] = useState('')
  const [message, setMessage] = useState('')
  const getHandoutPreview = useCallback((knowledgeDocumentId: string) => setupApi.getSourcePreview(knowledgeDocumentId), [setupApi])
  const onTurnCommitted = useCallback(() => setMapRefreshToken(current => current + 1), [])

  useEffect(() => {
    let active = true
    setMessage('')
    void sessionApi.read(sessionId).then(next => { if (active) setSession(next) }).catch(error => { if (active) setMessage(error instanceof Error ? error.message : '세션을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [sessionApi, sessionId])

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
  />
}
