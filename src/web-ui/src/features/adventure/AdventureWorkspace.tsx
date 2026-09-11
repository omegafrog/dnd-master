import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, CalendarDays, Check, ChevronRight, FileText, Play, Plus, ScrollText, Users } from 'lucide-react'
import { MaterialRow, MaterialStatus, materialStatus } from '../../components/adventure/material-row'
import { Button } from '../../components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog'
import { Separator } from '../../components/ui/separator'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '../../components/ui/sheet'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../components/ui/tabs'
import type { KnowledgeDocumentView, ScenarioBundleRole, ScenarioBundleView, SetupApi } from '../rulebooks/SetupApi'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { AdventurePlayApi, SavedAdventure, SessionKnowledgeSet } from '../saved-adventures/AdventurePlayApi'

type WorkspaceTab = 'materials' | 'review' | 'characters' | 'sessions'

const tabLabels: Array<{ value: WorkspaceTab; label: string }> = [
  { value: 'materials', label: '자료' },
  { value: 'review', label: '검토' },
  { value: 'characters', label: '캐릭터' },
  { value: 'sessions', label: '세션 기록' },
]

const sessionStatusLabel: Record<AdventureSessionView['status'], string> = {
  DRAFT: '준비 중',
  STARTING: '시작하는 중',
  STARTED: '진행 중',
  COMPLETED: '완료',
  DELETED: '삭제됨',
}

type WorkspaceSessionApi = Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage' | 'read'>

