import { type FormEvent, useEffect, useState } from 'react'
import type {
  CreatedCharacterSheetView,
  CharacterCreationDraft,
  KnowledgeDocumentView,
  PlayPreparationView,
  RuntimeOptionsView,
  ScenarioBundleDraft,
  ScenarioBundleRole,
  ScenarioBundleView,
  ScenarioCompilationView,
  ScenarioPackageView,
  SetupApi,
  StorySourceEvidenceView,
} from '../rulebooks/SetupApi'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'

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

export function ScenarioSetup({ api, playerId, onError, sessionApi, onSessionCreated }: { api: SetupApi; playerId: string; onError: (message: string) => void; sessionApi?: Pick<AdventureSessionApi, 'create'>; onSessionCreated?: (sessionId: string) => void }) {
  const [documents, setDocuments] = useState<KnowledgeDocumentView[]>([])
  const [roles, setRoles] = useState<Record<string, ScenarioBundleRole>>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bundle, setBundle] = useState<ScenarioBundleView | null>(null)
  const [scenarioPackage, setScenarioPackage] = useState<ScenarioPackageView | null>(null)
  const [compilation, setCompilation] = useState<ScenarioCompilationView | null>(null)
  const [compilationFailure, setCompilationFailure] = useState<string | null>(null)
  const [playPreparation, setPlayPreparation] = useState<PlayPreparationView | null>(null)
  const [createdCharacterSheet, setCreatedCharacterSheet] = useState<CreatedCharacterSheetView | null>(null)
  const [runtimeOptions, setRuntimeOptions] = useState<RuntimeOptionsView | null>(null)
  const [selectedEngineId, setSelectedEngineId] = useState('')
  const [selectedToolIds, setSelectedToolIds] = useState<string[]>([])
  const [publishingBlueprint, setPublishingBlueprint] = useState(false)
  const [compiling, setCompiling] = useState(false)
  const [saving, setSaving] = useState(false)
  const [creatingCharacter, setCreatingCharacter] = useState(false)
  const [sourceQuery, setSourceQuery] = useState('')
  const [sourceResults, setSourceResults] = useState<StorySourceEvidenceView[]>([])
  const [searchingSources, setSearchingSources] = useState(false)
  const [characterEdition, setCharacterEdition] = useState<'DND_5E_2014' | 'DND_5E_2024'>('DND_5E_2024')
  const [characterName, setCharacterName] = useState('')
  const [characterLevel, setCharacterLevel] = useState(1)
  const [characterInspiration, setCharacterInspiration] = useState(false)
  const [blueprintValues, setBlueprintValues] = useState<Record<string, string>>({})
  const canCompile = Boolean(api.compileScenarioBundle || (api.startScenarioCompilation && api.getScenarioCompilation && api.getScenarioPackage))
  const canCreateCharacter = Boolean(!sessionApi && api.createCharacterSheet && playPreparation?.characterCreationBlueprint?.available)

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

  useEffect(() => {
    if (!scenarioPackage || !api.getPlayPreparation) {
      setPlayPreparation(null)
      return
    }
    let active = true
    void api.getPlayPreparation(scenarioPackage.packageId)
      .then(preparation => {
        if (!active) return
        setPlayPreparation(preparation)
      })
      .catch(error => {
        if (!active) return
        setPlayPreparation(null)
        onError(error instanceof Error ? error.message : '플레이 준비 상태를 불러오지 못했습니다.')
      })
    return () => { active = false }
  }, [api, onError, scenarioPackage])

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
    setPlayPreparation(null)
    setCompilationFailure(null)
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

  async function createCharacter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canCreateCharacter || !api.createCharacterSheet) return
    if (!characterName.trim()) return
    setCreatingCharacter(true)
    try {
      const draft: CharacterCreationDraft = {
        edition: characterEdition,
        characterName: characterName.trim(),
        level: characterLevel,
        inspiration: characterInspiration,
        blueprintRevision: playPreparation?.characterCreationBlueprint.revision,
        blueprintValues,
      }
      setCreatedCharacterSheet(await api.createCharacterSheet(draft))
    } catch (error) {
      onError(error instanceof Error ? error.message : '캐릭터를 생성하지 못했습니다.')
    } finally {
      setCreatingCharacter(false)
    }
  }

  async function compile() {
    if (!bundle) return
    setCompiling(true)
    setScenarioPackage(null)
    setPlayPreparation(null)
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
        const nextPackage = await api.getScenarioPackage(published.packageId)
        setScenarioPackage(nextPackage)
        return
      }
      if (!api.compileScenarioBundle) return
      setCompilation(null)
      const nextPackage = await api.compileScenarioBundle(bundle.bundleId, playerId)
      setScenarioPackage(nextPackage)
    } catch (error) {
      const message = error instanceof Error ? error.message : '시나리오 패키지 컴파일에 실패했습니다.'
      setCompilationFailure(message)
      onError(message)
    } finally {
      setCompiling(false)
    }
  }

  async function createSession() {
    const blueprint = playPreparation?.characterCreationBlueprint
    if (!sessionApi || !scenarioPackage || !blueprint?.available || blueprint.status !== 'PUBLISHED' || blueprint.revision == null) return
    try {
      const session = await sessionApi.create({
        scenarioPackageId: scenarioPackage.packageId,
        blueprintId: scenarioPackage.packageId,
        blueprintRevision: blueprint.revision,
      })
      onSessionCreated?.(session.sessionId)
    } catch (error) { onError(error instanceof Error ? error.message : '세션 초안을 생성하지 못했습니다.') }
  }

  async function publishBlueprint() {
    if (!scenarioPackage || !api.publishBlueprint) return
    setPublishingBlueprint(true)
    try {
      await api.publishBlueprint(scenarioPackage.packageId)
      if (api.getPlayPreparation) setPlayPreparation(await api.getPlayPreparation(scenarioPackage.packageId))
    } catch (error) { onError(error instanceof Error ? error.message : 'Blueprint 게시에 실패했습니다.') }
    finally { setPublishingBlueprint(false) }
  }

  async function resolveBlueprint(fieldKey: string) {
    if (!scenarioPackage || !api.resolveBlueprint || !api.getPlayPreparation) return
    try {
      await api.resolveBlueprint(scenarioPackage.packageId, fieldKey, blueprintValues[fieldKey] ?? '')
      setPlayPreparation(await api.getPlayPreparation(scenarioPackage.packageId))
    } catch (error) { onError(error instanceof Error ? error.message : 'Blueprint 검토를 저장하지 못했습니다.') }
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
          {compilationFailure ? <p role="alert">컴파일 실패: {compilationFailure} · 다시 컴파일하세요.</p> : null}
          {scenarioPackage ? (
            <div role="status">
              <p>패키지 {scenarioPackage.packageId} · {scenarioPackage.reportStatus}</p>
              <p>캐릭터 한도: {scenarioPackage.characterLimit.maximumCharacters}명</p>
              {scenarioPackage.characterLimit.source ? (
                <p>한도 근거: {scenarioPackage.characterLimit.source.locator} · {scenarioPackage.characterLimit.sourceQuote}</p>
              ) : <p>한도 근거: 추출되지 않아 기본값 1명 적용</p>}
              {scenarioPackage.warnings.length > 0 ? (
                <ul>
                  {scenarioPackage.warnings.map(warning => <li key={warning}>{warning}</li>)}
                </ul>
              ) : null}
              <ul aria-label="해석 단위">
                {scenarioPackage.units.map((unit, index) => (
                  <li key={`${unit.kind ?? 'unknown'}-${index}`}>
                    {unit.kind ?? 'UNKNOWN'} · {unit.abilityOrSkill ?? unit.diceExpression ?? '값 없음'}
                    {unit.dc === null ? '' : ` · DC ${unit.dc}`}
                    {unit.status !== 'COMPLETE' ? ` · ${unit.status}` : ''}
                    <div>visibility: {unit.visibility || '없음'} · 근거: {unit.sourceQuote || '없음'} · provenance: {unit.provenance || '없음'}</div>
                    {unit.runtimeCapabilities.length > 0 ? <div>runtime: {unit.runtimeCapabilities.join(', ')}</div> : null}
                    {unit.detail.triggerCondition ? <div>trigger: {unit.detail.triggerCondition}</div> : null}
                    {unit.detail.steps.length > 0 ? (
                      <ul>
                        {unit.detail.steps.map(step => <li key={step.id}>
                          {`step ${step.id}: ${step.kind ?? 'UNKNOWN'}${step.abilityOrSkill ? ` · ${step.abilityOrSkill}` : ''}${step.dc === null ? '' : ` · DC ${step.dc}`}${step.diceExpression ? ` · ${step.diceExpression}` : ''}`}
                        </li>)}
                      </ul>
                    ) : null}
                    {unit.detail.outcomes.length > 0 ? (
                      <ul>
                        {unit.detail.outcomes.map(outcome => <li key={outcome.id}>
                          {`outcome ${outcome.id}: ${outcome.label ?? 'UNKNOWN'} · ${outcome.description ?? '값 없음'}`}
                        </li>)}
                      </ul>
                    ) : null}
                    {unit.detail.randomTable.length > 0 ? (
                      <ul>
                        {unit.detail.randomTable.map(entry => <li key={`${entry.range}-${entry.outcome}`}>
                          {`table ${entry.range ?? '?'}: ${entry.outcome ?? '값 없음'}`}
                        </li>)}
                      </ul>
                    ) : null}
                    {unit.sourceRefs.length > 0 ? (
                      <ul>
                        {unit.sourceRefs.map(ref => <li key={`${ref.documentId}-${ref.extractionVersion}-${ref.locator}`}>
                          source: {ref.documentId} v{ref.extractionVersion} · {ref.locator}
                        </li>)}
                      </ul>
                    ) : null}
                    {unit.validationMessages.length > 0 ? (
                      <ul>
                        {unit.validationMessages.map(message => <li key={message}>{message}</li>)}
                      </ul>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
          {playPreparation ? (
            <section aria-labelledby="play-preparation-heading">
              <h3 id="play-preparation-heading">플레이 준비</h3>
              <p>준비 상태 {playPreparation.status} · 패키지 {playPreparation.scenarioPackageId}</p>
              <p>캐릭터 한도: {playPreparation.characterLimit.maximumCharacters}명</p>
              {playPreparation.characterLimit.source ? (
                <p>한도 근거: {playPreparation.characterLimit.source.locator} · {playPreparation.characterLimit.sourceQuote}</p>
              ) : <p>한도 근거: 추출되지 않아 기본값 1명 적용</p>}
              {playPreparation.blockers.length > 0 ? (
                <ul aria-label="준비 차단 사유">
                  {playPreparation.blockers.map(blocker => <li key={blocker}>{blocker}</li>)}
                </ul>
              ) : null}
              {playPreparation.characterCreationBlueprint ? (
                <div>
                  <p>
                    CharacterCreationBlueprint: {playPreparation.characterCreationBlueprint.summary ?? '없음'}
                  </p>
                  <p>상태: {playPreparation.characterCreationBlueprint.status ?? 'READY'} · revision {playPreparation.characterCreationBlueprint.revision ?? 0}</p>
                  <p>
                    {playPreparation.characterCreationBlueprint.rulebookDocumentCount > 0
                      ? `RULEBOOK ${playPreparation.characterCreationBlueprint.rulebookDocumentCount}개 · `
                      : 'RULEBOOK 런타임 세트 별도 · '}
                    STORYBOOK {playPreparation.characterCreationBlueprint.storybookDocumentCount}개
                  </p>
                  {playPreparation.characterCreationBlueprint.diagnostics.length > 0 ? (
                    <ul aria-label="Blueprint 진단">
                      {playPreparation.characterCreationBlueprint.diagnostics.map(diagnostic => (
                        <li key={diagnostic}>{diagnostic}</li>
                      ))}
                    </ul>
                  ) : null}
                  {(playPreparation.characterCreationBlueprint.fields ?? []).length > 0 ? (
                    <fieldset aria-label="Blueprint 캐릭터 필드">
                      <legend>캐릭터 생성 필드</legend>
                      {(playPreparation.characterCreationBlueprint.fields ?? []).map(field => (
                        <label key={field.key}>
                          {field.key}
                          {(field.inputMode ?? 'FREE_TEXT') === 'SINGLE_SELECT' ? (
                            <select
                              aria-label={field.key}
                              value={blueprintValues[field.key] ?? ''}
                              onChange={event => {
                                const value = event.currentTarget.value
                                setBlueprintValues(current => ({ ...current, [field.key]: value }))
                              }}
                            >
                              <option value="">선택하세요</option>
                              {field.options.map(option => <option key={option} value={option}>{option}</option>)}
                            </select>
                          ) : (field.inputMode ?? 'FREE_TEXT') === 'MULTI_SELECT' ? (
                            <select
                              multiple
                              aria-label={field.key}
                              value={(blueprintValues[field.key] ?? '').split(',').filter(Boolean)}
                              onChange={event => {
                                const value = Array.from(event.currentTarget.selectedOptions, option => option.value).join(',')
                                setBlueprintValues(current => ({ ...current, [field.key]: value }))
                              }}
                            >
                              {field.options.map(option => <option key={option} value={option}>{option}</option>)}
                            </select>
                          ) : (
                            <input
                              aria-label={field.key}
                              value={blueprintValues[field.key] ?? ''}
                              onChange={event => {
                                const value = event.currentTarget.value
                                setBlueprintValues(current => ({ ...current, [field.key]: value }))
                              }}
                            />
                          )}
                          {(field.suggestions ?? []).length > 0 ? <small>추천: {field.suggestions?.join(', ')}</small> : null}
                          {field.sourceQuote ? <small>원문 근거: {field.sourceQuote}</small> : null}
                          {(field.evidence ?? []).map(item => <small key={`${item.knowledgeDocumentId}-${item.locator}`}>근거: {item.knowledgeDocumentId} v{item.extractionVersion} · {item.locator}</small>)}
                          {field.inputStatus === 'MANUAL_INPUT_REQUIRED' ? <small>수동 입력 필요</small> : null}
                          {api.resolveBlueprint && playPreparation.characterCreationBlueprint.status === 'NEEDS_REVIEW' ? <button type="button" onClick={() => void resolveBlueprint(field.key)} disabled={!blueprintValues[field.key]}>검토값 저장</button> : null}
                        </label>
                      ))}
                    </fieldset>
                  ) : null}
                  {api.publishBlueprint && playPreparation.characterCreationBlueprint.status !== 'PUBLISHED' ? <button type="button" disabled={publishingBlueprint || playPreparation.status !== 'READY'} onClick={() => void publishBlueprint()}>{publishingBlueprint ? '게시 중…' : 'Blueprint 게시'}</button> : null}
                </div>
              ) : null}
              {sessionApi && playPreparation.status === 'READY' && playPreparation.characterCreationBlueprint.available && playPreparation.characterCreationBlueprint.status === 'PUBLISHED' ? (
                <button type="button" onClick={() => void createSession()}>세션 초안 생성 후 캐릭터 만들기</button>
              ) : null}
              {canCreateCharacter ? (
                <section aria-labelledby="character-creation-heading">
                  <h4 id="character-creation-heading">캐릭터 생성</h4>
                  <form onSubmit={createCharacter}>
                    <label>
                      캐릭터 이름
                      <input
                        value={characterName}
                        onChange={event => setCharacterName(event.currentTarget.value)}
                        aria-label="캐릭터 이름"
                      />
                    </label>
                    <label>
                      에디션
                      <select
                        aria-label="캐릭터 에디션"
                        value={characterEdition}
                        onChange={event => setCharacterEdition(event.currentTarget.value as 'DND_5E_2014' | 'DND_5E_2024')}
                      >
                        <option value="DND_5E_2024">DND 5E 2024</option>
                        <option value="DND_5E_2014">DND 5E 2014</option>
                      </select>
                    </label>
                    <label>
                      레벨
                      <input
                        type="number"
                        min={1}
                        max={20}
                        value={characterLevel}
                        onChange={event => setCharacterLevel(Number(event.currentTarget.value))}
                        aria-label="캐릭터 레벨"
                      />
                    </label>
                    <label>
                      영감
                      <input
                        type="checkbox"
                        checked={characterInspiration}
                        onChange={event => setCharacterInspiration(event.currentTarget.checked)}
                        aria-label="영감"
                      />
                    </label>
                    <button type="submit" disabled={creatingCharacter || !characterName.trim() || !canCreateCharacter}>
                      {creatingCharacter ? '생성 중…' : '캐릭터 시트 생성'}
                    </button>
                  </form>
                  {createdCharacterSheet ? (
                    <p role="status">
                      캐릭터 시트 {createdCharacterSheet.characterSheetId} 생성 완료.
                      <a href={`#/character/${createdCharacterSheet.characterSheetId}`}>캐릭터 보기</a>
                    </p>
                  ) : null}
                </section>
              ) : null}
            </section>
          ) : null}
          {runtimeOptions ? (
            <section aria-labelledby="runtime-options-heading">
              <h3 id="runtime-options-heading">런타임 옵션</h3>
              <details open>
                <summary>엔진 선택지</summary>
                <label>
                  엔진
                  <select aria-label="런타임 엔진" value={selectedEngineId} onChange={event => setSelectedEngineId(event.currentTarget.value)}>
                    {runtimeOptions.engines.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
                  </select>
                </label>
              </details>
              <details open>
                <summary>도구 선택지</summary>
                <fieldset>
                  <legend>도구</legend>
                  {runtimeOptions.tools.map(option => (
                    <label key={option.id}>
                      <input
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
  if (!api.getScenarioCompilation) throw new Error('시나리오 패키지 컴파일 상태 API가 없습니다.')
  let current = started
  for (let attempts = 0; attempts < compilationPollLimit; attempts += 1) {
    if (current.status === 'PUBLISHED') return current
    if (current.status === 'FAILED') {
      throw new Error(current.failureReason || '시나리오 패키지 컴파일에 실패했습니다.')
    }
    await sleep(compilationPollIntervalMs)
    current = await api.getScenarioCompilation(current.compilationId)
    onProgress(current)
  }
  throw new Error('시나리오 패키지 컴파일이 아직 진행 중입니다. 잠시 후 상태를 다시 확인하세요.')
}

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}
