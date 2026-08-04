import { type FormEvent, useEffect, useState } from 'react'
import type {
  KnowledgeDocumentView,
  RuntimeOptionsView,
  ScenarioBundleDraft,
  ScenarioBundleRole,
  ScenarioBundleView,
  ScenarioCompilationView,
  ScenarioPackageView,
  SetupApi,
  CharacterInputNodeView,
  StorySourceEvidenceView,
} from '../rulebooks/SetupApi'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import { Button } from '../../components/ui/button'
import { Input } from '../../components/ui/input'
import { Select } from '../../components/ui/select'

const roleLabel: Record<ScenarioBundleRole, string> = {
  RULEBOOK: '룰북',
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

// eslint-disable-next-line react-refresh/only-export-components -- Pure serializer is exercised by ScenarioSetup tests.
export function serializeBlueprintValues(nodes: CharacterInputNodeView[], values: Record<string, string>, parentPath = ''): string[] {
  return nodes.flatMap(node => {
    const path = parentPath ? `${parentPath}.${node.key}` : node.key
    const value = values[node.id] ?? node.value ?? ''
    const current = value.trim() ? [`${path}=${value}`] : []
    return [...current, ...serializeBlueprintValues(node.children, values, path)]
  })
}

export function ScenarioSetup({ api, playerId, onError, sessionApi, availableDocuments, initialBundle, onBundleSaved }: {
  api: SetupApi
  playerId: string
  onError: (message: string) => void
  sessionApi?: Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage'>
  availableDocuments?: KnowledgeDocumentView[]
  initialBundle?: ScenarioBundleView | null
  onBundleSaved?: (bundle: ScenarioBundleView) => void
}) {
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>(availableDocuments ?? [])
  const [roles, setRoles] = useState<Record<string, ScenarioBundleRole>>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bundle, setBundle] = useState<ScenarioBundleView | null>(null)
  const [scenarioPackage, setScenarioPackage] = useState<ScenarioPackageView | null>(null)
  const [sessions, setSessions] = useState<AdventureSessionView[]>([])
  const [compilation, setCompilation] = useState<ScenarioCompilationView | null>(null)
  const [compilationFailure, setCompilationFailure] = useState<string | null>(null)
  const [runtimeOptions, setRuntimeOptions] = useState<RuntimeOptionsView | null>(null)
  const [selectedEngineId, setSelectedEngineId] = useState('')
  const [selectedToolIds, setSelectedToolIds] = useState<string[]>([])
  const [compiling, setCompiling] = useState(false)
  const [saving, setSaving] = useState(false)
  const [sourceQuery, setSourceQuery] = useState('')
  const [sourceResults, setSourceResults] = useState<StorySourceEvidenceView[]>([])
  const [searchingSources, setSearchingSources] = useState(false)
  const canCompile = Boolean(api.compileScenarioBundle || (api.startScenarioCompilation && api.getScenarioCompilation && api.getScenarioPackage))

  useEffect(() => {
    if (initialBundle) {
      setBundle(initialBundle)
      setSelectedIds(new Set(initialBundle.documents.map(document => document.knowledgeDocumentId)))
      setRoles(Object.fromEntries(initialBundle.documents.map(document => [document.knowledgeDocumentId, document.role])))
    }
  }, [initialBundle])

  useEffect(() => {
    if (!bundle || !api.listScenarioPackages) return
    let active = true
    void api.listScenarioPackages(bundle.bundleId)
      .then(packages => {
        if (!active) return
        const currentPackage = packages.find(item => item.bundleRevision === bundle.currentRevision) ?? null
        setScenarioPackage(currentPackage)
        if (!currentPackage) setSessions([])
      })
      .catch(error => {
        if (active) onError(error instanceof Error ? error.message : '번들의 컴파일 패키지를 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [api, bundle, onError])

  useEffect(() => {
    if (!scenarioPackage || !sessionApi?.listByScenarioPackage) {
      setSessions([])
      return
    }
    let active = true
    void sessionApi.listByScenarioPackage(scenarioPackage.packageId)
      .then(items => { if (active) setSessions(items) })
      .catch(error => { if (active) onError(error instanceof Error ? error.message : '번들 세션을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [onError, scenarioPackage, sessionApi])

  useEffect(() => {
    if (initialBundle) return
    if (availableDocuments) {
      const selectable = availableDocuments.filter(document => selectableStatuses.has(document.status))
      setDocuments(availableDocuments)
      setSelectedIds(new Set(selectable.map(document => document.knowledgeDocumentId)))
      setRoles(selectable.reduce<Record<string, ScenarioBundleRole>>((acc, document, index) => {
        acc[document.knowledgeDocumentId] = document.documentType === 'RULEBOOK'
          ? 'RULEBOOK'
          : index === 0 ? 'MAIN_SCENARIO' : 'HANDOUT'
        return acc
      }, {}))
      return
    }
    let active = true
    void api.listKnowledgeDocuments(playerId)
      .then(items => {
        if (!active) return
        const selectable = items.filter(document => selectableStatuses.has(document.status))
        setDocuments(items)
        setSelectedIds(new Set(selectable.map(document => document.knowledgeDocumentId)))
        setRoles(selectable.reduce<Record<string, ScenarioBundleRole>>((acc, document, index) => {
          acc[document.knowledgeDocumentId] = document.documentType === 'RULEBOOK'
            ? 'RULEBOOK'
            : index === 0 ? 'MAIN_SCENARIO' : 'HANDOUT'
          return acc
        }, {}))
      })
      .catch(error => {
        if (!active) return
        onError(error instanceof Error ? error.message : '시나리오 자료를 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [api, onError, playerId, availableDocuments, initialBundle])

  useEffect(() => {
    if (!api.getRuntimeOptions) return
    let active = true
    void api.getRuntimeOptions()
      .then(options => {
        if (!active) return
        setRuntimeOptions(options)
        setSelectedEngineId(options.defaultEngineId)
        setSelectedToolIds(options.defaultToolIds)
      })
      .catch(error => {
        if (!active) return
        onError(error instanceof Error ? error.message : '런타임 옵션을 불러오지 못했습니다.')
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
    setScenarioPackage(null)
    setCompilationFailure(null)
    try {
      const nextBundle = bundle
        ? await api.reviseScenarioBundle(bundle.bundleId, playerId, documentsToSave)
        : await api.createScenarioBundle(playerId, documentsToSave)
      setBundle(nextBundle)
      onBundleSaved?.(nextBundle)
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
    setCompilationFailure(null)
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
      const message = error instanceof Error ? error.message : '시나리오 패키지 컴파일에 실패했습니다.'
      setCompilationFailure(message)
      onError(message)
    } finally {
      setCompiling(false)
    }
  }

  async function createAdventure() {
    if (!scenarioPackage || !sessionApi?.create || !api.getPlayPreparation) return
    try {
      const preparation = await api.getPlayPreparation(scenarioPackage.packageId)
      const session = await sessionApi.create({ scenarioPackageId: scenarioPackage.packageId, blueprintId: scenarioPackage.packageId, blueprintRevision: preparation.characterCreationBlueprint.revision ?? 0 })
      window.location.hash = `#/sessions/${session.sessionId}/party`
    } catch (error) {
      onError(error instanceof Error ? error.message : '모험 세션을 만들지 못했습니다.')
    }
  }

  return (
    <section className="setup-panel scenario-setup" aria-labelledby="scenario-heading">
      <h2 id="scenario-heading">시나리오 번들</h2>
      <form onSubmit={save}>
        <ul aria-label="시나리오 문서 목록">
          {documents.map(document => (
            <li key={document.knowledgeDocumentId}>
              <label>
                <Input
                  type="checkbox"
                  checked={selectedIds.has(document.knowledgeDocumentId)}
                  disabled={!selectableStatuses.has(document.status)}
                  onChange={() => toggleSelected(document.knowledgeDocumentId)}
                />
                {selectableStatuses.has(document.status) ? document.originalFilename : '문서 선택 불가'}
              </label>
              <Select
                className="scenario-role-select"
                aria-label={`${document.originalFilename} 역할`}
                value={roles[document.knowledgeDocumentId] ?? 'UNDETERMINED'}
                disabled={!selectableStatuses.has(document.status)}
                onChange={event => updateRole(document.knowledgeDocumentId, event.currentTarget.value as ScenarioBundleRole)}
              >
                {Object.entries(roleLabel).map(([role, label]) => (
                  <option key={role} value={role}>{label}</option>
                ))}
              </Select>
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
        <Button type="submit" disabled={saving || selectedIds.size === 0}>
          {saving ? '저장 중…' : bundle ? '시나리오 번들 다시 저장' : '시나리오 번들 저장'}
        </Button>
      </form>
      {bundle ? (
        <section aria-labelledby="bundle-summary-heading">
          <h3 id="bundle-summary-heading">저장된 번들</h3>
          <p>번들 저장 완료: {bundle.bundleId} v{bundle.currentRevision}</p>
          <ul>
            {bundle.documents.map(document => (
              <li key={document.knowledgeDocumentId}>{document.originalFilename} · {document.role}</li>
            ))}
          </ul>
          {canCompile ? (
            <Button type="button" disabled={compiling} onClick={() => void compile()}>
              {compiling ? '컴파일 중…' : '시나리오 패키지 컴파일'}
            </Button>
          ) : null}
          {compilation ? <p>컴파일 상태 {compilation.status} · 시도 {compilation.attempt}</p> : null}
          {compilationFailure ? <p role="alert">컴파일 실패: {compilationFailure} · 다시 컴파일하세요.</p> : null}
          {scenarioPackage ? (
            <div role="status">
              <p>패키지 {scenarioPackage.packageId} · {scenarioPackage.reportStatus}</p>
              <p>캐릭터 한도: {scenarioPackage.characterLimit.maximumCharacters}명</p>
              <Button type="button" onClick={() => { window.location.hash = `#/scenario-packages/${scenarioPackage.packageId}/character-blueprint` }}>
                캐릭터 생성 시작
              </Button>
              <Button type="button" onClick={() => void createAdventure()} disabled={scenarioPackage.reportStatus !== 'COMPLETE'}>
                이 번들로 모험 만들기
              </Button>
              {scenarioPackage.characterLimit.source ? (
                <p>한도 근거: {scenarioPackage.characterLimit.source.locator} · {scenarioPackage.characterLimit.sourceQuote}</p>
              ) : <p>한도 근거: 추출되지 않아 기본값 1명 적용</p>}
              {scenarioPackage.warnings.length > 0 ? <ul>{scenarioPackage.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul> : null}
            </div>
          ) : null}
          {scenarioPackage && sessionApi?.listByScenarioPackage ? (
            <section aria-labelledby="bundle-sessions-heading">
              <h3 id="bundle-sessions-heading">이 번들의 모험 세션</h3>
              {sessions.length === 0 ? <p>아직 생성된 모험 세션이 없습니다.</p> : (
                <ul aria-label="번들 모험 세션 목록">
                  {sessions.map(session => (
                    <li key={session.sessionId}>
                      <span>{session.sessionId} · {session.status} · 캐릭터 {session.party.length}/{session.characterLimit}</span>
                      <Button type="button" variant="outline" onClick={() => { window.location.hash = `#/sessions/${session.sessionId}/party` }}>파티 구성 열기</Button>
                      <Button type="button" variant="outline" onClick={() => { window.location.hash = `#/sessions/${session.sessionId}/character-blueprint` }}>캐릭터 설정 열기</Button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          ) : null}
        </section>
      ) : null}
      {runtimeOptions ? (
        <section aria-labelledby="runtime-options-heading">
          <h2 id="runtime-options-heading">세션 런타임 옵션</h2>
          <p>번들 컴파일과 분리된 모험 실행 설정입니다.</p>
          <details open>
            <summary>엔진 선택지</summary>
            <label>
              엔진
              <Select aria-label="런타임 엔진" value={selectedEngineId} onChange={event => setSelectedEngineId(event.currentTarget.value)}>
                {runtimeOptions.engines.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
              </Select>
            </label>
          </details>
          <details open>
            <summary>도구 선택지</summary>
            <fieldset>
              <legend>도구</legend>
              {runtimeOptions.tools.map(option => (
                <label key={option.id}>
                  <Input
                    type="checkbox"
                    aria-label={option.id}
                    checked={selectedToolIds.includes(option.id)}
                    onChange={event => setSelectedToolIds(current => event.currentTarget.checked
                      ? [...current, option.id]
                      : current.filter(id => id !== option.id))}
                  />
                  {option.label}
                </label>
              ))}
            </fieldset>
          </details>
        </section>
      ) : null}
      {api.searchStorySources ? (
        <section aria-labelledby="story-source-search-heading">
          <h3 id="story-source-search-heading">시나리오 원문 검색 진단</h3>
          <form onSubmit={searchSources}>
            <label>
              검색어
              <Input value={sourceQuery} onChange={event => setSourceQuery(event.currentTarget.value)} />
            </label>
              <Button type="submit" disabled={searchingSources || selectedIds.size === 0 || !sourceQuery.trim()}>
              {searchingSources ? '검색 중…' : '선택 문서에서 검색'}
              </Button>
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
  if (!api.getScenarioCompilation) throw new Error('시나리오 패키지 컴파일 상태 API가 없습니다.')
  let current = started
  for (let attempts = 0; attempts < compilationPollLimit; attempts += 1) {
    if (current.status === 'PUBLISHED') return current
    if (current.status === 'FAILED') throw new Error(current.failureReason || '시나리오 패키지 컴파일에 실패했습니다.')
    await sleep(compilationPollIntervalMs)
    current = await api.getScenarioCompilation(current.compilationId)
    onProgress(current)
  }
  throw new Error('시나리오 패키지 컴파일이 아직 진행 중입니다. 잠시 후 상태를 다시 확인하세요.')
}

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}
