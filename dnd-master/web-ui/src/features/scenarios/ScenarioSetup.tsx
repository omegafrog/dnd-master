import { type FormEvent, useEffect, useState } from 'react'
import type {
  KnowledgeDocumentView,
  ScenarioCompilationView,
  ScenarioBundleDraft,
  ScenarioBundleRole,
  ScenarioBundleView,
  ScenarioPackageView,
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

const compilationPollIntervalMs = 250
const compilationPollLimit = 240

export function ScenarioSetup({ api, playerId, onError }: { api: SetupApi; playerId: string; onError: (message: string) => void }) {
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [roles, setRoles] = useState<Record<string, ScenarioBundleRole>>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bundle, setBundle] = useState<ScenarioBundleView | null>(null)
  const [scenarioPackage, setScenarioPackage] = useState<ScenarioPackageView | null>(null)
  const [compilation, setCompilation] = useState<ScenarioCompilationView | null>(null)
  const [compiling, setCompiling] = useState(false)
  const [saving, setSaving] = useState(false)
  const [sourceQuery, setSourceQuery] = useState('')
  const [sourceResults, setSourceResults] = useState<StorySourceEvidenceView[]>([])
  const [searchingSources, setSearchingSources] = useState(false)
  const canCompile = Boolean(api.compileScenarioBundle || (api.startScenarioCompilation && api.getScenarioCompilation && api.getScenarioPackage))

  useEffect(() => {
    let active = true
    void api.listKnowledgeDocuments(playerId)
      .then(items => {
        if (!active) return
        const storybooks = items.filter(document => document.documentType === 'STORYBOOK')
        const selectableStorybooks = storybooks.filter(document => selectableStatuses.has(document.status))
        setDocuments(storybooks)
        setSelectedIds(new Set(selectableStorybooks.map(document => document.knowledgeDocumentId)))
        setRoles(selectableStorybooks.reduce<Record<string, ScenarioBundleRole>>((acc, document, index) => {
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

  async function compile() {
    if (!bundle) return
    setCompiling(true)
    setScenarioPackage(null)
    try {
      if (api.startScenarioCompilation && api.getScenarioCompilation && api.getScenarioPackage) {
        const started = await api.startScenarioCompilation(
          bundle.bundleId,
          playerId,
          `scenario-bundle:${bundle.bundleId}:revision:${bundle.currentRevision}`,
        )
        setCompilation(started)
        const published = await waitForCompilation(api, started, setCompilation)
        setCompilation(published)
        if (!published.packageId) throw new Error('시나리오 패키지 ID가 없습니다.')
        setScenarioPackage(await api.getScenarioPackage(published.packageId))
        return
      }
      if (!api.compileScenarioBundle) return
      setCompilation(null)
      setScenarioPackage(await api.compileScenarioBundle(bundle.bundleId, playerId))
    } catch (error) {
      onError(error instanceof Error ? error.message : '시나리오 패키지 컴파일에 실패했습니다.')
    } finally {
      setCompiling(false)
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
                  disabled={!selectableStatuses.has(document.status)}
                  onChange={() => toggleSelected(document.knowledgeDocumentId)}
                />
                {selectableStatuses.has(document.status) ? document.originalFilename : '문서 선택 불가'}
              </label>
              <label>
                {document.originalFilename} 역할
                <select
                  aria-label={`${document.originalFilename} 역할`}
                  value={roles[document.knowledgeDocumentId] ?? 'UNDETERMINED'}
                  disabled={!selectableStatuses.has(document.status)}
                  onChange={event => updateRole(document.knowledgeDocumentId, event.currentTarget.value as ScenarioBundleRole)}
                >
                  {Object.entries(roleLabel).map(([role, label]) => (
                    <option key={role} value={role}>{label}</option>
                  ))}
                </select>
              </label>
              {document.status === 'FAILED' ? (
                <p role="alert">{document.originalFilename}: 컴파일 위험 — {(document.warnings ?? []).join(', ') || document.failureReason || '추출 실패'}</p>
              ) : document.status === 'PARTIAL_AWAITING_CONFIRMATION' ||
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
          {canCompile ? (
            <button type="button" disabled={compiling} onClick={() => void compile()}>
              {compiling ? '컴파일 중…' : '시나리오 패키지 컴파일'}
            </button>
          ) : null}
          {compilation ? (
            <p>컴파일 상태 {compilation.status} · 시도 {compilation.attempt}</p>
          ) : null}
          {scenarioPackage ? (
            <div role="status">
              <p>패키지 {scenarioPackage.packageId} · {scenarioPackage.reportStatus}</p>
              {scenarioPackage.warnings.length > 0 ? <ul>
                {scenarioPackage.warnings.map(warning => <li key={warning}>{warning}</li>)}
              </ul> : null}
              <ul aria-label="해석 단위">
                {scenarioPackage.units.map((unit, index) => (
                  <li key={`${unit.kind ?? 'unknown'}-${index}`}>
                    {unit.kind ?? 'UNKNOWN'} · {unit.abilityOrSkill ?? unit.diceExpression ?? '값 없음'}
                    {unit.dc === null ? '' : ` · DC ${unit.dc}`}
                    {unit.status !== 'COMPLETE' ? ` · ${unit.status}` : ''}
                    <div>visibility: {unit.visibility || '없음'} · 근거: {unit.sourceQuote || '없음'} · provenance: {unit.provenance || '없음'}</div>
                    {unit.sourceRefs.length > 0 ? <ul>
                      {unit.sourceRefs.map(ref => <li key={`${ref.documentId}-${ref.extractionVersion}-${ref.locator}`}>
                        source: {ref.documentId} v{ref.extractionVersion} · {ref.locator}
                      </li>)}
                    </ul> : null}
                    {unit.validationMessages.length > 0 ? <ul>
                      {unit.validationMessages.map(message => <li key={message}>{message}</li>)}
                    </ul> : null}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
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

async function waitForCompilation(
  api: SetupApi,
  started: ScenarioCompilationView,
  onProgress: (compilation: ScenarioCompilationView) => void,
): Promise<ScenarioCompilationView> {
  const readCompilation = api.getScenarioCompilation
  if (!readCompilation) throw new Error('시나리오 패키지 컴파일 상태 API가 없습니다.')
  let current = started
  for (let attempts = 0; attempts < compilationPollLimit; attempts += 1) {
    if (current.status === 'PUBLISHED') return current
    if (current.status === 'FAILED') {
      throw new Error(current.failureReason || '시나리오 패키지 컴파일에 실패했습니다.')
    }
    await sleep(compilationPollIntervalMs)
    current = await readCompilation(current.compilationId)
    onProgress(current)
  }
  throw new Error('시나리오 패키지 컴파일이 아직 진행 중입니다. 잠시 후 상태를 다시 확인하세요.')
}

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}
