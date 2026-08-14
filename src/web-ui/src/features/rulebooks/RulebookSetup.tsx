import { type FormEvent, useCallback, useEffect, useState } from 'react'
import type {
  BatchRulebookView,
  KnowledgeDocumentView,
  RulebookUploadDraft,
  ScenarioBundleView,
  SetupApi,
  SourcePreviewView,
} from './SetupApi'
import { ScenarioSetup } from '../scenarios/ScenarioSetup'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import { Button } from '../../components/ui/button'
import { Card, CardContent } from '../../components/ui/card'
import { Checkbox } from '../../components/ui/checkbox'
import { Input } from '../../components/ui/input'
import { Progress } from '../../components/ui/progress'

const batchStatusText: Record<BatchRulebookView['status'], string> = {
  ACCEPTED: '사용 준비 완료',
  VALIDATION_FAILED: '검증 실패',
}

const knowledgeStatusText: Record<KnowledgeDocumentView['status'], string> = {
  UPLOADED: '대기 중',
  NEEDS_INPUT: '추가 입력 필요',
  QUEUED: '대기 중',
  PROCESSING: '자료 준비 중',
  INDEXED: '사용 준비 완료',
  FAILED: '자료 준비 실패',
  EXTRACTED: '자료 확인 완료',
  PARTIAL_AWAITING_CONFIRMATION: '확인 필요',
  PARTIAL_CONFIRMED: '사용 준비 완료',
  REJECTED: '자료 사용 불가',
}

const indexingFinishedStatuses = new Set<KnowledgeDocumentView['status']>(['INDEXED', 'PARTIAL_CONFIRMED'])

type PendingDocument = RulebookUploadDraft & { originalFilename: string }
type CatalogRulebook = { catalogRevisionId: string; edition: string; displayName: string; rulebookId: string | null; revisionNumber: number; status: string }

