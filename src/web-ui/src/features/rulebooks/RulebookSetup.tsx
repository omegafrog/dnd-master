import { type FormEvent, useCallback, useEffect, useState } from 'react'
import type {
  BatchRulebookView,
  DocumentType,
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
import { Select } from '../../components/ui/select'

const batchStatusText: Record<BatchRulebookView['status'], string> = {
  ACCEPTED: '사용 준비 완료',
  VALIDATION_FAILED: '검증 실패',
}

const knowledgeStatusText: Record<KnowledgeDocumentView['status'], string> = {
  UPLOADED: '대기 중',
  NEEDS_INPUT: '추가 입력 필요',
  QUEUED: '대기 중',
  PROCESSING: '처리 중',
  INDEXED: '색인 완료',
  FAILED: '실패',
  EXTRACTED: '추출 완료',
  PARTIAL_AWAITING_CONFIRMATION: '확인 대기',
  PARTIAL_CONFIRMED: '확인 완료',
  REJECTED: '실패',
}

const documentTypeLabel: Record<DocumentType, string> = {
  RULEBOOK: 'RULEBOOK',
  STORYBOOK: 'STORYBOOK',
}

type PendingDocument = RulebookUploadDraft & { originalFilename: string }

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
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [message, setMessage] = useState('')
  const [uploading, setUploading] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [ruleSetMessage, setRuleSetMessage] = useState('')
  const [sourcePreview, setSourcePreview] = useState<SourcePreviewView | null>(null)
  const [bundles, setBundles] = useState<ScenarioBundleView[]>([])
  const [selectedBundleIds, setSelectedBundleIds] = useState<Set<string>>(new Set())
  const [selectedBundle, setSelectedBundle] = useState<ScenarioBundleView | null>(null)
  const [deletingBundleId, setDeletingBundleId] = useState<string | null>(null)
  const [deletingBundles, setDeletingBundles] = useState(false)

  const refreshDocuments = useCallback(async () => {
    try {
      setDocuments(await api.listKnowledgeDocuments(playerId))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '문서 목록을 불러오지 못했습니다.')
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
      setMessage(error instanceof Error ? error.message : '시나리오 번들 목록을 불러오지 못했습니다.')
    }
  }, [api])

  useEffect(() => {
    void refreshBundles()
  }, [refreshBundles])

  async function openBundle(bundleId: string) {
    try {
      if (!api.getScenarioBundle) return
      window.localStorage.setItem('dnd-selected-bundle-id', bundleId)
      window.dispatchEvent(new Event('dnd-selected-bundle-change'))
      setSelectedBundle(await api.getScenarioBundle(bundleId))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '시나리오 번들을 불러오지 못했습니다.')
    }
  }

  async function deleteBundle(bundleId: string) {
    if (!api.deleteScenarioBundle || !window.confirm('이 번들과 연결된 컴파일 패키지도 삭제합니다. 계속할까요?')) return
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
      setMessage('시나리오 번들을 삭제했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '시나리오 번들을 삭제하지 못했습니다.')
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
    if (!window.confirm(`선택한 ${selectedBundleIds.size}개 번들과 연결된 컴파일 패키지를 모두 삭제할까요?`)) return

    setDeletingBundles(true)
    const bundleIds = [...selectedBundleIds]
    const outcomes = await Promise.allSettled(bundleIds.map(bundleId => api.deleteScenarioBundle!(bundleId)))
    const deletedIds = bundleIds.filter((_, index) => outcomes[index].status === 'fulfilled')
    const failedIds = bundleIds.filter((_, index) => outcomes[index].status === 'rejected')
    setBundles(current => current.filter(bundle => !deletedIds.includes(bundle.bundleId)))
    setSelectedBundleIds(new Set(failedIds))
    setSelectedBundle(current => current && deletedIds.includes(current.bundleId) ? null : current)
    setMessage(failedIds.length ? `${deletedIds.length}개 삭제, ${failedIds.length}개 삭제 실패` : `${deletedIds.length}개 번들을 삭제했습니다.`)
    setDeletingBundles(false)
  }

  function updateDraftType(index: number, documentType: DocumentType) {
    setDrafts(current => current.map((draft, draftIndex) => draftIndex === index ? { ...draft, documentType } : draft))
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
      setSelectedIds(prev => {
        const next = new Set(prev)
        uploaded.forEach(result => {
          if (result.status === 'ACCEPTED' && result.knowledgeDocumentId) next.add(result.knowledgeDocumentId)
        })
        return next
      })
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

  async function previewDocument(knowledgeDocumentId: string) {
    try {
      setSourcePreview(null)
      setSourcePreview(await api.getSourcePreview(knowledgeDocumentId))
    } catch (error) {
      setSourcePreview(null)
      setMessage(error instanceof Error ? error.message : '미리보기를 불러오지 못했습니다.')
    }
  }

  async function saveRuleSet() {
    setRuleSetMessage('')
    try {
      await api.saveRuleSet([...selectedIds])
      setRuleSetMessage('룰 세트가 저장되었습니다.')
    } catch (error) {
      setRuleSetMessage(error instanceof Error ? error.message : '룰 세트를 저장하지 못했습니다.')
    }
  }

  const Container = asMain ? 'main' : 'section'

  return (
    <Container className="setup-page">
      <div className="page-heading"><div><p className="eyebrow">ADVENTURE WORKSHOP</p><h1>자료와 모험 설정</h1><p>룰북과 시나리오 자료를 준비하고 플레이 가능한 번들을 만드세요.</p></div></div>
      <p role="status" aria-live="polite">{message}</p>
      <section className="setup-panel setup-upload-panel" aria-labelledby="rulebook-heading">
        <h2 id="rulebook-heading">자료 업로드</h2>
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
                  documentType: 'RULEBOOK',
                  idempotencyKey: createIdempotencyKey(file, index),
                })))
              }}
            />
          </label>
          <Button type="submit" disabled={uploading || drafts.length === 0}>{uploading ? '업로드 중…' : '자료 업로드'}</Button>
        </form>
        {drafts.length > 0 && (
          <ul aria-label="자료 유형 선택">
            {drafts.map((draft, index) => (
              <li key={draft.idempotencyKey}>
                <label>
                  {draft.originalFilename} 유형
                  <Select
                    aria-label={`${draft.originalFilename} 유형`}
                    value={draft.documentType}
                    onChange={event => updateDraftType(index, event.currentTarget.value as DocumentType)}
                  >
                    <option value="RULEBOOK">{documentTypeLabel.RULEBOOK}</option>
                    <option value="STORYBOOK">{documentTypeLabel.STORYBOOK}</option>
                  </Select>
                </label>
              </li>
            ))}
          </ul>
        )}
        <ul aria-label="자료 처리 상태">
          {results.map(result => (
            <li key={result.knowledgeDocumentId ?? `${result.originalFilename}-${result.status}`}>
              {result.knowledgeDocumentId ? (
                <label>
                  <Input
                    type="checkbox"
                    checked={selectedIds.has(result.knowledgeDocumentId)}
                    onChange={() => toggleSelected(result.knowledgeDocumentId!)}
                  />
                  {result.originalFilename}
                </label>
              ) : (
                <span>{result.originalFilename}</span>
              )}
              : {batchStatusText[result.status]}
              {result.failureReason ? <span> ({result.failureReason})</span> : null}
            </li>
          ))}
        </ul>
        {results.some(result => result.status === 'ACCEPTED') && (
          <Button onClick={() => void saveRuleSet()}>룰 세트 저장</Button>
        )}
        {ruleSetMessage && <p role="status">{ruleSetMessage}</p>}
        <section className="setup-subpanel" aria-labelledby="document-status-heading">
          <h3 id="document-status-heading">문서 상태</h3>
          <ul aria-label="문서 상태 목록">
            {documents.map(document => (
              <li key={document.knowledgeDocumentId}>
                <span>{document.originalFilename}</span>
                <span> - {knowledgeStatusText[document.status]}</span>
                {document.extractionVersion != null ? <span> (v{document.extractionVersion})</span> : null}
                {document.failureReason ? <span> ({document.failureReason})</span> : null}
                {document.warnings?.length ? <span> [경고: {document.warnings.join(', ')}]</span> : null}
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
        </section>
      </section>
      <Card className="setup-panel setup-bundle-list" aria-labelledby="saved-bundles-heading">
        <CardContent>
        <div className="bundle-list-heading">
          <h2 id="saved-bundles-heading">생성한 번들</h2>
          <Button type="button" variant="outline" onClick={() => void refreshBundles()}>번들 목록 새로고침</Button>
        </div>
        {bundles.length === 0 ? <p>생성한 번들이 없습니다.</p> : (
          <>
            <div className="bundle-list-toolbar">
              <div className="bundle-select-all">
                <Checkbox aria-label="전체 번들 선택" checked={selectedBundleIds.size === bundles.length} onCheckedChange={toggleAllBundles} />
                <span>전체 선택</span>
              </div>
              <Button type="button" variant="destructive" disabled={selectedBundleIds.size === 0 || deletingBundles} onClick={() => void deleteSelectedBundles()}>
                {deletingBundles ? '삭제 중…' : `선택 삭제 (${selectedBundleIds.size})`}
              </Button>
            </div>
            <ul aria-label="생성한 번들 목록">
            {bundles.map(bundle => (
              <li key={bundle.bundleId}>
                <Checkbox aria-label={`${bundle.bundleId} 선택`} checked={selectedBundleIds.has(bundle.bundleId)} onCheckedChange={() => toggleBundleSelection(bundle.bundleId)} />
                <span>{bundle.bundleId} · v{bundle.currentRevision} · 문서 {bundle.documents.length}개</span>
                <Button type="button" variant="outline" onClick={() => void openBundle(bundle.bundleId)}>번들 열기</Button>
                <Button type="button" variant="outline" onClick={() => { window.localStorage.setItem('dnd-selected-bundle-id', bundle.bundleId); window.dispatchEvent(new Event('dnd-selected-bundle-change')); window.location.hash = `#/bundles/${bundle.bundleId}` }}>번들 화면</Button>
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
      <ScenarioSetup
        api={api}
        playerId={playerId}
        onError={setMessage}
        sessionApi={sessionApi}
        availableDocuments={documents}
        initialBundle={selectedBundle}
        onBundleSaved={savedBundle => {
          window.localStorage.setItem('dnd-selected-bundle-id', savedBundle.bundleId)
          window.dispatchEvent(new Event('dnd-selected-bundle-change'))
          setSelectedBundle(savedBundle)
          setBundles(current => [savedBundle, ...current.filter(item => item.bundleId !== savedBundle.bundleId)])
        }}
      />
    </Container>
  )
}
