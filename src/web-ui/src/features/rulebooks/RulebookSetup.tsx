import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react'
import { Eye, FilePlus2, Plus, RefreshCw, Trash2, Upload } from 'lucide-react'
import { MaterialRow, materialRoleLabels, materialStatus } from '../../components/adventure/material-row'
import { Button } from '../../components/ui/button'
import { Checkbox } from '../../components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog'
import { Input } from '../../components/ui/input'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '../../components/ui/sheet'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
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

const bundleReadyStatuses = new Set<KnowledgeDocumentView['status']>(['INDEXED', 'READY', 'PARTIAL_CONFIRMED'])

function createIdempotencyKey(file: File, index: number) {
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${file.name}-${index}-${random}`
}

function filenameTitle(filename: string) {
  return filename.replace(/\.[^.]+$/, '').trim() || '새 모험'
}

function rulebookEdition(value: string | undefined): 'DND_5E_2014' | 'DND_5E_2024' | null {
  return value === 'DND_5E_2014' || value === 'DND_5E_2024' ? value : null
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
  const [addOpen, setAddOpen] = useState(false)
  const [preparationBundle, setPreparationBundle] = useState<ScenarioBundleView | null>(null)

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
  const readySelectedCount = selectedDocuments.filter(document => bundleReadyStatuses.has(document.status)).length
  const hasMainScenario = selectedDocuments.some(document => roles[document.knowledgeDocumentId] === 'MAIN_SCENARIO')
  const canCreateBundle = Boolean(selectedCatalogRulebookId && selectedDocuments.length > 0 && readySelectedCount === selectedDocuments.length && hasMainScenario)

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
      setAddOpen(false)
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
    if (!canCreateBundle || !selectedCatalogRulebookId) return
    const selectedRulebook = catalogRulebooks.find(item => item.rulebookId === selectedCatalogRulebookId)
    const primary = selectedDocuments.find(document => roles[document.knowledgeDocumentId] === 'MAIN_SCENARIO') ?? selectedDocuments[0]
    const edition = rulebookEdition(selectedRulebook?.edition)
    const bundleDocuments = [
      ...selectedDocuments.map(document => ({ knowledgeDocumentId: document.knowledgeDocumentId, role: roles[document.knowledgeDocumentId] ?? 'HANDOUT' as ScenarioBundleRole })),
      { knowledgeDocumentId: selectedCatalogRulebookId, role: 'RULEBOOK' as ScenarioBundleRole },
    ]
    setCreatingBundle(true)
    setMessage('')
    try {
      const bundle = await api.createScenarioBundle(
        playerId,
        bundleDocuments,
        edition ? { name: filenameTitle(primary.originalFilename), rulebookEdition: edition } : undefined,
      )
      window.localStorage.setItem('dnd-selected-bundle-id', bundle.bundleId)
      window.dispatchEvent(new Event('dnd-selected-bundle-change'))
      setPreparationBundle(bundle)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 자료를 저장하지 못했습니다.')
    } finally {
      setCreatingBundle(false)
    }
  }

  const Container = asMain ? 'main' : 'section'
  const pendingCount = selectedDocuments.filter(document => materialStatus(document).kind === 'processing').length
  const issueCount = selectedDocuments.filter(document => ['review', 'failed'].includes(materialStatus(document).kind)).length

  return <Container className="setup-page new-adventure-setup" aria-labelledby="new-adventure-title">
    <div className="workspace-breadcrumb setup-breadcrumb"><a href="#/adventures">← 내 모험</a></div>
    <header className="setup-document-header">
      <p className="eyebrow">NEW ADVENTURE</p>
      <h1 id="new-adventure-title">새 모험 준비</h1>
      <p>플레이에 사용할 룰북과 모험 자료를 한 장의 준비 시트에서 정리합니다.</p>
    </header>
    {message ? <p className="workspace-inline-notice setup-notice" role="status" aria-live="polite">{message}</p> : null}

    <section className="setup-document-section" aria-labelledby="setup-rulebook-heading">
      <SectionHeading number="01" title="룰북" description="이 모험에서 사용할 규칙 기준을 선택하세요." id="setup-rulebook-heading" />
      {catalogRulebooks.length === 0 ? <div className="setup-inline-empty"><p>현재 선택할 수 있는 룰북이 없습니다.</p><small>관리자가 룰북을 준비하면 이곳에 표시됩니다.</small></div> : <ul className="setup-rulebook-list" aria-label="룰북 목록">{catalogRulebooks.map(rulebook => {
        const selected = selectedCatalogRulebookId === rulebook.rulebookId
        return <li key={rulebook.catalogRevisionId} className={selected ? 'setup-rulebook-row setup-rulebook-row-selected' : 'setup-rulebook-row'}>
          <label>
            <Checkbox aria-label={`${rulebook.displayName} 선택`} checked={selected} onCheckedChange={() => setSelectedCatalogRulebookId(rulebook.rulebookId)} />
            <span><strong>{rulebook.displayName}</strong><small>{rulebook.edition === 'DND_5E_2024' ? 'D&D 5.5판' : rulebook.edition === 'DND_5E_2014' ? 'D&D 5판' : rulebook.edition} · revision {rulebook.revisionNumber}</small></span>
          </label>
          {selected ? <span className="setup-rulebook-selected">✓ 선택됨</span> : null}
        </li>
      })}</ul>}
    </section>

    <section className="setup-document-section" aria-labelledby="setup-material-heading">
      <SectionHeading number="02" title="모험 자료" description="시나리오, 지도, 핸드아웃과 캐릭터 자료를 추가하고 역할을 지정하세요." id="setup-material-heading" action={<Button variant="outline" onClick={() => setAddOpen(true)}><Plus size={15} aria-hidden="true" />자료 추가</Button>} />
      {uploadedDocuments.length === 0 ? <button type="button" className="setup-material-empty" onClick={() => setAddOpen(true)}><FilePlus2 size={24} aria-hidden="true" /><strong>아직 추가된 모험 자료가 없습니다</strong><span>시나리오나 지도를 추가해 모험을 준비하세요.</span></button> : <ul className="file-list setup-material-list" aria-label="문서 상태 목록">{uploadedDocuments.map(document => {
        const selected = selectedUploadedIds.has(document.knowledgeDocumentId)
        const previewable = ['EXTRACTED', 'INDEXED', 'READY', 'PARTIAL_CONFIRMED'].includes(document.status)
        return <MaterialRow
          key={document.knowledgeDocumentId}
          document={document}
          selected={selected}
          selectable={materialStatus(document).kind !== 'failed'}
          onSelectedChange={checked => assignDefaultRole(document.knowledgeDocumentId, checked)}
          role={selected ? roles[document.knowledgeDocumentId] ?? 'HANDOUT' : undefined}
          onRoleChange={selected ? role => updateRole(document.knowledgeDocumentId, role) : undefined}
          actions={<>
            {previewable ? <Button variant="ghost" size="icon" aria-label={`${document.originalFilename} 미리보기`} onClick={() => void previewDocument(document.knowledgeDocumentId)}><Eye size={16} aria-hidden="true" /></Button> : null}
            {document.status === 'FAILED' ? <Button variant="ghost" size="icon" aria-label={`${document.originalFilename} 다시 처리`} onClick={() => void retryDocument(document.knowledgeDocumentId)}><RefreshCw size={16} aria-hidden="true" /></Button> : null}
            {api.deleteKnowledgeDocument ? <Button variant="ghost" size="icon" aria-label={`${document.originalFilename} 삭제`} onClick={() => void deleteDocument(document)}><Trash2 size={16} aria-hidden="true" /></Button> : null}
          </>}
        />
      })}</ul>}
    </section>

    <section className="setup-document-section setup-confirm-section" aria-labelledby="setup-confirm-heading">
      <SectionHeading number="03" title="준비 확인" description="필수 항목이 준비되면 내부 처리 과정은 자동으로 진행됩니다." id="setup-confirm-heading" />
      <div className="setup-confirm-grid">
        <div className={selectedCatalogRulebookId ? 'setup-check setup-check-ready' : 'setup-check'}><strong>{selectedCatalogRulebookId ? '✓' : '○'} 룰북</strong><span>{selectedCatalogRulebookId ? '선택됨' : '선택 필요'}</span></div>
        <div className={selectedDocuments.length > 0 ? 'setup-check setup-check-ready' : 'setup-check'}><strong>{selectedDocuments.length > 0 ? '✓' : '○'} 모험 자료</strong><span>{selectedDocuments.length}개 선택</span></div>
        <div className={hasMainScenario ? 'setup-check setup-check-ready' : 'setup-check'}><strong>{hasMainScenario ? '✓' : '○'} 메인 시나리오</strong><span>{hasMainScenario ? '지정됨' : '역할 지정 필요'}</span></div>
        <div className={pendingCount === 0 && issueCount === 0 && selectedDocuments.length > 0 ? 'setup-check setup-check-ready' : 'setup-check'}><strong>{pendingCount === 0 && issueCount === 0 ? '✓' : '○'} 자료 상태</strong><span>{pendingCount > 0 ? `${pendingCount}개 준비 중` : issueCount > 0 ? `${issueCount}개 확인 필요` : selectedDocuments.length > 0 ? '모두 준비됨' : '자료 없음'}</span></div>
      </div>
      <div className="setup-confirm-footer"><div><p>새 모험 생성에 필요한 정보만 확인합니다.</p><small>Bundle, compilation, package 같은 내부 처리 단계는 화면에 노출하지 않습니다.</small></div><Button disabled={!canCreateBundle || creatingBundle} onClick={() => void createBundleAndPrepare()}>{creatingBundle ? '모험 준비 중…' : '모험 만들기'} </Button></div>
    </section>

    <Dialog open={addOpen} onOpenChange={setAddOpen}><DialogContent className="setup-upload-dialog"><DialogHeader><p className="eyebrow">ADD MATERIALS</p><DialogTitle>자료 추가</DialogTitle><DialogDescription>PDF, 문서, 이미지 파일을 추가하세요. 업로드한 자료는 같은 목록에서 상태와 역할을 관리합니다.</DialogDescription></DialogHeader><form onSubmit={upload} className="setup-upload-form"><label className="setup-dropzone"><Upload size={24} aria-hidden="true" /><strong>파일을 선택하세요</strong><span>PDF, DOCX, TXT, Markdown, 이미지</span><Input name="rulebooks" aria-label="자료 파일" type="file" accept=".pdf,.docx,.txt,.md,.png,.jpg,.jpeg,.tif,.tiff,.bmp" multiple onChange={event => { const files = Array.from(event.currentTarget.files ?? []); setDrafts(files.map((file, index) => ({ file, originalFilename: file.name, documentType: 'STORYBOOK', idempotencyKey: createIdempotencyKey(file, index) }))) }} /></label>{drafts.length ? <ul className="setup-draft-list" aria-label="추가할 파일 목록">{drafts.map(draft => <li key={draft.idempotencyKey}>{draft.originalFilename}</li>)}</ul> : null}{results.some(result => result.status === 'VALIDATION_FAILED') ? <p className="setup-upload-warning">일부 파일은 사용할 수 없습니다. 닫은 뒤 상단 안내를 확인하세요.</p> : null}<DialogFooter><Button type="button" variant="ghost" onClick={() => setAddOpen(false)}>취소</Button><Button type="submit" disabled={uploading || drafts.length === 0}>{uploading ? '추가 중…' : '자료 추가'}</Button></DialogFooter></form></DialogContent></Dialog>

    <Sheet open={Boolean(sourcePreview)} onOpenChange={open => { if (!open) setSourcePreview(null) }}><SheetContent className="setup-preview-sheet"><SheetHeader><p className="eyebrow">SOURCE PREVIEW</p><SheetTitle>{sourcePreview?.originalFilename ?? '자료 미리보기'}</SheetTitle></SheetHeader>{sourcePreview ? <div className="setup-preview-content"><p className="setup-preview-meta">{sourcePreview.format} · 원문 미리보기</p>{sourcePreview.warnings.length ? <p className="setup-preview-warning">{sourcePreview.warnings.join(', ')}</p> : null}<ol aria-label="원문 줄 미리보기">{sourcePreview.spans.map(span => <li key={`${span.lineNumber}-${span.startInclusive}-${span.endExclusive}`}><small>{span.path.join(' › ')}{span.pageNumber ? ` · p.${span.pageNumber}` : ''}</small><pre>{span.text || ' '}</pre></li>)}</ol></div> : null}</SheetContent></Sheet>

    <Dialog open={Boolean(preparationBundle)} onOpenChange={open => { if (!open) setPreparationBundle(null) }}><DialogContent className="setup-preparation-dialog"><DialogHeader><p className="eyebrow">ADVENTURE PREPARATION</p><DialogTitle>모험 준비</DialogTitle><DialogDescription>선택한 자료를 바탕으로 플레이에 필요한 내용을 자동으로 준비합니다.</DialogDescription></DialogHeader>{preparationBundle ? <PreparationFlow api={api} playerId={playerId} bundle={preparationBundle} sessionApi={sessionApi} onError={setMessage} /> : null}<DialogFooter><Button variant="ghost" onClick={() => setPreparationBundle(null)}>닫기</Button></DialogFooter></DialogContent></Dialog>
  </Container>
}

function SectionHeading({ number, title, description, id, action }: { number: string; title: string; description: string; id: string; action?: React.ReactNode }) {
  return <div className="setup-section-heading"><div className="setup-section-number">{number}</div><div className="setup-section-copy"><h2 id={id}>{title}</h2><p>{description}</p></div>{action ? <div className="setup-section-action">{action}</div> : null}</div>
}