function createIdempotencyKey(file: File, index: number) {
  const random = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${file.name}-${index}-${random}`
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
  const [selectedUploadedIds, setSelectedUploadedIds] = useState<Set<string>>(new Set())
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [message, setMessage] = useState('')
  const [uploading, setUploading] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [sourcePreview, setSourcePreview] = useState<SourcePreviewView | null>(null)
  const [bundles, setBundles] = useState<ScenarioBundleView[]>([])
  const [selectedBundleIds, setSelectedBundleIds] = useState<Set<string>>(new Set())
  const [selectedBundle, setSelectedBundle] = useState<ScenarioBundleView | null>(null)
  const [preparationBundle, setPreparationBundle] = useState<ScenarioBundleView | null>(null)
  const [preparationLoading, setPreparationLoading] = useState(false)
  const [deletingBundleId, setDeletingBundleId] = useState<string | null>(null)
  const [deletingBundles, setDeletingBundles] = useState(false)
  const [catalogRulebooks, setCatalogRulebooks] = useState<CatalogRulebook[]>([])

  const refreshDocuments = useCallback(async (): Promise<KnowledgeDocumentView[] | null> => {
    try {
      const loaded = await api.listKnowledgeDocuments(playerId)
      setDocuments(loaded)
      return loaded
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '문서 목록을 불러오지 못했습니다.')
      return null
    }
  }, [api, playerId])

  useEffect(() => {
    void refreshDocuments()
  }, [refreshDocuments])

  const refreshBundles = useCallback(async () => {
    if (!api.listScenarioBundles) return
    try {
      const loadedBundles = await api.listScenarioBundles()
      setBundles(loadedBundles)
      setSelectedBundleIds(current => new Set([...current].filter(id => loadedBundles.some(bundle => bundle.bundleId === id))))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 자료 목록을 불러오지 못했습니다.')
    }
  }, [api])

  useEffect(() => {
    void refreshBundles()
  }, [refreshBundles])

  useEffect(() => {
    const hasNonTerminalDocuments = documents.some(document => !indexingFinishedStatuses.has(document.status) && document.status !== 'FAILED' && document.status !== 'NEEDS_INPUT' && document.status !== 'REJECTED')
    if (!hasNonTerminalDocuments) return

    let active = true
    let polling = false
    const poll = async () => {
      if (!active || polling) return
      polling = true
      try {
        const loaded = await refreshDocuments()
        if (!active || !loaded) return
        if (loaded.every(document => indexingFinishedStatuses.has(document.status) || document.status === 'FAILED' || document.status === 'NEEDS_INPUT' || document.status === 'REJECTED')) {
          active = false
        }
      } finally {
        polling = false
      }
    }

    const timer = window.setInterval(() => void poll(), 1000)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [documents, refreshDocuments])

  useEffect(() => {
    void fetch('/api/v1/rulebook-catalog').then(response => response.ok ? response.json() : []).then((items: CatalogRulebook[]) => {
      setCatalogRulebooks(items.filter(item => item.status === 'READY' && item.rulebookId))
    }).catch(() => setCatalogRulebooks([]))
  }, [])

  async function openPreparation(bundleId: string) {
    if (!api.getScenarioBundle) return
    setPreparationLoading(true)
    setMessage('')
    try {
      setPreparationBundle(await api.getScenarioBundle(bundleId))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 자료를 불러오지 못했습니다.')
    } finally {
      setPreparationLoading(false)
    }
  }

  async function deleteBundle(bundleId: string) {
    if (!api.deleteScenarioBundle || !window.confirm('이 자료와 연결된 모험 준비 결과도 삭제합니다. 계속할까요?')) return
    setDeletingBundleId(bundleId)
    try {
      await api.deleteScenarioBundle(bundleId)
      setBundles(current => current.filter(bundle => bundle.bundleId !== bundleId))
      setSelectedBundleIds(current => {
        const next = new Set(current)
        next.delete(bundleId)
        return next
      })
      setSelectedBundle(current => current?.bundleId === bundleId ? null : current)
      setMessage('모험 자료를 삭제했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험 자료를 삭제하지 못했습니다.')
    } finally {
      setDeletingBundleId(null)
    }
  }

  function toggleBundleSelection(bundleId: string) {
    setSelectedBundleIds(current => {
      const next = new Set(current)
      if (next.has(bundleId)) next.delete(bundleId)
      else next.add(bundleId)
      return next
    })
  }

  function toggleAllBundles() {
    setSelectedBundleIds(current => current.size === bundles.length
      ? new Set()
      : new Set(bundles.map(bundle => bundle.bundleId)))
  }

  async function deleteSelectedBundles() {
    if (!api.deleteScenarioBundle || selectedBundleIds.size === 0) return
    if (!window.confirm(`선택한 ${selectedBundleIds.size}개 자료와 연결된 모험 준비 결과를 모두 삭제할까요?`)) return

    setDeletingBundles(true)
    const bundleIds = [...selectedBundleIds]
    const outcomes = await Promise.allSettled(bundleIds.map(bundleId => api.deleteScenarioBundle!(bundleId)))
    const deletedIds = bundleIds.filter((_, index) => outcomes[index].status === 'fulfilled')
    const failedIds = bundleIds.filter((_, index) => outcomes[index].status === 'rejected')
    setBundles(current => current.filter(bundle => !deletedIds.includes(bundle.bundleId)))
    setSelectedBundleIds(new Set(failedIds))
    setSelectedBundle(current => current && deletedIds.includes(current.bundleId) ? null : current)
    setMessage(failedIds.length ? `${deletedIds.length}개 삭제, ${failedIds.length}개 삭제 실패` : `${deletedIds.length}개 자료를 삭제했습니다.`)
    setDeletingBundles(false)
  }

  function toggleSelected(rulebookId: string) {
    setSelectedIds(prev => {
      const next = new Set(prev)
      if (next.has(rulebookId)) next.delete(rulebookId)
      else next.add(rulebookId)
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
      await refreshDocuments()
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
      setMessage('다시 처리했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '다시 처리하지 못했습니다.')
    }
  }

  async function deleteDocument(document: KnowledgeDocumentView) {
    if (!api.deleteKnowledgeDocument || !window.confirm(`${document.originalFilename}을(를) 삭제할까요?`)) return
    try {
      await api.deleteKnowledgeDocument(document.knowledgeDocumentId)
      setDocuments(current => current.filter(item => item.knowledgeDocumentId !== document.knowledgeDocumentId))
      setResults(current => current.filter(item => item.knowledgeDocumentId !== document.knowledgeDocumentId))
      setSelectedUploadedIds(current => {
        const next = new Set(current)
        next.delete(document.knowledgeDocumentId)
        return next
      })
      setMessage('자료를 삭제했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '자료를 삭제하지 못했습니다.')
    }
  }

  async function previewDocument(knowledgeDocumentId: string) {
    try {
      setSourcePreview(null)
      setSourcePreview(await api.getSourcePreview(knowledgeDocumentId))
    } catch (error) {
      setSourcePreview(null)
      setMessage(error instanceof Error ? error.message : '미리보기를 불러오지 못했습니다.')
    }
  }

  const Container = asMain ? 'main' : 'section'
  const uploadedDocuments = documents.filter(document => document.documentType === 'STORYBOOK')

  return (
    <Container className="setup-page">
      <div className="page-heading"><div><p className="eyebrow">ADVENTURE WORKSHOP</p><h1>자료와 모험 설정</h1><p>룰북과 시나리오 자료를 준비하고 플레이할 모험을 만드세요.</p></div></div>
      <p role="status" aria-live="polite">{message}</p>
      <section className="setup-panel setup-catalog-panel" aria-labelledby="catalog-rulebook-heading">
        <h2 id="catalog-rulebook-heading">룰북 선택</h2>
        {catalogRulebooks.length === 0 ? <p>사용 가능한 룰북이 없습니다. 관리자가 준비하면 여기에 표시됩니다.</p> : <ul aria-label="룰북 목록">{catalogRulebooks.map(rulebook => <li key={rulebook.catalogRevisionId}><label><Checkbox checked={selectedIds.has(rulebook.rulebookId!)} onCheckedChange={() => toggleSelected(rulebook.rulebookId!)} />{rulebook.displayName} · revision {rulebook.revisionNumber}</label></li>)}</ul>}
      </section>
      <section className="setup-panel setup-upload-panel" aria-labelledby="rulebook-heading">
        <h2 id="rulebook-heading">스토리북 업로드</h2>
        <form onSubmit={upload}>
          <label>
            자료 파일
            <Input
              name="rulebooks"
              type="file"
              accept=".pdf,.docx,.txt,.md,.png,.jpg,.jpeg,.tif,.tiff,.bmp"
              multiple
              onChange={event => {
                const files = Array.from(event.currentTarget.files ?? [])
                setDrafts(files.map((file, index) => ({
                  file,
                  originalFilename: file.name,
                  documentType: 'STORYBOOK',
                  idempotencyKey: createIdempotencyKey(file, index),
                })))
              }}
            />
          </label>
          <Button type="submit" disabled={uploading || drafts.length === 0}>{uploading ? '업로드 중…' : '자료 업로드'}</Button>
        </form>
        <ul aria-label="자료 처리 상태">
          {results.map(result => (
            <li key={result.knowledgeDocumentId ?? `${result.originalFilename}-${result.status}`}>
              {result.knowledgeDocumentId ? (
                <label className="upload-result-label">
                  <Input
                    type="checkbox"
                    checked={selectedIds.has(result.knowledgeDocumentId)}
                    onChange={() => toggleSelected(result.knowledgeDocumentId!)}
                  />
                  {result.originalFilename}
                </label>
              ) : (
                <span className="upload-result-label">{result.originalFilename}</span>
              )}
              {result.status === 'VALIDATION_FAILED' ? (
                <span>: {batchStatusText[result.status]}{result.failureReason ? ` (${result.failureReason})` : ''}</span>
              ) : null}
            </li>
          ))}
        </ul>
        {uploadedDocuments.length > 0 ? <section className="setup-subpanel" aria-labelledby="document-status-heading">
          <h3 id="document-status-heading">올려둔 자료 상태</h3>
          {(() => {
            const trackedDocuments = uploadedDocuments
            const pendingDocuments = trackedDocuments.filter(document => !indexingFinishedStatuses.has(document.status) && document.status !== 'FAILED' && document.status !== 'NEEDS_INPUT' && document.status !== 'REJECTED')
            // The server owns progress. Documents without a progress snapshot must
            // not be treated as 0%, otherwise an unrelated pending/failed document
            // hides the authoritative percentage of the document being processed.
            const progressValues = trackedDocuments
              .filter(document => document.progress != null || indexingFinishedStatuses.has(document.status))
              .map(document => document.progress?.percent ?? 100)
            const overallProgress = progressValues.length ? Math.round(progressValues.reduce((sum, value) => sum + value, 0) / progressValues.length) : 0
            return trackedDocuments.length > 0 && pendingDocuments.length > 0 ? (
              <div className="document-progress" aria-label="자료 준비 진행률">
                <p>자료를 준비하는 중입니다. {trackedDocuments.length - pendingDocuments.length}/{trackedDocuments.length}개 완료</p>
                <Progress value={overallProgress} aria-label="전체 자료 준비 진행률" />
              </div>
            ) : null
          })()}
          <ul aria-label="문서 상태 목록">
            {uploadedDocuments.map(document => (
              <li key={document.knowledgeDocumentId} className="uploaded-document-row">
                <label className="uploaded-document-select">
                  <Input
                    type="checkbox"
                    aria-label={`${document.originalFilename} 모험 자료 선택`}
                    checked={selectedUploadedIds.has(document.knowledgeDocumentId)}
                    disabled={!indexingFinishedStatuses.has(document.status)}
                    onChange={() => setSelectedUploadedIds(current => {
                      const next = new Set(current)
                      if (next.has(document.knowledgeDocumentId)) next.delete(document.knowledgeDocumentId)
                      else next.add(document.knowledgeDocumentId)
                      return next
                    })}
                  />
                </label>
                <span className="uploaded-document-name">{document.originalFilename}</span>
                <span> - {knowledgeStatusText[document.status]}</span>
                {document.progress ? (
                  <span className="uploaded-document-progress">
                    <span>{document.progress.stage} {document.progress.percent}%</span>
                    <Progress value={document.progress.percent} aria-label={`${document.originalFilename} 자료 준비 진행률`} />
                  </span>
                ) : null}
                {document.extractionVersion != null ? <span> (v{document.extractionVersion})</span> : null}
                {document.failureReason ? <span> ({document.failureReason})</span> : null}
                {document.warnings?.length ? <span> [경고: {document.warnings.join(', ')}]</span> : null}
                <span className="uploaded-document-actions">
                {(document.status === 'EXTRACTED' || document.status === 'PARTIAL_CONFIRMED') ? (
                  <Button type="button" variant="outline" onClick={() => void previewDocument(document.knowledgeDocumentId)}>
                    미리보기
                  </Button>
                ) : null}
                {document.status === 'FAILED' ? (
                  <Button type="button" variant="outline" onClick={() => void retryDocument(document.knowledgeDocumentId)}>
                    다시 처리
                  </Button>
                ) : null}
                {api.deleteKnowledgeDocument ? (
                  <Button className="uploaded-document-delete" type="button" variant="outline" onClick={() => void deleteDocument(document)}>
                    삭제
                  </Button>
                ) : null}
                </span>
              </li>
            ))}
          </ul>
          {sourcePreview ? (
            <section aria-labelledby="source-preview-heading">
              <h4 id="source-preview-heading">{sourcePreview.originalFilename} 미리보기</h4>
              <p>{sourcePreview.format} · {sourcePreview.status} · v{sourcePreview.extractionVersion}</p>
              {sourcePreview.warnings.length ? <p>경고: {sourcePreview.warnings.join(', ')}</p> : null}
              <ol aria-label="원문 줄 미리보기">
                {sourcePreview.spans.map(span => (
                  <li key={`${span.lineNumber}-${span.startInclusive}-${span.endExclusive}`}>
                    <span>{span.kind} · {span.path.join(' > ')}</span>
                    {span.sourceMethod ? <span> · {span.sourceMethod}</span> : null}
                    {span.confidence != null ? <span> · {span.confidence.toFixed(1)}%</span> : null}
                    {span.pageNumber ? <span> · p{span.pageNumber}</span> : null}
                    {span.bounds ? <span> · [{span.bounds.left.toFixed(3)}, {span.bounds.top.toFixed(3)}, {span.bounds.right.toFixed(3)}, {span.bounds.bottom.toFixed(3)}]</span> : null}
                    <pre>{span.text || ' '}</pre>
                  </li>
                ))}
              </ol>
              {sourcePreview.assets.length ? (
                <section aria-labelledby="preview-assets-heading">
                  <h5 id="preview-assets-heading">첨부 자산</h5>
                  <ul>
                    {sourcePreview.assets.map(asset => (
                      <li key={`${asset.kind}-${asset.locator}`}>
                        {asset.kind} · {asset.locator}
                        {asset.contentType ? ` · ${asset.contentType}` : ''}
                        {asset.pageNumber ? ` · p${asset.pageNumber}` : ''}
                      </li>
                    ))}
                  </ul>
                </section>
              ) : null}
            </section>
          ) : null}
        </section> : null}
      </section>
      <Card className="setup-panel setup-bundle-list" aria-labelledby="saved-bundles-heading">
        <CardContent>
        <div className="bundle-list-heading">
          <h2 id="saved-bundles-heading">저장된 모험 자료</h2>
          <Button type="button" variant="outline" onClick={() => void refreshBundles()}>목록 새로고침</Button>
        </div>
        {bundles.length === 0 ? <p>저장된 모험 자료가 없습니다.</p> : (
          <>
            <div className="bundle-list-toolbar">
              <div className="bundle-select-all">
                <Checkbox aria-label="전체 자료 선택" checked={selectedBundleIds.size === bundles.length} onCheckedChange={toggleAllBundles} />
                <span>전체 선택</span>
              </div>
              <Button type="button" variant="destructive" disabled={selectedBundleIds.size === 0 || deletingBundles} onClick={() => void deleteSelectedBundles()}>
                {deletingBundles ? '삭제 중…' : `선택한 자료 삭제 (${selectedBundleIds.size})`}
              </Button>
            </div>
            <ul aria-label="저장된 모험 자료 목록">
            {bundles.map(bundle => (
              <li key={bundle.bundleId}>
                <Checkbox aria-label={`${bundle.name ?? '모험 자료'} 선택`} checked={selectedBundleIds.has(bundle.bundleId)} onCheckedChange={() => toggleBundleSelection(bundle.bundleId)} />
                <span><strong>{bundle.name ?? '이름 없는 모험 자료'}</strong> · {bundle.rulebookEdition === 'DND_5E_2014' ? 'D&D 5판' : bundle.rulebookEdition === 'DND_5E_2024' ? 'D&D 5.5판' : '룰북 미지정'} · 자료 {bundle.documents.length}개</span>
                <Button type="button" onClick={() => void openPreparation(bundle.bundleId)}>게임 준비</Button>
                <Button type="button" variant="destructive" disabled={deletingBundleId === bundle.bundleId} onClick={() => void deleteBundle(bundle.bundleId)}>
                  {deletingBundleId === bundle.bundleId ? '삭제 중…' : '삭제'}
                </Button>
              </li>
            ))}
            </ul>
          </>
        )}
        </CardContent>
      </Card>
      {selectedUploadedIds.size > 0 ? <ScenarioSetup
          api={api}
          playerId={playerId}
          onError={setMessage}
          sessionApi={sessionApi}
          availableDocuments={documents.filter(document => selectedUploadedIds.has(document.knowledgeDocumentId))}
          initialBundle={selectedBundle}
          onBundleSaved={savedBundle => {
            window.localStorage.setItem('dnd-selected-bundle-id', savedBundle.bundleId)
            window.dispatchEvent(new Event('dnd-selected-bundle-change'))
            setSelectedBundle(savedBundle)
            setBundles(current => [savedBundle, ...current.filter(item => item.bundleId !== savedBundle.bundleId)])
          }}
        /> : null}
      {preparationBundle || preparationLoading ? (
        <div className="modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) setPreparationBundle(null) }}>
          <section className="modal-dialog preparation-modal" role="dialog" aria-modal="true" aria-labelledby="preparation-modal-title">
            <div className="modal-dialog-heading">
              <div>
                <p className="eyebrow">ADVENTURE PREPARATION</p>
                <h2 id="preparation-modal-title">게임 준비</h2>
              </div>
              <Button type="button" variant="outline" onClick={() => setPreparationBundle(null)}>닫기</Button>
            </div>
            {preparationLoading ? <p>모험 자료를 불러오는 중…</p> : preparationBundle ? (
              <ScenarioSetup
                api={api}
                playerId={playerId}
                sessionApi={sessionApi}
                initialBundle={preparationBundle}
                preparationOnly
                onError={setMessage}
              />
            ) : null}
          </section>
        </div>
      ) : null}
    </Container>
  )
}
