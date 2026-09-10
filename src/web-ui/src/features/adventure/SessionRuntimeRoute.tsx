import { useEffect, useState } from 'react'
import type { AdventureApi } from './AdventureApi'
import { SessionRuntime, type RuntimePartyCharacter } from './SessionRuntime'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'

export function SessionRuntimeRoute({ sessionId, sessionApi, adventureApi, playApi }: { sessionId: string; sessionApi: AdventureSessionApi; adventureApi: AdventureApi; playApi: AdventurePlayApi }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [partyCharacters, setPartyCharacters] = useState<RuntimePartyCharacter[]>([])
  const [message, setMessage] = useState('')

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

  if (!session) return <section className="workspace-page workspace-state" aria-busy="true"><p className="eyebrow">SESSION</p><h1>{message ? '세션을 열 수 없습니다' : '세션을 불러오는 중입니다'}</h1><p role={message ? 'alert' : 'status'}>{message || '잠시만 기다려 주세요.'}</p></section>
  if (!session.adventureId) return <section className="workspace-page workspace-state workspace-state-error"><p className="eyebrow">SESSION</p><h1>아직 시작되지 않은 세션입니다</h1><p>파티와 자료를 확인한 뒤 세션을 시작하세요.</p><a className="text-link" href={`#/sessions/${encodeURIComponent(sessionId)}`}>준비 화면으로 돌아가기</a></section>
  return <SessionRuntime
    adventureId={session.adventureId}
    adventureApi={adventureApi}
    expectedVersion={undefined}
    playApi={playApi}
    sessionLabel={`Session · ${session.status === 'STARTED' ? '진행 중' : session.status}`}
    initialScene={session.runtimeConfiguration?.initialScene}
    partyCharacters={partyCharacters}
  />
}