export function AdventureWorkspace({ adventureId, activeTab, playApi, setupApi, sessionApi, playerId }: { adventureId: string; activeTab: WorkspaceTab; playApi: AdventurePlayApi; setupApi: SetupApi; sessionApi: WorkspaceSessionApi; playerId: string }) {
  const [adventure, setAdventure] = useState<SavedAdventure | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [knowledgeSet, setKnowledgeSet] = useState<SessionKnowledgeSet | null>(null)
  const [bundle, setBundle] = useState<ScenarioBundleView | null>(null)
  const [rolesByDocument, setRolesByDocument] = useState<Record<string, ScenarioBundleRole>>({})
  const [savedRolesByDocument, setSavedRolesByDocument] = useState<Record<string, ScenarioBundleRole>>({})
  const [scenarioPackageId, setScenarioPackageId] = useState<string | null>(null)
  const [sessions, setSessions] = useState<AdventureSessionView[]>([])
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [startOpen, setStartOpen] = useState(false)
  const [characterOpen, setCharacterOpen] = useState(false)
  const [creatingSession, setCreatingSession] = useState(false)
  const [savingMaterialRoles, setSavingMaterialRoles] = useState(false)

  useEffect(() => {
    let active = true
    setLoading(true)
    setMessage('')
    void Promise.all([
      playApi.listSaved(playerId),
      setupApi.listKnowledgeDocuments(playerId),
      playApi.getSessionKnowledgeSet(adventureId),
      setupApi.getRuntimeBinding?.(adventureId, playerId).catch(() => null) ?? Promise.resolve(null),
    ]).then(async ([adventures, nextDocuments, nextKnowledgeSet, runtimeBinding]) => {
      const fallbackSession = !runtimeBinding && nextKnowledgeSet.sessionId
        ? await sessionApi.read(nextKnowledgeSet.sessionId).catch(() => null)
        : null
      const nextScenarioPackageId = runtimeBinding?.scenarioPackageId ?? fallbackSession?.scenarioPackageId ?? null
      const [nextSessions, nextPackage] = nextScenarioPackageId
        ? await Promise.all([
          sessionApi.listByScenarioPackage(nextScenarioPackageId),
          setupApi.getScenarioPackage?.(nextScenarioPackageId).catch(() => null) ?? Promise.resolve(null),
        ])
        : [[], null]
      const nextBundle = nextPackage
        ? await setupApi.getScenarioBundle(nextPackage.bundleId).catch(() => null)
        : null
      const nextRoles = Object.fromEntries(nextBundle?.documents.map(document => [document.knowledgeDocumentId, document.role]) ?? [])
      if (!active) return
      setBundle(nextBundle)
      setRolesByDocument(nextRoles)
      setSavedRolesByDocument(nextRoles)
      setScenarioPackageId(nextScenarioPackageId)
      setSessions(nextSessions)
      setAdventure(adventures.find(item => item.id === adventureId) ?? null)
      setDocuments(nextDocuments)
      setKnowledgeSet(nextKnowledgeSet)
    }).catch(error => {
      if (active) setMessage(error instanceof Error ? error.message : '모험 자료를 불러오지 못했습니다.')
    }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [adventureId, playApi, playerId, sessionApi, setupApi])

  const reviewDocuments = useMemo(() => documents.filter(document => ['review', 'failed'].includes(materialStatus(document).kind)), [documents])
  const title = adventure?.title ?? '이름 없는 모험'
  const readyCount = documents.filter(document => materialStatus(document).kind === 'ready').length
  const status = reviewDocuments.length > 0 ? '확인 필요' : documents.length > 0 && readyCount === documents.length ? '준비 완료' : '준비 중'
  const partySessionId = sessions.find(session => session.status === 'DRAFT' || session.status === 'STARTING')?.sessionId
    ?? sessions.find(session => session.status === 'STARTED')?.sessionId
    ?? knowledgeSet?.sessionId

  function changeTab(tab: WorkspaceTab) {
    window.location.hash = `#/adventures/${encodeURIComponent(adventureId)}?tab=${tab}`
  }

  function openSession(session: AdventureSessionView) {
    const path = session.status === 'STARTED' && session.adventureId
      ? `#/sessions/${session.sessionId}?mode=play`
      : session.status === 'COMPLETED' || session.status === 'DELETED'
        ? `#/sessions/${session.sessionId}`
        : `#/sessions/${session.sessionId}/party`
    window.location.hash = path
  }

  async function createSession() {
    if (!scenarioPackageId || !setupApi.getPlayPreparation) return
    setCreatingSession(true)
    setMessage('')
    try {
      const getPlayPreparation = setupApi.getPlayPreparation
      const preparation = await getPlayPreparation(scenarioPackageId)
      const blueprint = preparation.characterCreationBlueprint
      if (blueprint.status !== 'PUBLISHED' || blueprint.revision == null) {
        throw new Error('캐릭터 생성 설정을 먼저 검토하고 게시해 주세요.')
      }
      const session = await sessionApi.create({
        scenarioPackageId,
        blueprintId: scenarioPackageId,
        blueprintRevision: blueprint.revision,
        partySize: preparation.characterLimit.maximumCharacters,
      })
      setSessions(current => [session, ...current.filter(item => item.sessionId !== session.sessionId)])
      setStartOpen(false)
      window.location.hash = `#/sessions/${session.sessionId}/party`
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 세션을 만들지 못했습니다.')
    } finally {
      setCreatingSession(false)
    }
  }

  async function saveMaterialRoles() {
    if (!bundle || !setupApi.reviseScenarioBundle) return
    setSavingMaterialRoles(true)
    setMessage('')
    try {
      const saved = await setupApi.reviseScenarioBundle(
        bundle.bundleId,
        playerId,
        bundle.documents.map(document => ({
          knowledgeDocumentId: document.knowledgeDocumentId,
          role: rolesByDocument[document.knowledgeDocumentId] ?? document.role,
        })),
        {
          name: bundle.name ?? title,
          rulebookEdition: bundle.rulebookEdition ?? 'DND_5E_2014',
        },
      )
      const nextRoles = Object.fromEntries(saved.documents.map(document => [document.knowledgeDocumentId, document.role]))
      setBundle(saved)
      setRolesByDocument(nextRoles)
      setSavedRolesByDocument(nextRoles)
      setMessage(`자료 역할을 v${saved.currentRevision}로 저장했습니다.`)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '자료 역할을 저장하지 못했습니다.')
    } finally {
      setSavingMaterialRoles(false)
    }
  }

  if (loading) return <section className="workspace-page workspace-state" aria-busy="true"><p className="eyebrow">모험 준비</p><h1>자료를 불러오는 중입니다</h1><p role="status">잠시만 기다려 주세요.</p></section>
  if (message) return <section className="workspace-page workspace-state workspace-state-error"><p className="eyebrow">모험 준비</p><h1>자료를 불러오지 못했습니다</h1><p role="alert">{message}</p><Button variant="outline" onClick={() => window.location.reload()}>다시 시도</Button></section>

  return <section className="workspace-page" aria-labelledby="adventure-workspace-title">
    <div className="workspace-breadcrumb"><a href="#/adventures">모험 목록</a><ChevronRight aria-hidden="true" size={14} /><span>{title}</span></div>
    <header className="workspace-header">
      <div className="workspace-header-copy">
        <p className="eyebrow">모험 준비</p>
        <h1 id="adventure-workspace-title">{title}</h1>
        <p>플레이에 필요한 자료와 파티를 확인하고, 준비가 끝나면 세션을 시작하세요.</p>
      </div>
      <div className="workspace-header-actions">
        <span className={`workspace-status workspace-status-${status === '준비 완료' ? 'ready' : status === '확인 필요' ? 'review' : 'processing'}`}><StatusIcon status={status} />{status}</span>
        <Button onClick={() => setStartOpen(true)}><Play size={15} aria-hidden="true" />세션 시작</Button>
      </div>
    </header>

    <Tabs value={activeTab} className="workspace-tabs">
      <TabsList>
        {tabLabels.map(tab => <TabsTrigger key={tab.value} value={tab.value} active={tab.value === activeTab} onClick={() => changeTab(tab.value)}>{tab.label}{tab.value === 'review' && reviewDocuments.length > 0 ? <span className="tab-count">{reviewDocuments.length}</span> : null}</TabsTrigger>)}
      </TabsList>
      <TabsContent value="materials" active={activeTab === 'materials'}><MaterialsTab documents={documents} readyCount={readyCount} roles={rolesByDocument} canEditRoles={Boolean(bundle && setupApi.reviseScenarioBundle)} hasRoleChanges={Object.keys(rolesByDocument).some(id => rolesByDocument[id] !== savedRolesByDocument[id])} savingRoles={savingMaterialRoles} onRoleChange={(documentId, role) => setRolesByDocument(current => ({ ...current, [documentId]: role }))} onSaveRoles={() => void saveMaterialRoles()} onAdd={() => { window.location.hash = '#/setup?mode=create' }} /></TabsContent>
      <TabsContent value="review" active={activeTab === 'review'}><ReviewTab documents={reviewDocuments} /></TabsContent>
      <TabsContent value="characters" active={activeTab === 'characters'}><CharactersTab onOpen={() => setCharacterOpen(true)} /></TabsContent>
      <TabsContent value="sessions" active={activeTab === 'sessions'}><SessionsTab sessions={sessions} title={title} onOpen={openSession} /></TabsContent>
    </Tabs>

    {message && <p className="workspace-inline-notice" role="status">{message}</p>}
    <SessionStartDialog open={startOpen} onOpenChange={setStartOpen} title={title} sessions={sessions} canCreate={Boolean(scenarioPackageId && setupApi.getPlayPreparation)} creating={creatingSession} onResume={openSession} onCreate={() => void createSession()} />
    <Sheet open={characterOpen} onOpenChange={setCharacterOpen}><SheetContent className="character-sheet-placeholder"><SheetHeader><SheetTitle>캐릭터 시트</SheetTitle></SheetHeader><div className="sheet-placeholder-content"><Users size={24} aria-hidden="true" /><p>캐릭터는 세션 준비에서 파티에 추가한 뒤 확인할 수 있습니다.</p><Button variant="outline" onClick={() => { setCharacterOpen(false); if (partySessionId) window.location.hash = `#/sessions/${partySessionId}/party` }}>파티 확인</Button></div></SheetContent></Sheet>
  </section>
}

function MaterialsTab({ documents, readyCount, roles, canEditRoles, hasRoleChanges, savingRoles, onRoleChange, onSaveRoles, onAdd }: { documents: KnowledgeDocumentView[]; readyCount: number; roles: Record<string, ScenarioBundleRole>; canEditRoles: boolean; hasRoleChanges: boolean; savingRoles: boolean; onRoleChange: (documentId: string, role: ScenarioBundleRole) => void; onSaveRoles: () => void; onAdd: () => void }) {
  return <div className="workspace-tab-panel">
    <div className="tab-panel-heading"><div><p className="eyebrow">모험 자료</p><h2>이번 모험에 들어있는 자료</h2><p>{documents.length}개 자료 중 {readyCount}개 준비됨</p></div><span className="tab-panel-actions">{canEditRoles && hasRoleChanges ? <Button onClick={onSaveRoles} disabled={savingRoles}>{savingRoles ? '저장 중…' : '역할 변경사항 저장'}</Button> : null}<Button variant="outline" onClick={onAdd}><Plus size={15} aria-hidden="true" />자료 추가</Button></span></div>
    {documents.length === 0 ? <EmptyState icon={<FileText size={20} />} title="아직 자료가 없습니다" description="자료 설정에서 시나리오, 지도, 핸드아웃을 추가하세요." /> : <ul className="file-list" aria-label="모험 자료 목록">{documents.map(document => <MaterialRow key={document.knowledgeDocumentId} document={document} role={document.documentType === 'RULEBOOK' ? undefined : roles[document.knowledgeDocumentId]} onRoleChange={role => onRoleChange(document.knowledgeDocumentId, role)} />)}</ul>}
  </div>
}

function ReviewTab({ documents }: { documents: KnowledgeDocumentView[] }) {
  const [selected, setSelected] = useState(0)
  if (documents.length === 0) return <div className="workspace-tab-panel"><EmptyState icon={<Check size={20} />} title="확인할 항목이 없습니다" description="현재 자료는 모두 준비된 상태입니다." /></div>
  const document = documents[Math.min(selected, documents.length - 1)]
  return <div className="review-workspace">
    <aside className="review-list" aria-label="확인할 항목 목록"><div className="review-list-heading"><p className="eyebrow">검토함</p><h2>확인할 항목 {documents.length}</h2></div><ul>{documents.map((item, index) => <li key={item.knowledgeDocumentId}><button type="button" className={index === selected ? 'review-issue-row review-issue-active' : 'review-issue-row'} onClick={() => setSelected(index)}><span className="review-issue-marker"><AlertTriangle size={14} aria-hidden="true" /></span><span><strong>{item.originalFilename}</strong><small>{materialStatus(item).label} · 자료 준비 상태</small></span></button></li>)}</ul></aside>
    <article className="review-detail"><p className="eyebrow">확인할 내용</p><h2>{document.originalFilename}</h2><p className="review-detail-lead">이 자료를 세션에서 사용하려면 현재 상태를 확인해야 합니다.</p><Separator /><dl className="review-evidence"><div><dt>현재 상태</dt><dd><MaterialStatus document={document} /></dd></div><div><dt>자료 종류</dt><dd>{document.documentType === 'RULEBOOK' ? '룰북' : '시나리오 자료'}</dd></div><div><dt>파일 형식</dt><dd>{document.format}</dd></div></dl>{document.failureReason && <p className="review-warning"><AlertTriangle size={15} aria-hidden="true" />{document.failureReason}</p>}<div className="review-actions"><Button variant="outline" onClick={() => window.location.hash = '#/setup?mode=create'}>자료 구성에서 확인</Button></div></article>
  </div>
}

function CharactersTab({ onOpen }: { onOpen: () => void }) {
  return <div className="workspace-tab-panel"><div className="tab-panel-heading"><div><p className="eyebrow">파티 기록</p><h2>모험에 참여하는 캐릭터</h2><p>캐릭터 시트는 세션 준비에서 파티에 추가하면 이곳에서 이어집니다.</p></div><Button variant="outline" onClick={onOpen}><Users size={15} aria-hidden="true" />캐릭터 시트 보기</Button></div><EmptyState icon={<Users size={20} />} title="아직 연결된 캐릭터가 없습니다" description="세션 시작을 누른 뒤 파티 구성에서 직접 조작할 캐릭터를 추가하세요." /></div>
}

function SessionsTab({ sessions, title, onOpen }: { sessions: AdventureSessionView[]; title: string; onOpen: (session: AdventureSessionView) => void }) {
  return <div className="workspace-tab-panel"><div className="tab-panel-heading"><div><p className="eyebrow">진행 기록</p><h2>이 모험의 세션</h2><p>같은 모험의 준비와 플레이 기록을 이어갑니다.</p></div></div>{sessions.length > 0 ? <ul className="session-history-list" aria-label="이 모험의 세션 목록">{sessions.map(session => <li key={session.sessionId}><span className="session-history-icon" aria-hidden="true"><ScrollText size={18} /></span><span><strong>{title}</strong><small>{sessionStatusLabel[session.status]} · 캐릭터 {session.party.length}/{session.characterLimit}</small></span><span className="session-history-status">{sessionStatusLabel[session.status]}</span><Button variant="ghost" onClick={() => onOpen(session)}>{session.status === 'STARTED' ? '이어하기' : session.status === 'DRAFT' || session.status === 'STARTING' ? '파티 구성' : '기록 보기'}<ChevronRight size={15} aria-hidden="true" /></Button></li>)}</ul> : <EmptyState icon={<CalendarDays size={20} />} title="세션 기록이 없습니다" description="세션 시작을 눌러 첫 기록을 만드세요." />}</div>
}

function SessionStartDialog({ open, onOpenChange, title, sessions, canCreate, creating, onResume, onCreate }: { open: boolean; onOpenChange: (open: boolean) => void; title: string; sessions: AdventureSessionView[]; canCreate: boolean; creating: boolean; onResume: (session: AdventureSessionView) => void; onCreate: () => void }) {
  const resumable = sessions.find(session => session.status === 'DRAFT' || session.status === 'STARTING' || session.status === 'STARTED')
  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent><DialogHeader><p className="eyebrow">세션 시작</p><DialogTitle>{title}</DialogTitle><DialogDescription>참가 캐릭터와 이전 진행 상황을 확인한 뒤 플레이 화면으로 이동합니다.</DialogDescription></DialogHeader><div className="session-start-choice"><div className={`session-start-option ${resumable ? 'session-start-option-active' : 'session-start-option-disabled'}`}><span className="session-start-option-icon"><ScrollText size={18} aria-hidden="true" /></span><span><strong>{resumable ? '준비 중인 세션 이어하기' : '이어갈 세션 없음'}</strong><small>{resumable ? `${sessionStatusLabel[resumable.status]} 상태의 세션을 엽니다.` : '새 세션을 만들어 시작하세요.'}</small></span>{resumable && <Check size={17} aria-hidden="true" />}</div><div className={`session-start-option ${canCreate ? '' : 'session-start-option-disabled'}`}><span className="session-start-option-icon"><Plus size={18} aria-hidden="true" /></span><span><strong>새 세션</strong><small>{canCreate ? '현재 모험 자료로 새 세션을 만듭니다.' : '게시된 캐릭터 생성 설정이 필요합니다.'}</small></span></div></div><DialogFooter><Button variant="ghost" onClick={() => onOpenChange(false)}>취소</Button>{resumable && <Button variant="outline" onClick={() => onResume(resumable)}><Play size={15} aria-hidden="true" />{resumable.status === 'STARTED' ? '게임 시작' : '파티 구성'}</Button>}<Button disabled={!canCreate || creating} onClick={onCreate}><Plus size={15} aria-hidden="true" />{creating ? '세션 준비 중…' : '새 세션 만들기'}</Button></DialogFooter></DialogContent></Dialog>
}

function EmptyState({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) {
  return <div className="workspace-empty"><span className="workspace-empty-icon" aria-hidden="true">{icon}</span><h3>{title}</h3><p>{description}</p></div>
}

function StatusIcon({ status }: { status: string }) {
  if (status === '준비 완료' || status === '준비됨' || status === '사용 가능') return <Check size={14} aria-hidden="true" />
  if (status === '확인 필요' || status === '사용 불가') return <AlertTriangle size={14} aria-hidden="true" />
  return <span className="status-dot" aria-hidden="true" />
}
