import { useState, type ReactNode } from 'react'
import { BookOpen, ChevronRight, Dice5, FileText, Map, MoreHorizontal, NotebookPen, Shield, Users } from 'lucide-react'
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
      <a className="session-runtime-back" href={`#/adventures/${encodeURIComponent(adventureId)}?tab=sessions`}><ChevronRight size={15} aria-hidden="true" className="rotate-180" />세션 기록</a>
      <div><p className="eyebrow">세션 실행</p><h1 id="session-runtime-title">지금 모험을 플레이하고 있습니다</h1><p>현재 상황과 파티 상태를 확인하며 다음 행동을 선택하세요.</p></div>
      <div className="session-runtime-header-actions"><span className="runtime-live-state"><span aria-hidden="true" />진행 중</span><Button variant="outline" onClick={() => window.location.hash = `#/adventures/${encodeURIComponent(adventureId)}?tab=sessions`}>세션 종료</Button></div>
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
  return <aside className="runtime-party-panel" aria-label="파티 상태"><div className="runtime-panel-heading"><div><p className="eyebrow">파티</p><h2>함께하는 캐릭터</h2></div><Users size={18} aria-hidden="true" /></div><Separator />{participants.length > 0 ? <ul className="runtime-party-list">{participants.map((participant, index) => <li key={participant.participantId} className={participant.displayName === currentParticipant ? 'runtime-party-current' : undefined}><span className="runtime-portrait" aria-hidden="true">{participant.displayName.slice(0, 1)}</span><span><strong>{participant.displayName || `캐릭터 ${index + 1}`}</strong><small>직접 조작 · {participant.initiative} 우선권</small></span><Shield size={15} aria-label="상태 확인" /></li>)}</ul> : <div className="runtime-empty-panel"><Users size={20} aria-hidden="true" /><p>파티 상태는 현재 세션 기록에서 확인됩니다.</p></div>}</aside>
}

function ContextPanel({ combatSnapshot, mapOpen, setMapOpen, noteOpen, setNoteOpen, map }: { combatSnapshot?: CombatSnapshot | null; mapOpen: boolean; setMapOpen: (value: boolean) => void; noteOpen: boolean; setNoteOpen: (value: boolean) => void; map: ReactNode }) {
  return <>
    <div className="runtime-panel-heading"><div><p className="eyebrow">현재 상황</p><h2>지금 확인할 내용</h2></div><MoreHorizontal size={18} aria-hidden="true" /></div>
    <Separator />
    <dl className="runtime-context-list"><div><dt>진행 상태</dt><dd>{combatSnapshot ? `${combatSnapshot.round}라운드` : '이야기 진행 중'}</dd></div><div><dt>현재 차례</dt><dd>{combatSnapshot ? (combatSnapshot.currentParticipantId ? '행동을 선택하세요' : '게임 마스터') : '게임 마스터'}</dd></div><div><dt>전투</dt><dd>{combatSnapshot ? '진행 중' : '없음'}</dd></div></dl>
    <Separator />
    <div className="runtime-quick-actions"><p className="eyebrow">빠른 작업</p><Button variant="ghost" onClick={() => setMapOpen(!mapOpen)}><Map size={15} aria-hidden="true" />{mapOpen ? '지도 닫기' : '지도 열기'}</Button><Button variant="ghost" onClick={() => setNoteOpen(!noteOpen)}><NotebookPen size={15} aria-hidden="true" />노트 {noteOpen ? '닫기' : '추가'}</Button><Button variant="ghost"><Dice5 size={15} aria-hidden="true" />주사위 굴리기</Button><Button variant="ghost"><MoreHorizontal size={15} aria-hidden="true" />더보기</Button></div>
    {noteOpen && <div className="runtime-note-box"><label htmlFor="runtime-note">세션 노트</label><textarea id="runtime-note" placeholder="기억할 내용을 적으세요." /><Button variant="outline" onClick={() => setNoteOpen(false)}>노트 닫기</Button></div>}
    {map && <div className="runtime-map-drawer"><div className="runtime-map-heading"><BookOpen size={15} aria-hidden="true" /><strong>현재 지도</strong></div>{map}</div>}
    <div className="runtime-context-footer"><FileText size={14} aria-hidden="true" /><span>세션 기록은 모험 기록에서 다시 확인할 수 있습니다.</span></div>
  </>
}
