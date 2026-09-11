import { useEffect, useState, type ReactNode } from 'react'
import { BookOpen, ChevronLeft, ChevronRight, Dice5, FileText, Map, MoreHorizontal, NotebookPen, Settings, Shield, Users } from 'lucide-react'
import { Button } from '../../components/ui/button'
import { Separator } from '../../components/ui/separator'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '../../components/ui/sheet'
import type { AdventureApi } from './AdventureApi'
import { AdventureStream } from './AdventureStream'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import type { CombatSnapshot } from '../combat/CombatApi'
import { CombatMapView } from '../combat-map/CombatMapView'
import type { SourcePreviewView } from '../rulebooks/SetupApi'

export type RuntimePartyCharacter = {
  characterSheetId: string
  name: string
  race?: string
  characterClass?: string
  level?: number
  controlMode?: 'DIRECT' | 'AGENT'
}

export type RuntimeHandout = {
  knowledgeDocumentId: string
  originalFilename: string
}

export type RuntimeHandoutPreviewLoader = (knowledgeDocumentId: string) => Promise<SourcePreviewView>

export function SessionRuntime({ adventureId, adventureApi, expectedVersion, playApi, combatSnapshot, mapRefreshToken, onTurnCommitted, adventureTitle, sessionLabel, initialScene, partyCharacters = [], handouts = [], handoutsLoading = false, handoutsMessage = '', getHandoutPreview }: { adventureId: string; adventureApi: AdventureApi; expectedVersion?: number | null; playApi: AdventurePlayApi; combatSnapshot?: CombatSnapshot | null; mapRefreshToken?: number; onTurnCommitted?: () => void; adventureTitle?: string; sessionLabel?: string; initialScene?: string | null; partyCharacters?: RuntimePartyCharacter[]; handouts?: RuntimeHandout[]; handoutsLoading?: boolean; handoutsMessage?: string; getHandoutPreview?: RuntimeHandoutPreviewLoader }) {
  const [mapOpen, setMapOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const [partyOpen, setPartyOpen] = useState(true)
  const current = combatSnapshot?.initiative.find(item => item.participantId === combatSnapshot.currentParticipantId)

  useEffect(() => {
    if (mapRefreshToken === undefined) return
    let active = true
    void playApi.getCombatMap(adventureId).then(map => {
      if (active && map.status === 'authoritative-map' && map.mapId) setMapOpen(true)
    }).catch(() => undefined)
    return () => { active = false }
  }, [adventureId, mapRefreshToken, playApi])

  return <section className="session-runtime" aria-labelledby="session-runtime-title">
    <header className="session-runtime-header">
      <a className="session-runtime-exit" href={`#/adventures/${encodeURIComponent(adventureId)}?tab=sessions`}><ChevronLeft size={16} aria-hidden="true" />세션 종료</a>
      <div className="session-runtime-title-block"><p className="eyebrow">SESSION</p><h1 id="session-runtime-title">{adventureTitle || '모험 세션'}</h1><p>{sessionLabel || '플레이 기록'}</p></div>
      <Button className="session-runtime-settings" variant="ghost" size="icon" aria-label="세션 설정"><Settings size={17} aria-hidden="true" /></Button>
    </header>
    <div className={`session-runtime-columns${partyOpen ? '' : ' runtime-party-collapsed'}`}>
      <PartyPanel snapshot={combatSnapshot} currentParticipant={current?.displayName} characters={partyCharacters} open={partyOpen} onToggle={() => setPartyOpen(value => !value)} />
      <main className="runtime-feed-panel" aria-label="게임 기록"><AdventureStream adventureId={adventureId} api={adventureApi} expectedVersion={expectedVersion} onTurnCommitted={onTurnCommitted} /></main>
      <aside className="runtime-context-panel" aria-label="현재 상황">
        <ContextPanel initialScene={initialScene} combatSnapshot={combatSnapshot} mapOpen={mapOpen} setMapOpen={setMapOpen} noteOpen={noteOpen} setNoteOpen={setNoteOpen} map={mapOpen ? <CombatMapView adventureId={adventureId} api={playApi} refreshToken={mapRefreshToken} compact /> : null} handouts={handouts} handoutsLoading={handoutsLoading} handoutsMessage={handoutsMessage} getHandoutPreview={getHandoutPreview} />
      </aside>
    </div>
  </section>
}

function PartyPanel({ snapshot, currentParticipant, characters, open, onToggle }: { snapshot?: CombatSnapshot | null; currentParticipant?: string; characters: RuntimePartyCharacter[]; open: boolean; onToggle: () => void }) {
  const combatPlayers = snapshot?.initiative.filter(item => item.controller === 'PLAYER') ?? []
  const participants = characters.length > 0
    ? characters.map(character => {
      const combat = combatPlayers.find(item => item.displayName === character.name || item.participantId === character.characterSheetId)
      return {
        id: character.characterSheetId,
        name: character.name,
        detail: [character.characterClass, character.level ? `Lv.${character.level}` : null].filter(Boolean).join(' · ') || character.race || (character.controlMode === 'AGENT' ? 'AI 동료' : '플레이어'),
        condition: combat?.publicCondition,
        initiative: combat?.initiative,
        current: combat?.displayName === currentParticipant,
      }
    })
    : combatPlayers.map((participant, index) => ({
      id: participant.participantId,
      name: participant.displayName || `캐릭터 ${index + 1}`,
      detail: '플레이어',
      condition: participant.publicCondition,
      initiative: participant.initiative,
      current: participant.displayName === currentParticipant,
    }))

  return <aside className={`runtime-party-panel${open ? '' : ' runtime-party-panel-collapsed'}`} aria-label="플레이 캐릭터 패널">
    <div className="runtime-panel-heading">{open && <div><p className="eyebrow">PARTY</p><h2>파티</h2></div>}<Button variant="ghost" size="icon" className="runtime-panel-toggle" aria-label={open ? '플레이 캐릭터 패널 접기' : '플레이 캐릭터 패널 펼치기'} aria-expanded={open} onClick={onToggle}>{open ? <ChevronLeft size={17} aria-hidden="true" /> : <ChevronRight size={17} aria-hidden="true" />}</Button></div>
    {open && <>
      <Separator />
      {participants.length > 0 ? <ul className="runtime-party-list">{participants.map(participant => <li key={participant.id} className={participant.current ? 'runtime-party-current' : undefined}>
      <span className="runtime-portrait" aria-hidden="true">{participant.name.slice(0, 1)}</span>
      <span><strong>{participant.name}</strong><small>{participant.detail}{participant.condition ? ` · ${participant.condition}` : ''}{participant.initiative ? ` · INIT ${participant.initiative}` : ''}</small></span>
      <Shield size={15} aria-label="상태 확인" />
      </li>)}</ul> : <div className="runtime-empty-panel"><Users size={20} aria-hidden="true" /><p>세션의 파티 정보가 여기에 표시됩니다.</p></div>}
    </>}
  </aside>
}

function ContextPanel({ initialScene, combatSnapshot, mapOpen, setMapOpen, noteOpen, setNoteOpen, map, handouts, handoutsLoading, handoutsMessage, getHandoutPreview }: { initialScene?: string | null; combatSnapshot?: CombatSnapshot | null; mapOpen: boolean; setMapOpen: (value: boolean) => void; noteOpen: boolean; setNoteOpen: (value: boolean) => void; map: ReactNode; handouts: RuntimeHandout[]; handoutsLoading: boolean; handoutsMessage: string; getHandoutPreview?: RuntimeHandoutPreviewLoader }) {
  const [selectedHandout, setSelectedHandout] = useState<RuntimeHandout | null>(null)
  const [handoutPreview, setHandoutPreview] = useState<SourcePreviewView | null>(null)
  const [handoutPreviewLoading, setHandoutPreviewLoading] = useState(false)
  const [handoutPreviewMessage, setHandoutPreviewMessage] = useState('')

  useEffect(() => {
    if (!selectedHandout) return
    if (!getHandoutPreview) {
      setHandoutPreviewMessage('핸드아웃 내용을 불러올 수 없습니다.')
      return
    }
    let active = true
    setHandoutPreview(null)
    setHandoutPreviewMessage('')
    setHandoutPreviewLoading(true)
    void getHandoutPreview(selectedHandout.knowledgeDocumentId).then(preview => {
      if (active) setHandoutPreview(preview)
    }).catch(error => {
      if (active) setHandoutPreviewMessage(error instanceof Error ? error.message : '핸드아웃 내용을 불러오지 못했습니다.')
    }).finally(() => {
      if (active) setHandoutPreviewLoading(false)
    })
    return () => { active = false }
  }, [getHandoutPreview, selectedHandout])

  return <>
    <div className="runtime-panel-heading"><div><p className="eyebrow">CURRENT</p><h2>현재 상황</h2></div><MoreHorizontal size={17} aria-hidden="true" /></div>
    <Separator />
    <dl className="runtime-context-list">
      <div><dt>현재 위치</dt><dd>{initialScene || '현재 장면'}</dd></div>
      <div><dt>진행</dt><dd>{combatSnapshot ? `Round ${combatSnapshot.round}` : '탐색'}</dd></div>
      <div><dt>현재 차례</dt><dd>{combatSnapshot?.currentParticipantId ? '플레이어' : '게임 마스터'}</dd></div>
      <div><dt>전투</dt><dd>{combatSnapshot ? '진행 중' : '없음'}</dd></div>
    </dl>
    <Separator />
    <section className="runtime-handout-panel" aria-labelledby="runtime-handout-title">
      <div className="runtime-handout-heading"><div><p className="eyebrow">자료</p><h2 id="runtime-handout-title">핸드아웃</h2></div><span>{handouts.length}개</span></div>
      {handoutsLoading ? <p className="runtime-handout-empty" role="status">핸드아웃을 불러오는 중입니다.</p>
        : handoutsMessage ? <p className="runtime-handout-empty" role="alert">{handoutsMessage}</p>
          : handouts.length > 0 ? <ul className="runtime-handout-list">{handouts.map(handout => <li key={handout.knowledgeDocumentId}><Button type="button" variant="ghost" onClick={() => setSelectedHandout(handout)} aria-label={`${handout.originalFilename} 열기`}><FileText size={15} aria-hidden="true" /><span>{handout.originalFilename}</span><ChevronRight size={14} aria-hidden="true" /></Button></li>)}</ul>
            : <p className="runtime-handout-empty">게임 중에 볼 수 있는 핸드아웃이 없습니다.</p>}
    </section>
    <div className="runtime-quick-actions"><p className="eyebrow">QUICK ACTIONS</p><Button variant="ghost" onClick={() => setMapOpen(!mapOpen)}><Map size={15} aria-hidden="true" />{mapOpen ? '지도 닫기' : '지도 열기'}</Button><Button variant="ghost" onClick={() => setNoteOpen(!noteOpen)}><NotebookPen size={15} aria-hidden="true" />노트 {noteOpen ? '닫기' : '추가'}</Button><Button variant="ghost"><Dice5 size={15} aria-hidden="true" />주사위 굴리기</Button><Button variant="ghost"><MoreHorizontal size={15} aria-hidden="true" />더보기</Button></div>
    {noteOpen && <div className="runtime-note-box"><label htmlFor="runtime-note">세션 노트</label><textarea id="runtime-note" placeholder="기억할 내용을 적으세요." /><Button variant="outline" onClick={() => setNoteOpen(false)}>노트 닫기</Button></div>}
    {map && <div className="runtime-map-drawer"><div className="runtime-map-heading"><BookOpen size={15} aria-hidden="true" /><strong>현재 지도</strong></div>{map}</div>}
    <div className="runtime-context-footer"><FileText size={14} aria-hidden="true" /><span>세션 종료 후 기록은 모험의 세션 기록에서 이어집니다.</span></div>
    <Sheet open={selectedHandout !== null} onOpenChange={open => { if (!open) setSelectedHandout(null) }}><SheetContent className="runtime-handout-sheet"><SheetHeader><SheetTitle>{selectedHandout?.originalFilename ?? '핸드아웃'}</SheetTitle></SheetHeader>{handoutPreviewLoading && <p role="status">핸드아웃 내용을 불러오는 중입니다.</p>}{handoutPreviewMessage && <p role="alert">{handoutPreviewMessage}</p>}{handoutPreview && <article className="runtime-handout-preview"><p className="eyebrow">핸드아웃 내용</p><pre>{handoutPreview.content || '표시할 본문이 없습니다.'}</pre></article>}</SheetContent></Sheet>
  </>
}
