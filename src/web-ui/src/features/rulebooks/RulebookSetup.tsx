import { type FormEvent, type ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { BookOpen, Check, ChevronLeft, Eye, FilePlus2, RefreshCw, ScrollText, Trash2, Upload } from 'lucide-react'
import { MaterialRow, materialStatus } from '../../components/adventure/material-row'
import { SetupStepper, type SetupStep } from '../../components/setup/setup-stepper'
import { Button } from '../../components/ui/button'
import { Input } from '../../components/ui/input'
import { Select } from '../../components/ui/select'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '../../components/ui/sheet'
import { Textarea } from '../../components/ui/textarea'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import { PreparationFlow } from './PreparationFlow'
import type {
  BatchRulebookView,
  KnowledgeDocumentView,
  RulebookUploadDraft,
  ScenarioBundleRole,
  ScenarioBundleView,
  SetupApi,
  SourcePreviewView,
} from './SetupApi'

type PendingDocument = RulebookUploadDraft & { originalFilename: string }
type CatalogRulebook = { catalogRevisionId: string; edition: string; displayName: string; rulebookId: string | null; revisionNumber: number; status: string }
type SetupView = 'intro' | 'details' | 'materials' | 'preparing' | 'complete'

const bundleReadyStatuses = new Set<KnowledgeDocumentView['status']>(['INDEXED', 'READY', 'PARTIAL_CONFIRMED'])

function createIdempotencyKey(file: File, index: number) {
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${file.name}-${index}-${random}`
}

function rulebookEdition(value: string | undefined): 'DND_5E_2014' | 'DND_5E_2024' | null {
  return value === 'DND_5E_2014' || value === 'DND_5E_2024' ? value : null
}

function stepForView(view: SetupView): SetupStep {
  if (view === 'complete') return 'complete'
  if (view === 'materials' || view === 'preparing') return 'materials'
  return 'details'
}

export function RulebookSetup({
  api,
  playerId,
  asMain = true,
  sessionApi,
}: {
  api: SetupApi
  playerId: string
  asMain?: boolean
  sessionApi?: Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage'>
}) {
  const [view, setView] = useState<SetupView>('intro')
  const [adventureName, setAdventureName] = useState('')
  const [subtitle, setSubtitle] = useState('')
  const [description, setDescription] = useState('')
  const [drafts, setDrafts] = useState<PendingDocument[]>([])
  const [results, setResults] = useState<BatchRulebookView[]>([])
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [selectedUploadedIds, setSelectedUploadedIds] = useState<Set<string>>(new Set())
  const [roles, setRoles] = useState<Record<string, ScenarioBundleRole>>({})
  const [selectedCatalogRulebookId, setSelectedCatalogRulebookId] = useState<string | null>(null)
  const [catalogRulebooks, setCatalogRulebooks] = useState<CatalogRulebook[]>([])
  const [sourcePreview, setSourcePreview] = useState<SourcePreviewView | null>(null)
  const [message, setMessage] = useState('')
  const [uploading, setUploading] = useState(false)
  const [creatingBundle, setCreatingBundle] = useState(false)
  const [preparationBundle, setPreparationBundle] = useState<ScenarioBundleView | null>(null)
  const [createdSession, setCreatedSession] = useState<AdventureSessionView | null>(null)

  const refreshDocuments = useCallback(async (): Promise<KnowledgeDocumentView[] | null> => {
    try {
      const loaded = await api.listKnowledgeDocuments(playerId)
      setDocuments(loaded)
      return loaded
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '자료 목록을 불러오지 못했습니다.')
      return null
    }
  }, [api, playerId])

  useEffect(() => { void refreshDocuments() }, [refreshDocuments])

  useEffect(() => {
    const hasPending = documents.some(document => materialStatus(document).kind === 'processing')
    if (!hasPending) return
    let active = true
    let polling = false
    const timer = window.setInterval(() => {
      if (!active || polling) return
      polling = true
      void refreshDocuments().finally(() => { polling = false })
    }, 1000)
    return () => { active = false; window.clearInterval(timer) }
  }, [documents, refreshDocuments])

  useEffect(() => {
    void fetch('/api/v1/rulebook-catalog')
      .then(response => response.ok ? response.json() : [])
      .then((items: CatalogRulebook[]) => {
        const ready = items.filter(item => item.status === 'READY' && item.rulebookId)
        setCatalogRulebooks(ready)
        setSelectedCatalogRulebookId(current => current ?? (ready.length === 1 ? ready[0].rulebookId : null))
      })
      .catch(() => setCatalogRulebooks([]))
  }, [])

  const uploadedDocuments = useMemo(() => documents.filter(document => document.documentType === 'STORYBOOK'), [documents])
  const selectedDocuments = useMemo(() => uploadedDocuments.filter(document => selectedUploadedIds.has(document.knowledgeDocumentId)), [selectedUploadedIds, uploadedDocuments])
  const selectedRulebook = catalogRulebooks.find(item => item.rulebookId === selectedCatalogRulebookId)
  const selectedEdition = rulebookEdition(selectedRulebook?.edition)
  const readySelectedCount = selectedDocuments.filter(document => bundleReadyStatuses.has(document.status)).length
  const hasMainScenario = selectedDocuments.some(document => roles[document.knowledgeDocumentId] === 'MAIN_SCENARIO')
  const pendingCount = selectedDocuments.filter(document => materialStatus(document).kind === 'processing').length
  const issueCount = selectedDocuments.filter(document => ['review', 'failed'].includes(materialStatus(document).kind)).length
  const canContinueDetails = Boolean(adventureName.trim() && selectedCatalogRulebookId && selectedEdition)
  const canCreateBundle = Boolean(canContinueDetails && selectedDocuments.length > 0 && readySelectedCount === selectedDocuments.length && hasMainScenario)

  function assignDefaultRole(documentId: string, selected: boolean) {
    setSelectedUploadedIds(current => {
      const next = new Set(current)
      if (selected) next.add(documentId)
      else next.delete(documentId)
      return next
    })
    if (selected) {
      setRoles(current => {
        if (current[documentId]) return current
        const hasMain = Object.entries(current).some(([id, role]) => id !== documentId && selectedUploadedIds.has(id) && role === 'MAIN_SCENARIO')
        return { ...current, [documentId]: hasMain ? 'HANDOUT' : 'MAIN_SCENARIO' }
      })
    }
  }

  function updateRole(documentId: string, role: ScenarioBundleRole) {
    setRoles(current => {
      if (role !== 'MAIN_SCENARIO') return { ...current, [documentId]: role }
      const next = { ...current }
      Object.keys(next).forEach(id => { if (next[id] === 'MAIN_SCENARIO') next[id] = 'HANDOUT' })
      next[documentId] = 'MAIN_SCENARIO'
      return next
    })
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!drafts.length) return
    setMessage('')
    setUploading(true)
    try {
      const uploaded = await api.uploadRulebooks(drafts, playerId)
      setResults(uploaded)
      const acceptedIds = uploaded.flatMap(item => item.status === 'ACCEPTED' && item.knowledgeDocumentId ? [item.knowledgeDocumentId] : [])
      const failures = uploaded.filter(item => item.status === 'VALIDATION_FAILED')
      setSelectedUploadedIds(current => new Set([...current, ...acceptedIds]))
      setRoles(current => {
        const next = { ...current }
        let mainAssigned = Object.values(next).includes('MAIN_SCENARIO')
        acceptedIds.forEach(id => {
          if (!next[id]) {
            next[id] = mainAssigned ? 'HANDOUT' : 'MAIN_SCENARIO'
            mainAssigned = true
          }
        })
        return next
      })
      await refreshDocuments()
      if (failures.length) setMessage(failures.map(item => `${item.originalFilename}: ${item.failureReason || '파일을 사용할 수 없습니다.'}`).join(' · '))
      setDrafts([])
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '자료를 업로드하지 못했습니다.')
    } finally {
      setUploading(false)
    }
  }

  async function retryDocument(knowledgeDocumentId: string) {
    try {
      await api.retryKnowledgeDocument(knowledgeDocumentId)
      await refreshDocuments()
      setMessage('자료를 다시 준비하고 있습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '다시 처리하지 못했습니다.')
    }
  }

  async function deleteDocument(document: KnowledgeDocumentView) {
    if (!api.deleteKnowledgeDocument || !window.confirm(`${document.originalFilename}을(를) 삭제할까요?`)) return
    try {
      await api.deleteKnowledgeDocument(document.knowledgeDocumentId)
      setDocuments(current => current.filter(item => item.knowledgeDocumentId !== document.knowledgeDocumentId))
      setSelectedUploadedIds(current => {
        const next = new Set(current)
        next.delete(document.knowledgeDocumentId)
        return next
      })
      setRoles(current => {
        const next = { ...current }
        delete next[document.knowledgeDocumentId]
        return next
      })
      setMessage('자료를 삭제했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '자료를 삭제하지 못했습니다.')
    }
  }

  async function previewDocument(knowledgeDocumentId: string) {
    try {
      setSourcePreview(await api.getSourcePreview(knowledgeDocumentId))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '미리보기를 불러오지 못했습니다.')
    }
  }

  async function createBundleAndPrepare() {
    if (!canCreateBundle || !selectedCatalogRulebookId || !selectedEdition) return
    const bundleDocuments = [
      ...selectedDocuments.map(document => ({ knowledgeDocumentId: document.knowledgeDocumentId, role: roles[document.knowledgeDocumentId] ?? 'HANDOUT' as ScenarioBundleRole })),
      { knowledgeDocumentId: selectedCatalogRulebookId, role: 'RULEBOOK' as ScenarioBundleRole },
    ]
    setCreatingBundle(true)
    setMessage('')
    try {
      const bundle = await api.createScenarioBundle(playerId, bundleDocuments, { name: adventureName.trim(), rulebookEdition: selectedEdition })
      window.localStorage.setItem('dnd-selected-bundle-id', bundle.bundleId)
      window.dispatchEvent(new Event('dnd-selected-bundle-change'))
      setPreparationBundle(bundle)
      setView('preparing')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 자료를 저장하지 못했습니다.')
    } finally {
      setCreatingBundle(false)
    }
  }

  function resetWizard() {
    setView('intro')
    setAdventureName('')
    setSubtitle('')
    setDescription('')
    setDrafts([])
    setResults([])
    setSelectedUploadedIds(new Set())
    setRoles({})
    setPreparationBundle(null)
    setCreatedSession(null)
    setMessage('')
  }

  const Container = asMain ? 'main' : 'section'
  const commonPreview = <Sheet open={Boolean(sourcePreview)} onOpenChange={open => { if (!open) setSourcePreview(null) }}><SheetContent className="setup-preview-sheet"><SheetHeader><p className="eyebrow">SOURCE PREVIEW</p><SheetTitle>{sourcePreview?.originalFilename ?? '자료 미리보기'}</SheetTitle></SheetHeader>{sourcePreview ? <div className="setup-preview-content"><p className="setup-preview-meta">{sourcePreview.format} · 원문 미리보기</p>{sourcePreview.warnings.length ? <p className="setup-preview-warning">{sourcePreview.warnings.join(', ')}</p> : null}<ol aria-label="원문 줄 미리보기">{sourcePreview.spans.map(span => <li key={`${span.lineNumber}-${span.startInclusive}-${span.endExclusive}`}><small>{span.path.join(' › ')}{span.pageNumber ? ` · p.${span.pageNumber}` : ''}</small><pre>{span.text || ' '}</pre></li>)}</ol></div> : null}</SheetContent></Sheet>

  if (view === 'intro') return <Container className="setup-page setup-wizard setup-wizard-intro" aria-labelledby="setup-intro-title">
    <a className="setup-back-link" href="#/adventures">← 내 모험</a>
    <div className="setup-intro-body">
      <span className="setup-intro-mark" aria-hidden="true">◇</span>
      <p className="eyebrow">NEW ADVENTURE</p>
      <h1 id="setup-intro-title">새로운 모험을 시작하세요</h1>
      <p className="setup-intro-lead">이야기의 기본 정보를 적고 자료를 더하면, 플레이할 수 있는 모험 공간을 준비합니다.</p>
      <div className="setup-intro-features" aria-label="새 모험 만들기 안내">
        <Feature icon={<BookOpen size={20} />} title="기본 정보" description="모험 이름과 사용할 룰북을 정합니다." />
        <Feature icon={<FilePlus2 size={20} />} title="자료 추가" description="시나리오, 지도와 핸드아웃을 한곳에 모읍니다." />
        <Feature icon={<Check size={20} />} title="준비 완료" description="필요한 처리가 끝나면 모험 공간으로 이동합니다." />
      </div>
      <Button className="setup-intro-primary" onClick={() => setView('details')}>시작하기</Button>
    </div>
  </Container>

  if (view === 'complete') return <Container className="setup-page setup-wizard setup-wizard-complete" aria-labelledby="setup-complete-title">
    <SetupStepper step="complete" />
    <div className="setup-complete-body">
      <span className="setup-complete-mark" aria-hidden="true"><Check size={28} /></span>
      <p className="eyebrow">ADVENTURE READY</p>
      <h1 id="setup-complete-title">모험 준비가 완료되었습니다</h1>
      <p>{adventureName}의 준비가 끝났습니다. 이제 캐릭터와 세션을 이어서 구성할 수 있습니다.</p>
      <dl className="setup-complete-summary">
        <div><dt>모험</dt><dd>{adventureName}</dd></div>
        {subtitle ? <div><dt>부제</dt><dd>{subtitle}</dd></div> : null}
        <div><dt>룰북</dt><dd>{selectedRulebook?.displayName ?? '선택한 룰북'}</dd></div>
        <div><dt>자료</dt><dd>{selectedDocuments.length}개</dd></div>
      </dl>
      <div className="setup-complete-actions">
        <Button variant="outline" onClick={resetWizard}>다른 모험 만들기</Button>
        <Button onClick={() => {
          if (createdSession?.adventureId) window.location.hash = `#/adventures/${encodeURIComponent(createdSession.adventureId)}?tab=materials`
          else if (createdSession) window.location.hash = `#/sessions/${createdSession.sessionId}/party`
          else window.location.hash = '#/adventures'
        }}>모험 공간으로 이동</Button>
      </div>
    </div>
  </Container>

  return <Container className="setup-page setup-wizard" aria-labelledby="setup-wizard-title">
    <a className="setup-back-link" href="#/adventures">← 내 모험</a>
    <SetupStepper step={stepForView(view)} />
    {message ? <p className="workspace-inline-notice setup-notice" role="status" aria-live="polite">{message}</p> : <span className="setup-status-anchor" role="status" aria-live="polite" />}

    {view === 'details' ? <section className="setup-wizard-stage" aria-labelledby="setup-wizard-title">
      <header className="setup-stage-header"><p className="eyebrow">STEP 01</p><h1 id="setup-wizard-title">모험의 기본 정보를 입력하세요</h1><p>플레이 중 계속 보게 될 이름과 규칙 기준을 먼저 정합니다.</p></header>
      <div className="setup-details-grid">
        <div className="setup-fields">
          <label className="setup-field"><span>모험 이름 <strong aria-hidden="true">*</strong></span><Input aria-label="모험 이름" value={adventureName} onChange={event => setAdventureName(event.currentTarget.value)} placeholder="예: 폭풍왕의 천둥" autoFocus /></label>
          <label className="setup-field"><span>부제 <small>선택</small></span><Input aria-label="부제" value={subtitle} onChange={event => setSubtitle(event.currentTarget.value)} placeholder="예: Storm King's Thunder" /></label>
          <label className="setup-field"><span>설명 <small>선택</small></span><Textarea aria-label="설명" value={description} onChange={event => setDescription(event.currentTarget.value)} placeholder="이 모험을 기억하기 위한 짧은 설명을 적어두세요." /></label>
          <div className="setup-field"><span>룰북 <strong aria-hidden="true">*</strong></span>{catalogRulebooks.length === 0 ? <div className="setup-inline-empty"><strong>사용 가능한 룰북이 없습니다</strong><small>룰북이 준비되면 다음 단계로 진행할 수 있습니다.</small></div> : catalogRulebooks.length === 1 ? <div className="setup-fixed-rulebook"><BookOpen size={18} aria-hidden="true" /><span><strong>{catalogRulebooks[0].displayName}</strong><small>{catalogRulebooks[0].edition === 'DND_5E_2024' ? 'D&D 5.5판' : 'D&D 5판'} · revision {catalogRulebooks[0].revisionNumber}</small></span><Check size={16} aria-hidden="true" /></div> : <Select aria-label="룰북" value={selectedCatalogRulebookId ?? ''} onChange={event => setSelectedCatalogRulebookId(event.currentTarget.value || null)}><option value="">룰북을 선택하세요</option>{catalogRulebooks.map(rulebook => <option key={rulebook.catalogRevisionId} value={rulebook.rulebookId ?? ''}>{rulebook.displayName}</option>)}</Select>}</div>
        </div>
        <aside className="setup-note-panel"><ScrollText size={20} aria-hidden="true" /><p className="eyebrow">GM NOTE</p><h2>이름은 언제든 다듬을 수 있어요</h2><p>지금은 모험을 구분할 수 있을 정도면 충분합니다. 실제 플레이에 필요한 구조화와 준비 작업은 자료를 추가한 뒤 자동으로 진행됩니다.</p></aside>
      </div>
      <StageFooter backLabel="처음으로" onBack={() => setView('intro')} nextLabel="다음 단계" onNext={() => setView('materials')} nextDisabled={!canContinueDetails} />
    </section> : null}

    {view === 'materials' ? <section className="setup-wizard-stage" aria-labelledby="setup-wizard-title">
      <header className="setup-stage-header"><p className="eyebrow">STEP 02</p><h1 id="setup-wizard-title">모험 자료를 추가하세요</h1><p>시나리오, 지도, 핸드아웃과 캐릭터 자료를 추가하고 각각의 주 역할을 지정합니다.</p></header>
      <div className="setup-materials-grid">
        <form className="setup-inline-upload" onSubmit={upload}>
          <label className="setup-dropzone"><Upload size={26} aria-hidden="true" /><strong>파일을 선택하거나 끌어오세요</strong><span>PDF, DOCX, TXT, Markdown, 이미지</span><Input name="rulebooks" aria-label="자료 파일" type="file" accept=".pdf,.docx,.txt,.md,.png,.jpg,.jpeg,.tif,.tiff,.bmp" multiple onChange={event => { const files = Array.from(event.currentTarget.files ?? []); setDrafts(files.map((file, index) => ({ file, originalFilename: file.name, documentType: 'STORYBOOK', idempotencyKey: createIdempotencyKey(file, index) }))) }} /></label>
          {drafts.length ? <div className="setup-upload-selection"><span>{drafts.length}개 파일 선택됨</span><Button type="submit" disabled={uploading}>{uploading ? '추가 중…' : '자료 추가'}</Button></div> : null}
          {results.some(result => result.status === 'VALIDATION_FAILED') ? <small className="setup-upload-warning">일부 파일은 추가하지 못했습니다. 상단 안내를 확인하세요.</small> : null}
        </form>
        <aside className="setup-role-guide"><p className="eyebrow">자료 역할</p><h2>가장 중요한 역할을 지정하세요</h2><p>한 파일에 여러 성격의 내용이 있어도 현재 모험에서는 주 역할 하나를 기준으로 정리합니다.</p><dl><div><dt>메인 시나리오</dt><dd>진행의 중심이 되는 이야기 자료</dd></div><div><dt>지도</dt><dd>장소와 전투 공간을 설명하는 자료</dd></div><div><dt>핸드아웃</dt><dd>플레이 중 참고하거나 보여줄 자료</dd></div></dl></aside>
      </div>
      {uploadedDocuments.length > 0 ? <div className="setup-material-list-section"><div className="setup-material-list-heading"><div><h2>추가된 자료</h2><p>이 모험에 사용할 자료를 선택하고 주 역할을 확인하세요.</p></div><span>{selectedDocuments.length}개 선택</span></div><ul className="file-list setup-material-list" aria-label="문서 상태 목록">{uploadedDocuments.map(document => {
        const selected = selectedUploadedIds.has(document.knowledgeDocumentId)
        const previewable = ['EXTRACTED', 'INDEXED', 'READY', 'PARTIAL_CONFIRMED'].includes(document.status)
        return <MaterialRow key={document.knowledgeDocumentId} document={document} selected={selected} selectable={materialStatus(document).kind !== 'failed'} onSelectedChange={checked => assignDefaultRole(document.knowledgeDocumentId, checked)} role={selected ? roles[document.knowledgeDocumentId] ?? 'HANDOUT' : undefined} onRoleChange={selected ? role => updateRole(document.knowledgeDocumentId, role) : undefined} actions={<>{previewable ? <Button variant="ghost" size="icon" aria-label={`${document.originalFilename} 미리보기`} onClick={() => void previewDocument(document.knowledgeDocumentId)}><Eye size={16} aria-hidden="true" /></Button> : null}{document.status === 'FAILED' ? <Button variant="ghost" size="icon" aria-label={`${document.originalFilename} 다시 처리`} onClick={() => void retryDocument(document.knowledgeDocumentId)}><RefreshCw size={16} aria-hidden="true" /></Button> : null}{api.deleteKnowledgeDocument ? <Button variant="ghost" size="icon" aria-label={`${document.originalFilename} 삭제`} onClick={() => void deleteDocument(document)}><Trash2 size={16} aria-hidden="true" /></Button> : null}</>} />
      })}</ul></div> : <div className="setup-material-empty"><FilePlus2 size={22} aria-hidden="true" /><strong>아직 추가된 자료가 없습니다</strong><span>위 영역에서 첫 번째 시나리오 자료를 추가하세요.</span></div>}
      <div className="setup-readiness-line" aria-live="polite"><span>{hasMainScenario ? '✓ 메인 시나리오 지정됨' : '○ 메인 시나리오를 지정하세요'}</span><span>{pendingCount > 0 ? `○ ${pendingCount}개 준비 중` : issueCount > 0 ? `△ ${issueCount}개 확인 필요` : selectedDocuments.length > 0 ? '✓ 선택한 자료 준비됨' : '○ 자료를 선택하세요'}</span></div>
      <StageFooter backLabel="이전" onBack={() => setView('details')} nextLabel={creatingBundle ? '준비 중…' : '모험 준비'} onNext={() => void createBundleAndPrepare()} nextDisabled={!canCreateBundle || creatingBundle} />
    </section> : null}

    {view === 'preparing' ? <section className="setup-wizard-stage setup-preparing-stage" aria-labelledby="setup-wizard-title"><header className="setup-stage-header setup-stage-header-centered"><p className="eyebrow">PREPARING ADVENTURE</p><h1 id="setup-wizard-title">모험을 준비하고 있습니다</h1><p>{adventureName}의 자료를 정리하고 플레이에 필요한 내용을 준비합니다.</p></header>{preparationBundle ? <PreparationFlow api={api} playerId={playerId} bundle={preparationBundle} sessionApi={sessionApi} onError={setMessage} onAdventureCreated={session => { setCreatedSession(session); setView('complete') }} /> : null}<button type="button" className="setup-preparing-back" onClick={() => setView('materials')}><ChevronLeft size={14} aria-hidden="true" />자료 화면으로 돌아가기</button></section> : null}
    {commonPreview}
  </Container>
}

function Feature({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return <div className="setup-intro-feature"><span className="setup-intro-feature-icon" aria-hidden="true">{icon}</span><strong>{title}</strong><p>{description}</p></div>
}

function StageFooter({ backLabel, onBack, nextLabel, onNext, nextDisabled }: { backLabel: string; onBack: () => void; nextLabel: string; onNext: () => void; nextDisabled?: boolean }) {
  return <footer className="setup-stage-footer"><Button variant="ghost" onClick={onBack}><ChevronLeft size={15} aria-hidden="true" />{backLabel}</Button><Button onClick={onNext} disabled={nextDisabled}>{nextLabel}</Button></footer>
}
