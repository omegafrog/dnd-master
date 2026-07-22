import { type FormEvent, useState } from 'react'
import type { BatchRulebookView, DocumentType, RulebookUploadDraft, SetupApi } from './SetupApi'
import { ScenarioSetup } from '../scenarios/ScenarioSetup'

const batchStatusText: Record<BatchRulebookView['status'], string> = {
  ACCEPTED: '사용 준비 완료',
  VALIDATION_FAILED: '검증 실패',
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
  const [message, setMessage] = useState('')
  const [uploading, setUploading] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [ruleSetMessage, setRuleSetMessage] = useState('')

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
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '자료를 업로드하지 못했습니다.')
    } finally {
      setUploading(false)
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
      </section>
      <ScenarioSetup api={api} onError={setMessage} />
    </main>
  )
}
