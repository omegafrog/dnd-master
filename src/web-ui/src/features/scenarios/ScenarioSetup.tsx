import { type FormEvent, useEffect, useState } from 'react'
import type {
  KnowledgeDocumentView,
  ScenarioBundleDraft,
  ScenarioBundleRole,
  ScenarioBundleView,
  ScenarioCompilationView,
  ScenarioPackageView,
  SetupApi,
  CharacterInputNodeView,
} from '../rulebooks/SetupApi'
import type { AdventureSessionApi, AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import { Button } from '../../components/ui/button'
import { Input } from '../../components/ui/input'
import { Progress } from '../../components/ui/progress'
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

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

const selectableStatuses = new Set<KnowledgeDocumentView['status']>([
  'EXTRACTED',
  'INDEXED',
  'PARTIAL_AWAITING_CONFIRMATION',
  'PARTIAL_CONFIRMED',
])

const indexingFinishedStatuses = new Set<KnowledgeDocumentView['status']>(['INDEXED', 'PARTIAL_CONFIRMED'])

const compilationPollIntervalMs = 500

function preparationStorageKey(bundleId: string, revision: number) {
  return `dnd-preparation:${bundleId}:${revision}`
}

function compilationProgress(status: ScenarioCompilationView['status']): number {
  switch (status) {
    case 'REQUESTED': return 10
    case 'WAITING_RETRY': return 40
    case 'RUNNING': return 70
    case 'PUBLISHED': return 100
    case 'FAILED': return 0
  }
}

// eslint-disable-next-line react-refresh/only-export-components -- Pure serializer is exercised by ScenarioSetup tests.
export function serializeBlueprintValues(nodes: CharacterInputNodeView[], values: Record<string, string>, parentPath = ''): string[] {
  return nodes.flatMap(node => {
    const path = parentPath ? `${parentPath}.${node.key}` : node.key
    const value = values[node.id] ?? node.value ?? ''
    const current = value.trim() ? [`${path}=${value}`] : []
    return [...current, ...serializeBlueprintValues(node.children, values, path)]
  })
}

export function ScenarioSetup({ api, playerId, onError, sessionApi, availableDocuments, rulebookDocumentId, initialBundle, onBundleSaved, preparationOnly = false }: {
  api: SetupApi
  playerId: string
  onError: (message: string) => void
  sessionApi?: Pick<AdventureSessionApi, 'create' | 'listByScenarioPackage'>
  availableDocuments?: KnowledgeDocumentView[]
  rulebookDocumentId?: string
  initialBundle?: ScenarioBundleView | null
  onBundleSaved?: (bundle: ScenarioBundleView) => void
  preparationOnly?: boolean
}) {
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>(availableDocuments ?? [])
  const [roles, setRoles] = useState<Record<string, ScenarioBundleRole>>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bundle, setBundle] = useState<ScenarioBundleView | null>(null)
  const [scenarioPackage, setScenarioPackage] = useState<ScenarioPackageView | null>(null)
  const [sessions, setSessions] = useState<AdventureSessionView[]>([])
  const [compilation, setCompilation] = useState<ScenarioCompilationView | null>(null)
  const [compilationFailure, setCompilationFailure] = useState<string | null>(null)
  const [partySize, setPartySize] = useState(4)
  const [compiling, setCompiling] = useState(false)
  const [saving, setSaving] = useState(false)
  const canCompile = Boolean(api.startScenarioCompilation && api.getScenarioCompilation && api.getScenarioPackage)
  const allDocumentsIndexed = documents.length > 0 && documents.every(document => indexingFinishedStatuses.has(document.status))

  useEffect(() => {
    if (initialBundle) {
      setScenarioPackage(null)
      setCompilation(null)
      setCompilationFailure(null)
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
        if (currentPackage) setScenarioPackage(currentPackage)
        if (!currentPackage) setSessions([])
      })
      .catch(error => {
        if (active) onError(error instanceof Error ? error.message : '모험 준비 결과를 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [api, bundle, onError])

  useEffect(() => {
    if (!bundle || !api.getScenarioCompilation) return
    const storageKey = preparationStorageKey(bundle.bundleId, bundle.currentRevision)
    const compilationId = window.localStorage.getItem(storageKey)
    if (!compilationId) return
    let active = true
    void api.getScenarioCompilation(compilationId)
      .then(current => {
        if (!active || current.bundleId !== bundle.bundleId || current.bundleRevision !== bundle.currentRevision) return
        setCompilation(current)
        if (current.status === 'PUBLISHED' && current.packageId && api.getScenarioPackage) {
          return api.getScenarioPackage(current.packageId).then(packageView => {
            if (active) setScenarioPackage(packageView)
          })
        }
        return undefined
      })
      .catch(error => {
        if (active) onError(error instanceof Error ? error.message : '게임 준비 상태를 복원하지 못했습니다.')
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
      .catch(error => { if (active) onError(error instanceof Error ? error.message : '이 자료로 만든 모험을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [onError, scenarioPackage, sessionApi])

  useEffect(() => {
    if (!compilation || !api.getScenarioCompilation || compilation.status === 'PUBLISHED' || compilation.status === 'FAILED') return
    let active = true
    const poll = async () => {
      let current = compilation
      try {
        while (active && current.status !== 'PUBLISHED' && current.status !== 'FAILED') {
          await sleep(compilationPollIntervalMs)
          current = await api.getScenarioCompilation!(current.compilationId)
          if (!active) return
          setCompilation(current)
        }
        if (current.status === 'PUBLISHED' && current.packageId && api.getScenarioPackage) {
          setScenarioPackage(await api.getScenarioPackage(current.packageId))
        }
        if (current.status === 'FAILED') setCompilationFailure(current.failureReason || '게임 준비에 실패했습니다.')
      } catch (error) {
        if (active) setCompilationFailure(error instanceof Error ? error.message : '게임 준비 상태를 확인하지 못했습니다.')
      }
    }
    void poll()
    return () => { active = false }
  }, [api, compilation])

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
    if (rulebookDocumentId) documentsToSave.push({ knowledgeDocumentId: rulebookDocumentId, role: 'RULEBOOK' })
    if (!documentsToSave.length || !rulebookDocumentId) return
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
      onError(error instanceof Error ? error.message : '모험 자료를 저장하지 못했습니다.')
    } finally {
      setSaving(false)
    }
  }

  async function compile() {
    if (!bundle) return
    setCompiling(true)
    setScenarioPackage(null)
    setCompilationFailure(null)
    try {
      if (api.startScenarioCompilation && api.getScenarioCompilation && api.getScenarioPackage) {
        const inputFingerprint = compilation?.status === 'PUBLISHED' || compilation?.status === 'FAILED'
          ? `scenario-bundle:${bundle.bundleId}:revision:${bundle.currentRevision}:retry:${Date.now()}`
          : `scenario-bundle:${bundle.bundleId}:revision:${bundle.currentRevision}`
        const started = await api.startScenarioCompilation(
          bundle.bundleId,
          playerId,
          inputFingerprint,
        )
        window.localStorage.setItem(preparationStorageKey(bundle.bundleId, bundle.currentRevision), started.compilationId)
        setCompilation(started)
        return
      }
      throw new Error('자동으로 게임을 준비할 수 없습니다.')
    } catch (error) {
      const message = error instanceof Error ? error.message : '게임 준비에 실패했습니다.'
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
      const session = await sessionApi.create({ scenarioPackageId: scenarioPackage.packageId, blueprintId: scenarioPackage.packageId, blueprintRevision: preparation.characterCreationBlueprint.revision ?? 0, partySize: Math.min(partySize, scenarioPackage.characterLimit.maximumCharacters) })
      window.location.hash = `#/sessions/${session.sessionId}/party`
    } catch (error) {
      onError(error instanceof Error ? error.message : '모험 세션을 만들지 못했습니다.')
    }
  }

  return (
    <section className="setup-panel scenario-setup" aria-labelledby="scenario-heading">
      <h2 id="scenario-heading">{preparationOnly ? '게임 준비' : '모험 자료 구성'}</h2>
      {!preparationOnly ? <form onSubmit={save}>
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
                <p role="alert">{document.originalFilename}: 준비에 문제가 있습니다 — {(document.warnings ?? []).join(', ') || document.failureReason || '자료를 읽지 못했습니다.'}</p>
              ) : document.status === 'PARTIAL_AWAITING_CONFIRMATION' ||
                document.status === 'PARTIAL_CONFIRMED' ||
                (document.warnings?.length ?? 0) > 0 ? (
                <p role="alert">{document.originalFilename}: 자료를 읽는 중 확인이 필요한 부분이 있습니다.</p>
              ) : null}
            </li>
          ))}
        </ul>
        <Button type="submit" disabled={saving || selectedIds.size === 0 || !allDocumentsIndexed || !rulebookDocumentId}>
          {saving ? '저장 중…' : bundle ? '모험 자료 다시 저장' : '모험 자료 저장'}
        </Button>
        {!rulebookDocumentId ? <p>위에서 룰북을 하나 선택하면 모험 자료를 저장할 수 있습니다.</p> : !allDocumentsIndexed ? <p>모든 자료의 준비가 끝나면 모험 자료를 저장할 수 있습니다.</p> : null}
      </form> : null}
      {bundle && preparationOnly ? (
        <section aria-labelledby="bundle-summary-heading">
          {!preparationOnly ? <h3 id="bundle-summary-heading">저장된 모험 자료</h3> : <h3 id="bundle-summary-heading">게임 준비 대상</h3>}
          <p>{preparationOnly ? '이 자료를 바탕으로 게임에 필요한 내용을 준비합니다.' : `모험 자료 저장 완료: ${bundle.bundleId} v${bundle.currentRevision}`}</p>
          <ul>
            {bundle.documents.map(document => (
              <li key={document.knowledgeDocumentId}>{document.originalFilename} · {document.role}</li>
            ))}
          </ul>
          {canCompile ? (
            <Button
              type="button"
              disabled={compiling || compilation?.status === 'REQUESTED' || compilation?.status === 'RUNNING' || compilation?.status === 'WAITING_RETRY'}
              onClick={() => void compile()}
            >
            {compiling || compilation?.status === 'REQUESTED' || compilation?.status === 'RUNNING' || compilation?.status === 'WAITING_RETRY' ? '게임 준비 상태 확인 중…' : '게임 준비 시작'}
            </Button>
          ) : null}
          {compilation ? (
            <div className="preparation-progress" role="status" aria-live="polite">
              <div className="preparation-progress-heading">
                <span>게임 준비 상태 {compilation.status} · 시도 {compilation.attempt}</span>
                <strong>{compilationProgress(compilation.status)}%</strong>
              </div>
              <Progress value={compilationProgress(compilation.status)} aria-label="게임 준비 진행률" />
              {compilation.status === 'REQUESTED' || compilation.status === 'RUNNING' || compilation.status === 'WAITING_RETRY' ? (
                <p>서버에서 준비 작업을 진행 중입니다. 이 창을 열어 둔 동안 상태를 자동으로 확인합니다.</p>
              ) : null}
            </div>
          ) : null}
          {compilationFailure ? <p role="alert">게임 준비 실패: {compilationFailure} · 다시 준비해 주세요.</p> : null}
          {scenarioPackage ? (
            <div role="status">
              <p>모험 준비 결과 {scenarioPackage.packageId} · {scenarioPackage.reportStatus}</p>
              <p>캐릭터 한도: {scenarioPackage.characterLimit.maximumCharacters}명</p>
              <label>
                파티 인원
                <Select aria-label="파티 인원" value={partySize} onChange={event => setPartySize(Number(event.currentTarget.value))}>
                  {Array.from({ length: scenarioPackage.characterLimit.maximumCharacters }, (_, index) => index + 1).map(size => (
                    <option key={size} value={size}>{size}명</option>
                  ))}
                </Select>
              </label>
              <Button type="button" onClick={() => { window.location.hash = `#/scenario-packages/${scenarioPackage.packageId}/character-blueprint` }}>
                캐릭터 생성 시작
              </Button>
              <Button type="button" onClick={() => void createAdventure()} disabled={scenarioPackage.reportStatus !== 'COMPLETE'}>
                이 자료로 모험 만들기
              </Button>
              {scenarioPackage.characterLimit.source ? (
                <p>인원 제한 기준: {scenarioPackage.characterLimit.source.locator} · {scenarioPackage.characterLimit.sourceQuote}</p>
              ) : <p>고정 인원 조건이 없어 1~{scenarioPackage.characterLimit.maximumCharacters}명 중 선택할 수 있습니다.</p>}
              {scenarioPackage.warnings.length > 0 ? <ul>{scenarioPackage.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul> : null}
            </div>
          ) : null}
          {scenarioPackage && sessionApi?.listByScenarioPackage ? (
            <section aria-labelledby="bundle-sessions-heading">
              <h3 id="bundle-sessions-heading">이 자료로 만든 모험</h3>
              {sessions.length === 0 ? <p>아직 생성된 모험 세션이 없습니다.</p> : (
                <ul aria-label="자료로 만든 모험 목록">
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
    </section>
  )
}
