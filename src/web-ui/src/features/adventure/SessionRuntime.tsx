import { useState, type ReactNode } from 'react'
import { BookOpen, ChevronLeft, Dice5, FileText, Map, MoreHorizontal, NotebookPen, Settings, Shield, Users } from 'lucide-react'
import { Button } from '../../components/ui/button'
import { Separator } from '../../components/ui/separator'
import type { AdventureApi } from './AdventureApi'
import { AdventureStream } from './AdventureStream'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import type { CombatSnapshot } from '../combat/CombatApi'
import { CombatMapView } from '../combat-map/CombatMapView'

export function SessionRuntime({ adventureId, adventureApi, expectedVersion, playApi, combatSnapshot, mapRefreshToken, onTurnCommitted }: { adventureId: string; adventureApi: AdventureApi; expectedVersion?: number | null; playApi: AdventurePlayApi; combatSnapshot?: CombatSnapshot | null; mapRefreshToken?: number; onTurnCommitted?: () => void }) {
  const [mapOpen, setMapOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const current = combatSnapshot?.initiative.find(item => item.participantId === combatSnapshot.currentParticipantId)

  return <section className="session-runtime" aria-labelledby="session-runtime-title">
    <header className="session-runtime-header">
      <a className="session-runtime-exit" href={`#/adventures/${encodeURIComponent(adventureId)}?tab=sessions`}><ChevronLeft size={16} aria-hidden="true" />세션 종료</a>
      <div className="session-runtime-title-block"><p className="eyebrow">SESSION</p><h1 id="session-runtime-title">모험 세션</h1><p>플레이 기록</p></div>
      <Button className="session-runtime-settings" variant="ghost" size="icon" aria-label="세션 설정"><Settings size={17} aria-hidden="true" /></Button>
    </header>
    <div className="session-runtime-columns">
      <PartyPanel snapshot={combatSnapshot} currentParticipant={current?.displayName} />
      <main className="runtime-feed-panel" aria-label="게임 기록"><AdventureStream adventureId={adventureId} api={adventureApi} expectedVersion={expectedVersion} onTurnCommitted={onTurnCommitted} /></main>
      <aside className="runtime-context-panel" aria-label="현재 상황">
        <ContextPanel combatSnapshot={combatSnapshot} mapOpen={mapOpen} setMapOpen={setMapOpen} noteOpen={noteOpen} setNoteOpen={setNoteOpen} map={mapOpen ? <CombatMapView adventureId={adventureId} api={playApi} refreshToken={mapRefreshToken} compact /> : null} />
      </aside>
    </div>
  </section>
}

function PartyPanel({ snapshot, currentParticipant }: { snapshot?: CombatSnapshot | null; currentParticipant?: string }) {
  const participants = snapshot?.initiative.filter(item => item.controller === 'PLAYER') ?? []
  return <aside className="runtime-party-panel" aria-label="파티 상태">
    <div className="runtime-panel-heading"><div><p className="eyebrow">PARTY</p><h2>파티</h2></div><Users size={17} aria-hidden="true" /></div>
    <Separator />
    {participants.length > 0 ? <ul className="runtime-party-list">{participants.map((participant, index) => <li key={participant.participantId} className={participant.displayName === currentParticipant ? 'runtime-party-current' : undefined}>
      <span className="runtime-portrait" aria-hidden="true">{participant.displayName.slice(0, 1)}</span>
      <span><strong>{participant.displayName || `캐릭터 ${index + 1}`}</strong><small>{participant.publicCondition || '상태 이상 없음'}{participant.initiative ? ` · INIT ${participant.initiative}` : ''}</small></span>
      <Shield size={15} aria-label="상태 확인" />
    </li>)}</ul> : <div className="runtime-empty-panel"><Users size={20} aria-hidden="true" /><p>세션의 파티 정보가 여기에 표시됩니다.</p></div>}
  </aside>
}

function ContextPanel({ combatSnapshot, mapOpen, setMapOpen, noteOpen, setNoteOpen, map }: { combatSnapshot?: CombatSnapshot | null; mapOpen: boolean; setMapOpen: (value: boolean) => void; noteOpen: boolean; setNoteOpen: (value: boolean) => void; map: ReactNode }) {
  return <>
    <div className="runtime-panel-heading"><div><p className="eyebrow">CURRENT</p><h2>현재 상황</h2></div><MoreHorizontal size={17} aria-hidden="true" /></div>
    <Separator />
    <dl className="runtime-context-list">
      <div><dt>진행</dt><dd>{combatSnapshot ? `Round ${combatSnapshot.round}` : '탐색'}</dd></div>
      <div><dt>현재 차례</dt><dd>{combatSnapshot?.currentParticipantId ? '플레이어' : '게임 마스터'}</dd></div>
      <div><dt>전투</dt><dd>{combatSnapshot ? '진행 중' : '없음'}</dd></div>
    </dl>
    <Separator />
    <div className="runtime-quick-actions"><p className="eyebrow">QUICK ACTIONS</p><Button variant="ghost" onClick={() => setMapOpen(!mapOpen)}><Map size={15} aria-hidden="true" />{mapOpen ? '지도 닫기' : '지도 열기'}</Button><Button variant="ghost" onClick={() => setNoteOpen(!noteOpen)}><NotebookPen size={15} aria-hidden="true" />노트 {noteOpen ? '닫기' : '추가'}</Button><Button variant="ghost"><Dice5 size={15} aria-hidden="true" />주사위 굴리기</Button><Button variant="ghost"><MoreHorizontal size={15} aria-hidden="true" />더보기</Button></div>
    {noteOpen && <div className="runtime-note-box"><label htmlFor="runtime-note">세션 노트</label><textarea id="runtime-note" placeholder="기억할 내용을 적으세요." /><Button variant="outline" onClick={() => setNoteOpen(false)}>노트 닫기</Button></div>}
    {map && <div className="runtime-map-drawer"><div className="runtime-map-heading"><BookOpen size={15} aria-hidden="true" /><strong>현재 지도</strong></div>{map}</div>}
    <div className="runtime-context-footer"><FileText size={14} aria-hidden="true" /><span>세션 종료 후 기록은 모험의 세션 기록에서 이어집니다.</span></div>
  </>
}
