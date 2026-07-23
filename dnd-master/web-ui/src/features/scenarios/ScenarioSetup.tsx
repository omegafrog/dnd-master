import { type FormEvent, useEffect, useState } from 'react'
import type {
  KnowledgeDocumentView,
  ScenarioBundleDraft,
  ScenarioBundleRole,
  ScenarioBundleView,
  SetupApi,
  StorySourceEvidenceView,
} from '../rulebooks/SetupApi'

const roleLabel: Record<ScenarioBundleRole, string> = {
  MAIN_SCENARIO: '메인 시나리오',
  MAP: '지도',
  HANDOUT: '핸드아웃',
  APPENDIX: '부록',
  REFERENCE: '참고 자료',
  CHARACTER_SHEET: '캐릭터 시트',
  UNDETERMINED: '미확정',
}

const selectableStatuses = new Set<KnowledgeDocumentView['status']>([
  'EXTRACTED',
  'INDEXED',
  'PARTIAL_AWAITING_CONFIRMATION',
  'PARTIAL_CONFIRMED',
])

export function ScenarioSetup({ api, playerId, onError }: { api: SetupApi; playerId: string; onError: (message: string) => void }) {
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [roles, setRoles] = useState<Record<string, ScenarioBundleRole>>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bundle, setBundle] = useState<ScenarioBundleView | null>(null)
  const [saving, setSaving] = useState(false)
  const [sourceQuery, setSourceQuery] = useState('')
  const [sourceResults, setSourceResults] = useState<StorySourceEvidenceView[]>([])
  const [searchingSources, setSearchingSources] = useState(false)

  useEffect(() => {
    let active = true
    void api.listKnowledgeDocuments(playerId)
      .then(items => {
        if (!active) return
        const storybooks = items.filter(document => document.documentType === 'STORYBOOK' && selectableStatuses.has(document.status))
        setDocuments(storybooks)
        setSelectedIds(new Set(storybooks.map(document => document.knowledgeDocumentId)))
        setRoles(storybooks.reduce<Record<string, ScenarioBundleRole>>((acc, document, index) => {
          acc[document.knowledgeDocumentId] = index === 0 ? 'MAIN_SCENARIO' : 'HANDOUT'
          return acc
        }, {}))
      })
      .catch(error => {
        if (!active) return
        onError(error instanceof Error ? error.message : '시나리오 자료를 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [api, onError, playerId])

  function toggleSelected(knowledgeDocumentId: string) {
    setSelectedIds(current => {
      const next = new Set(current)
      if (next.has(knowledgeDocumentId)) next.delete(knowledgeDocumentId)
      else next.add(knowledgeDocumentId)
      return next
    })
  }

  function updateRole(knowledgeDocumentId: string, role: ScenarioBundleRole) {
    setRoles(current => ({ ...current, [knowledgeDocumentId]: role }))
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const documentsToSave: ScenarioBundleDraft[] = documents
      .filter(document => selectedIds.has(document.knowledgeDocumentId))
      .map(document => ({
        knowledgeDocumentId: document.knowledgeDocumentId,
        role: roles[document.knowledgeDocumentId] ?? 'UNDETERMINED',
      }))
    if (!documentsToSave.length) return
    setSaving(true)
    try {
      const nextBundle = bundle
        ? await api.reviseScenarioBundle(bundle.bundleId, playerId, documentsToSave)
        : await api.createScenarioBundle(playerId, documentsToSave)
      setBundle(nextBundle)
    } catch (error) {
      onError(error instanceof Error ? error.message : '시나리오 번들을 저장하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  async function searchSources(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!api.searchStorySources || !sourceQuery.trim()) return
    setSearchingSources(true)
    try {
      const scope = documents
        .filter(document => selectedIds.has(document.knowledgeDocumentId) && document.extractionVersion != null)
        .map(document => ({ documentId: document.knowledgeDocumentId, extractionVersion: document.extractionVersion as number }))
      setSourceResults(await api.searchStorySources(playerId, scope, sourceQuery.trim()))
    } catch (error) {
      onError(error instanceof Error ? error.message : '시나리오 원문 검색에 실패했습니다.')
    } finally {
      setSearchingSources(false)
    }
  }

  return (
    <section aria-labelledby="scenario-heading">
      <h2 id="scenario-heading">시나리오 번들</h2>
      <form onSubmit={save}>
        <ul aria-label="시나리오 문서 목록">
          {documents.map(document => (
            <li key={document.knowledgeDocumentId}>
              <label>
                <input
                  type="checkbox"
                  checked={selectedIds.has(document.knowledgeDocumentId)}
                  onChange={() => toggleSelected(document.knowledgeDocumentId)}
                />
                {document.originalFilename}
              </label>
              <label>
                {document.originalFilename} 역할
                <select
                  aria-label={`${document.originalFilename} 역할`}
                  value={roles[document.knowledgeDocumentId] ?? 'UNDETERMINED'}
                  onChange={event => updateRole(document.knowledgeDocumentId, event.currentTarget.value as ScenarioBundleRole)}
                >
                  {Object.entries(roleLabel).map(([role, label]) => (
                    <option key={role} value={role}>{label}</option>
                  ))}
                </select>
              </label>
              {document.status === 'PARTIAL_AWAITING_CONFIRMATION' ||
              document.status === 'PARTIAL_CONFIRMED' ||
              (document.warnings?.length ?? 0) > 0 ? (
                <p role="alert">{document.originalFilename}: 추출 경고가 있어 컴파일 위험이 있습니다.</p>
              ) : null}
            </li>
          ))}
        </ul>
        <button type="submit" disabled={saving || selectedIds.size === 0}>
          {saving ? '저장 중…' : bundle ? '시나리오 번들 다시 저장' : '시나리오 번들 저장'}
        </button>
      </form>
      {bundle ? (
        <section aria-labelledby="bundle-summary-heading">
          <h3 id="bundle-summary-heading">저장된 번들</h3>
          <p>번들 저장 완료: {bundle.bundleId} v{bundle.currentRevision}</p>
          <ul>
            {bundle.documents.map(document => (
              <li key={document.knowledgeDocumentId}>
                {document.originalFilename} · {document.role}
              </li>
            ))}
          </ul>
        </section>
      ) : null}
      {api.searchStorySources ? (
        <section aria-labelledby="story-source-search-heading">
          <h3 id="story-source-search-heading">시나리오 원문 검색 진단</h3>
          <form onSubmit={searchSources}>
            <label>
              검색어
              <input value={sourceQuery} onChange={event => setSourceQuery(event.currentTarget.value)} />
            </label>
            <button type="submit" disabled={searchingSources || selectedIds.size === 0 || !sourceQuery.trim()}>
              {searchingSources ? '검색 중…' : '선택 문서에서 검색'}
            </button>
          </form>
          <ul aria-label="시나리오 원문 검색 결과">
            {sourceResults.map(result => (
              <li key={`${result.knowledgeDocumentId}-${result.extractionVersion}-${result.locator}`}>
                {result.locator}: {result.excerpt} · {result.score.toFixed(3)}
              </li>
            ))}
          </ul>
          {!searchingSources && sourceResults.length === 0 && sourceQuery ? <p>근거 없음</p> : null}
        </section>
      ) : null}
    </section>
  )
}
