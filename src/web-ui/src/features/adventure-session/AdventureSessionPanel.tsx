import { useEffect, useState } from 'react'
import type { AdventureSessionApi, AdventureSessionView, CharacterSheetSummary, GmProviderView } from './AdventureSessionApi'

type SessionApi = Pick<AdventureSessionApi, 'read' | 'listOwnedCharacters' | 'copyOwnedCharacter' | 'addMember' | 'removeMember' | 'complete' | 'delete'> & Partial<Pick<AdventureSessionApi, 'readGmProvider' | 'switchGmProvider'>>

export function AdventureSessionPanel({ api, ownerPlayerId, sessionId }: { api: SessionApi; ownerPlayerId: string; sessionId: string }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [characters, setCharacters] = useState<CharacterSheetSummary[]>([])
  const [message, setMessage] = useState('')
  const [provider, setProvider] = useState<GmProviderView | null>(null)
  const [pendingEnd, setPendingEnd] = useState<'complete' | 'delete' | null>(null)
  const [providerForm, setProviderForm] = useState({ provider: 'ollama', model: 'qwen3:8b', reasoning: 'medium' })
  const frozen = session?.status !== 'DRAFT'
  const load = () => void Promise.all([api.read(sessionId), api.listOwnedCharacters(ownerPlayerId), api.readGmProvider ? api.readGmProvider(sessionId) : Promise.resolve(null)]).then(([nextSession, ownedCharacters, nextProvider]) => { setSession(nextSession); setCharacters(ownedCharacters); if (nextProvider) { setProvider(nextProvider); setProviderForm({ provider: nextProvider.provider, model: nextProvider.model, reasoning: nextProvider.reasoning }) } }).catch(error => setMessage(error instanceof Error ? error.message : '세션을 불러오지 못했습니다.'))
  useEffect(load, [api, ownerPlayerId, sessionId])

  async function addMember(characterSheetId: string) {
    if (!session || frozen) return
    try {
      const copied = await api.copyOwnedCharacter(sessionId, characterSheetId, ownerPlayerId)
      setSession(await api.addMember(sessionId, session.version, { characterSheetId: copied.characterSheetId, controlMode: 'DIRECT', nameMutableAfterStart: false, raceMutableAfterStart: false, characterClassMutableAfterStart: false, backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false }))
    }
    catch (error) { setMessage(error instanceof Error ? error.message : '파티를 변경하지 못했습니다.') }
  }
  async function finish(action: 'complete' | 'delete') {
    if (!session || session.status !== 'STARTED') return
    try { setSession(await api[action](sessionId, session.version)); setPendingEnd(null); setMessage('세션이 종료되었습니다. 캐릭터 시트 정리를 요청했습니다.') }
    catch (error) { setMessage(error instanceof Error ? error.message : '세션을 종료하지 못했습니다.') }
  }
  async function switchProvider() {
    if (!session || !provider || !api.switchGmProvider) return
    try { setProvider(await api.switchGmProvider(sessionId, provider.version, providerForm)); setMessage('GM provider를 전환했습니다.') }
    catch (error) { setMessage(error instanceof Error ? error.message : 'GM provider를 전환하지 못했습니다.') }
  }

  if (!session) return <p role="status">{message || '세션 불러오는 중...'}</p>
  return <section className="session-page" aria-labelledby="session-party-heading">
    <div className="page-heading"><div><p className="eyebrow">ADVENTURE ASSEMBLY</p><h1 id="session-party-heading">모험 생성과 파티 구성</h1><p>스토리북에서 정한 파티 정원을 채우면 AI GM이 내부 모험 플랜을 준비하고 모험을 시작합니다.</p></div><span className="status-chip">{session.status} · 파티 {session.party.length}/{session.characterLimit}</span></div>
    <ul className="party-list">{session.party.map(member => <li key={member.characterSheetId}><span><strong>{member.characterSheetId}</strong><small>{member.controlMode}</small></span>{!frozen && <button type="button" onClick={() => void api.removeMember(sessionId, session.version, member.characterSheetId).then(setSession)}>제거</button>}</li>)}</ul>
    {provider && api.switchGmProvider && <section aria-labelledby="gm-provider-heading"><h2 id="gm-provider-heading">GM provider</h2><p>현재: {provider.provider} · {provider.model} · {provider.reasoning}</p><label>Provider<select aria-label="GM provider" value={providerForm.provider} onChange={event => { const value = event.currentTarget.value; setProviderForm(current => ({ ...current, provider: value })) }}><option value="ollama">ollama</option><option value="openai">openai</option></select></label><label>Model<input aria-label="GM model" value={providerForm.model} onChange={event => { const value = event.currentTarget.value; setProviderForm(current => ({ ...current, model: value })) }} /></label><label>Reasoning<select aria-label="GM reasoning" value={providerForm.reasoning} onChange={event => { const value = event.currentTarget.value; setProviderForm(current => ({ ...current, reasoning: value })) }}><option value="low">low</option><option value="medium">medium</option><option value="high">high</option></select></label><button type="button" onClick={() => void switchProvider()}>Provider 전환</button></section>}
    {!frozen && <div className="party-editor"><p className="party-capacity-note">이 모험은 캐릭터 {session.characterLimit}명으로 진행합니다. 캐릭터를 생성하면 파티에 자동으로 추가됩니다.</p><button type="button" onClick={() => { window.location.hash = `#/sessions/${sessionId}/character` }} disabled={session.party.length >= session.characterLimit}>새 내 캐릭터 만들기</button><h2>내 캐릭터</h2><ul aria-label="내 캐릭터 목록">{characters.filter(character => !session.party.some(member => member.characterSheetId === character.characterSheetId)).map(character => <li key={character.characterSheetId}><strong>{character.characterName}</strong><span>{character.race} · {character.characterClass} · 레벨 {character.level}</span><button type="button" onClick={() => void addMember(character.characterSheetId)} disabled={session.party.length >= session.characterLimit}>직접 조작으로 추가</button></li>)}</ul>{characters.length === 0 && <p>아직 저장된 캐릭터가 없습니다.</p>}</div>}
    {!frozen && <div className="session-primary-action"><button type="button" onClick={() => { window.location.hash = `#/sessions/${sessionId}/story-plan` }} disabled={session.party.length !== session.characterLimit || !session.runtimeConfiguration}>모험 계획 만들기</button>{session.party.length !== session.characterLimit && <p>파티 정원 {session.characterLimit}명에 맞춰야 합니다.</p>}{!session.runtimeConfiguration && <p>런타임 설정이 없어 계획을 만들 수 없습니다.</p>}</div>}
    {frozen && <p>시작 후 파티와 제어 방식은 변경할 수 없습니다. 종료된 세션의 시트는 비활성화됩니다.</p>}
    {session.status === 'STARTED' && <div><button type="button" onClick={() => setPendingEnd('complete')}>세션 완료</button><button type="button" onClick={() => setPendingEnd('delete')}>세션 삭제</button></div>}
    {pendingEnd && <div role="alert"><p>현재 모험을 종료하면 이후 변경할 수 없습니다. 계속할까요?</p><button type="button" onClick={() => void finish(pendingEnd)}>종료 확인</button><button type="button" onClick={() => setPendingEnd(null)}>취소</button></div>}
    <p role="status">{message}</p>
  </section>
}
