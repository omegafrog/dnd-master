import { useEffect, useState } from 'react'
import type { AdventureSessionApi, AdventureSessionView, AiCompanionCandidate, CharacterSheetSummary, GmProviderView } from './AdventureSessionApi'

type SessionApi = Pick<AdventureSessionApi, 'read' | 'listOwnedCharacters' | 'copyOwnedCharacter' | 'addMember' | 'removeMember' | 'start' | 'complete' | 'delete'> & Partial<Pick<AdventureSessionApi, 'generateAiCandidate' | 'adoptAiCandidate' | 'replaceMember' | 'readGmProvider' | 'switchGmProvider'>>

const sessionStatusLabel: Record<AdventureSessionView['status'], string> = {
  DRAFT: '준비 중', STARTING: '시작하는 중', STARTED: '진행 중', COMPLETED: '완료', DELETED: '삭제됨',
}

export function AdventureSessionPanel({ api, ownerPlayerId, sessionId }: { api: SessionApi; ownerPlayerId: string; sessionId: string }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [characters, setCharacters] = useState<CharacterSheetSummary[]>([])
  const [message, setMessage] = useState('')
  const [provider, setProvider] = useState<GmProviderView | null>(null)
  const [pendingEnd, setPendingEnd] = useState<'complete' | 'delete' | null>(null)
  const [providerForm, setProviderForm] = useState({ provider: 'ollama', model: 'qwen3:8b', reasoning: 'medium' })
  const [candidate, setCandidate] = useState<AiCompanionCandidate | null>(null)
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
  async function setControlMode(member: AdventureSessionView['party'][number], controlMode: 'DIRECT' | 'AGENT') {
    if (!session || frozen || member.controlMode === controlMode || !api.replaceMember) return
    try { setSession(await api.replaceMember(sessionId, session.version, member.characterSheetId, { ...member, controlMode })) }
    catch (error) { setMessage(error instanceof Error ? error.message : '조작 방식을 변경하지 못했습니다.') }
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
  const partyFull = session.party.length === session.characterLimit
  const orderedParty = [...session.party].sort((left, right) => Number(right.controlMode === 'DIRECT') - Number(left.controlMode === 'DIRECT'))
  const partyCharacter = (sheetId: string) => characters.find(character => character.characterSheetId === sheetId)
  const availableCharacters = characters.filter(character => !session.party.some(member => member.characterSheetId === character.characterSheetId))
  const requestCandidate = () => {
    if (!api.generateAiCandidate) return
    void api.generateAiCandidate(sessionId).then(setCandidate).catch(error => setMessage(error instanceof Error ? error.message : 'AI 후보를 만들지 못했습니다.'))
  }
  const adoptCandidate = () => {
    if (!candidate || !api.adoptAiCandidate) return
    void api.adoptAiCandidate(sessionId, session.version, candidate, 'AGENT')
      .then(next => { setSession(next); setCandidate(null) })
      .catch(error => setMessage(error instanceof Error ? error.message : 'AI 동료를 채택하지 못했습니다.'))
  }
  const startRuntime = () => {
    if (!partyFull || !session.runtimeConfiguration) return
    const adventureId = globalThis.crypto.randomUUID()
    void api.start(sessionId, session.version, adventureId).then(next => {
      setSession(next)
      window.location.hash = `#/adventures/${next.adventureId ?? adventureId}`
    }).catch(error => setMessage(error instanceof Error ? error.message : '시나리오 런타임을 시작하지 못했습니다.'))
  }
  return <section className="session-page" aria-labelledby="session-party-heading">
    <div className="page-heading party-heading">
      <div><p className="eyebrow">ADVENTURE ASSEMBLY</p><h1 id="session-party-heading">모험을 함께할 파티</h1><p>플레이어 캐릭터를 먼저 정하고, 남은 자리는 직접 만든 동료 또는 AI 제안으로 채우세요.</p></div>
      <span className="status-chip">{sessionStatusLabel[session.status]} · {session.party.length}/{session.characterLimit}명</span>
    </div>

    <ol className="party-progress" aria-label="모험 준비 상태">
      <li className="is-complete"><span>01</span><strong>시나리오</strong><small>준비됨</small></li>
      <li className={session.party.some(member => member.controlMode === 'DIRECT') ? 'is-complete' : 'is-current'}><span>02</span><strong>내 플레이 캐릭터</strong><small>{session.party.some(member => member.controlMode === 'DIRECT') ? '선택됨' : '필수'}</small></li>
      <li className={partyFull ? 'is-complete' : 'is-current'}><span>03</span><strong>파티 조립</strong><small>{session.party.length}/{session.characterLimit}명</small></li>
      <li className={partyFull && session.runtimeConfiguration ? 'is-current' : undefined}><span>04</span><strong>시나리오 런타임</strong><small>{partyFull && session.runtimeConfiguration ? '시작 가능' : '대기 중'}</small></li>
    </ol>

    <div className="party-workspace">
      <section className="party-board" aria-labelledby="party-board-heading">
        <div className="party-section-heading"><div><p className="eyebrow">YOUR TABLE</p><h2 id="party-board-heading">파티 조립 현황</h2></div><span>{session.characterLimit - session.party.length}자리 남음</span></div>
        <p className="party-capacity-note">이 모험은 총 {session.characterLimit}명으로 진행합니다. 첫 번째 캐릭터는 반드시 사용자가 직접 조작해야 합니다.</p>
        <ul className="party-slot-grid">
          {Array.from({ length: session.characterLimit }, (_, index) => {
            const member = orderedParty[index]
            const character = member && partyCharacter(member.characterSheetId)
            if (!member) return <li key={`empty-${index}`} className="party-slot party-slot-empty"><span className="party-slot-mark">+</span><strong>{index === 0 ? '내 플레이 캐릭터' : '동료 자리'}</strong><small>{index === 0 ? '직접 조작 캐릭터가 필요합니다' : '직접 만들거나 AI에게 제안받으세요'}</small></li>
            return <li key={member.characterSheetId} className={`party-slot ${member.controlMode === 'DIRECT' ? 'party-slot-direct' : 'party-slot-agent'}`}>
              <div className="party-member-card-head"><span className="party-avatar" aria-hidden="true">{character?.characterName?.slice(0, 1) ?? (member.controlMode === 'DIRECT' ? 'P' : 'AI')}</span><span className="party-control-badge">{member.controlMode === 'DIRECT' ? '직접 조작' : 'AI 조작'}</span></div>
              <strong>{character?.characterName ?? '동료 캐릭터'}</strong><small>{character ? `${character.race} · ${character.characterClass} · 레벨 ${character.level}` : '캐릭터 시트 연결됨'}</small>
              {!frozen && <div className="party-member-actions"><label>조작 방식<select aria-label={`${member.characterSheetId} 조작 방식`} value={member.controlMode} onChange={event => void setControlMode(member, event.currentTarget.value as 'DIRECT' | 'AGENT')}><option value="DIRECT">사용자 직접 조작</option><option value="AGENT">AI 조작</option></select></label><button type="button" className="party-remove-button" onClick={() => void api.removeMember(sessionId, session.version, member.characterSheetId).then(setSession).catch(error => setMessage(error instanceof Error ? error.message : '파티원을 제거하지 못했습니다.'))}>제거</button></div>}
            </li>
          })}
        </ul>
      </section>

      {!frozen && <aside className="party-side-panel">
        <section className="party-player-panel" aria-labelledby="player-character-heading"><div className="party-section-heading"><div><p className="eyebrow">PLAYER CHARACTER</p><h2 id="player-character-heading">내 플레이 캐릭터</h2></div></div><p>내가 직접 조작할 캐릭터는 반드시 한 명 이상 필요합니다.</p><button type="button" onClick={() => { window.location.hash = `#/sessions/${sessionId}/character` }} disabled={partyFull}>새 캐릭터 만들기</button>{availableCharacters.length > 0 ? <ul className="available-character-list" aria-label="내 캐릭터 목록">{availableCharacters.map(character => <li key={character.characterSheetId}><div><strong>{character.characterName}</strong><small>{character.race} · {character.characterClass} · Lv.{character.level}</small></div><button type="button" onClick={() => void addMember(character.characterSheetId)} disabled={partyFull}>{session.party.length === 0 ? '내 캐릭터로' : '파티에 추가'}</button></li>)}</ul> : <p className="empty-character-note">저장된 캐릭터가 없습니다. 새 시트를 만들어 시작하세요.</p>}</section>

        {session.party.length < session.characterLimit && <section className="ai-candidate-panel" aria-label="AI 동료 후보"><div className="party-section-heading"><div><p className="eyebrow">AI COMPANION</p><h2>AI 동료 제안</h2></div><span className="ai-spark" aria-hidden="true">✦</span></div><p>GM 설정의 모델이 현재 파티에 어울리는 동료를 제안합니다. 이름·종족·직업·요약을 확인한 뒤 채택하세요.</p>{candidate ? <article className="ai-candidate-card"><span className="party-control-badge">제안됨</span><h3>{candidate.name}</h3><p className="candidate-role">{candidate.race} · {candidate.characterClass}</p><p>{candidate.sheetSummary}</p><div>{api.adoptAiCandidate && <button type="button" onClick={adoptCandidate}>AI 동료로 채택</button>}<button type="button" onClick={requestCandidate}>다시 제안받기</button></div></article> : <button type="button" disabled={!api.generateAiCandidate} onClick={requestCandidate}>AI 동료 제안받기</button>}</section>}
      </aside>}
    </div>

    {session.status === 'DRAFT' && <div className="session-start-actions"><button type="button" onClick={startRuntime} disabled={!partyFull || !session.runtimeConfiguration}>시나리오 런타임 시작</button>{!partyFull && <p>파티 정원 {session.characterLimit}명에 맞춰야 시작할 수 있습니다.</p>}{partyFull && !session.runtimeConfiguration && <p>런타임 설정이 없어 시나리오를 시작할 수 없습니다.</p>}</div>}

    {provider && api.switchGmProvider && <details className="party-provider-settings"><summary>GM 연결 설정 <span>{provider.provider} · {provider.model}</span></summary><div><label>연결 방식<select aria-label="GM provider" value={providerForm.provider} onChange={event => { const value = event.currentTarget.value; setProviderForm(current => ({ ...current, provider: value })) }}><option value="codex-cli">Codex OAuth</option><option value="openai">OpenAI 호환</option></select></label><label>모델<input aria-label="GM model" value={providerForm.model} onChange={event => { const value = event.currentTarget.value; setProviderForm(current => ({ ...current, model: value })) }} /></label><label>Reasoning<select aria-label="GM reasoning" value={providerForm.reasoning} onChange={event => { const value = event.currentTarget.value; setProviderForm(current => ({ ...current, reasoning: value })) }}><option value="low">low</option><option value="medium">medium</option><option value="high">high</option></select></label><button type="button" onClick={() => void switchProvider()}>연결 변경</button></div></details>}
    {frozen && <p className="session-frozen-note">시작 후 파티와 제어 방식은 변경할 수 없습니다. 종료된 세션의 시트는 비활성화됩니다.</p>}
    {session.status === 'STARTED' && <div className="session-end-actions"><button type="button" disabled={pendingEnd !== null} onClick={() => setPendingEnd('complete')}>세션 완료</button><button type="button" disabled={pendingEnd !== null} onClick={() => setPendingEnd('delete')}>세션 삭제</button></div>}
    {pendingEnd && <div className="session-confirmation" role="alert"><p>현재 모험을 종료하면 이후 변경할 수 없습니다. 계속할까요?</p><button type="button" onClick={() => void finish(pendingEnd)}>종료 확인</button><button type="button" onClick={() => setPendingEnd(null)}>취소</button></div>}
    <p role="status">{message}</p>
  </section>
}
