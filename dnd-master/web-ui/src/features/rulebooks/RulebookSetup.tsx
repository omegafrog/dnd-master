import { type FormEvent, useCallback, useEffect, useState } from 'react'
import type {
  BatchRulebookView,
  DocumentType,
  KnowledgeDocumentView,
  RulebookUploadDraft,
  SetupApi,
  SourcePreviewView,
} from './SetupApi'
import { ScenarioSetup } from '../scenarios/ScenarioSetup'

const batchStatusText: Record<BatchRulebookView['status'], string> = {
  ACCEPTED: '사용 준비 완료',
  VALIDATION_FAILED: '검증 실패',
}

const knowledgeStatusText: Record<KnowledgeDocumentView['status'], string> = {
  UPLOADED: '대기 중',
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

export function RulebookSetup({ api, playerId }: { api: SetupApi; playerId: string }) {
  const [drafts, setDrafts] = useState<PendingDocument[]>([])
  const [results, setResults] = useState<BatchRulebookView[]>([])
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [message, setMessage] = useState('')
  const [uploading, setUploading] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [ruleSetMessage, setRuleSetMessage] = useState('')
  const [sourcePreview, setSourcePreview] = useState<SourcePreviewView | null>(null)

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

  return (
    <main>
      <h1>자료와 모험 설정</h1>
      <p role="status" aria-live="polite">{message}</p>
      <section aria-labelledby="rulebook-heading">
        <h2 id="rulebook-heading">자료 업로드</h2>
        <form onSubmit={upload}>
          <label>
            자료 파일
            <input
              name="rulebooks"
              type="file"
              accept=".pdf,.docx,.txt,.md"
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
          <button type="submit" disabled={uploading || drafts.length === 0}>{uploading ? '업로드 중…' : '자료 업로드'}</button>
        </form>
        {drafts.length > 0 && (
          <ul aria-label="자료 유형 선택">
            {drafts.map((draft, index) => (
              <li key={draft.idempotencyKey}>
                <label>
                  {draft.originalFilename} 유형
                  <select
                    aria-label={`${draft.originalFilename} 유형`}
                    value={draft.documentType}
                    onChange={event => updateDraftType(index, event.currentTarget.value as DocumentType)}
                  >
                    <option value="RULEBOOK">{documentTypeLabel.RULEBOOK}</option>
                    <option value="STORYBOOK">{documentTypeLabel.STORYBOOK}</option>
                  </select>
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
                  <input
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
          <button onClick={() => void saveRuleSet()}>룰 세트 저장</button>
        )}
        {ruleSetMessage && <p role="status">{ruleSetMessage}</p>}
        <section aria-labelledby="document-status-heading">
          <h3 id="document-status-heading">문서 상태</h3>
          <ul aria-label="문서 상태 목록">
            {documents.map(document => (
              <li key={document.knowledgeDocumentId}>
                <span>{document.originalFilename}</span>
                <span> - {knowledgeStatusText[document.status]}</span>
                {document.extractionVersion != null ? <span> (v{document.extractionVersion})</span> : null}
                {document.failureReason ? <span> ({document.failureReason})</span> : null}
                {document.warnings?.length ? <span> [경고: {document.warnings.join(', ')}]</span> : null}
                {document.format === 'TXT' && (document.status === 'EXTRACTED' || document.status === 'PARTIAL_CONFIRMED') ? (
                  <button type="button" onClick={() => void previewDocument(document.knowledgeDocumentId)}>
                    미리보기
                  </button>
                ) : null}
                {document.status === 'FAILED' ? (
                  <button type="button" onClick={() => void retryDocument(document.knowledgeDocumentId)}>
                    다시 처리
                  </button>
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
                    <span>{span.locator}</span>
                    <pre>{span.text || ' '}</pre>
                  </li>
                ))}
              </ol>
            </section>
          ) : null}
        </section>
      </section>
      <ScenarioSetup api={api} onError={setMessage} />
    </main>
  )
}
