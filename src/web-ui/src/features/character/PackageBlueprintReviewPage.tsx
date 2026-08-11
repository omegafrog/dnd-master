import { useEffect, useMemo, useState } from 'react'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CharacterInputNodeView, PlayPreparationView, SetupApi } from '../rulebooks/SetupApi'
import { CharacterInputTree } from './CharacterInputTree'

type PackageSetupApi = {
  getPlayPreparation: NonNullable<SetupApi['getPlayPreparation']>
  generateBlueprintDraft?: SetupApi['generateBlueprintDraft']
  resolveBlueprint?: SetupApi['resolveBlueprint']
  addBlueprintChild?: SetupApi['addBlueprintChild']
  publishBlueprint?: SetupApi['publishBlueprint']
}

type CatalogRulebook = {
  catalogRevisionId: string
  displayName: string
  edition: string
  rulebookId: string | null
  status: string
  extractionVersion: number
}

type SessionApi = Pick<AdventureSessionApi, 'create'>
type SaveState = 'DIRTY' | 'SAVING' | 'SAVED' | 'ERROR'

export function PackageBlueprintReviewPage({
  packageId,
  setupApi,
  sessionApi,
  onSessionCreated,
}: {
  packageId: string
  setupApi: PackageSetupApi
  sessionApi: SessionApi
  onSessionCreated: (sessionId: string) => void
}) {
  const [preparation, setPreparation] = useState<PlayPreparationView | null>(null)
  const [values, setValues] = useState<Record<string, string>>({})
  const [saveStates, setSaveStates] = useState<Record<string, SaveState>>({})
  const [message, setMessage] = useState('')
  const [creatingSession, setCreatingSession] = useState(false)
  const [catalogRulebooks, setCatalogRulebooks] = useState<CatalogRulebook[]>([])
  const [selectedCatalogRulebookId, setSelectedCatalogRulebookId] = useState('')
  const [generatingDraft, setGeneratingDraft] = useState(false)

  useEffect(() => {
    let active = true
    void setupApi.getPlayPreparation(packageId)
      .then(next => { if (active) setPreparation(next) })
      .catch(error => { if (active) setMessage(error instanceof Error ? error.message : '캐릭터 생성 설정을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [packageId, setupApi])

  useEffect(() => {
    let active = true
    void fetch('/api/v1/rulebook-catalog')
      .then(response => response.ok ? response.json() : [])
      .then((items: CatalogRulebook[]) => {
        if (!active) return
        const ready = items.filter(item => item.status === 'READY' && item.rulebookId && item.extractionVersion > 0)
        setCatalogRulebooks(ready)
        if (ready.length === 1) setSelectedCatalogRulebookId(ready[0].rulebookId!)
      })
      .catch(() => { if (active) setCatalogRulebooks([]) })
    return () => { active = false }
  }, [])

  const nodes = useMemo(() => flatten(preparation?.characterCreationBlueprint.roots ?? []), [preparation])
  const dirtyCount = Object.values(saveStates).filter(state => state === 'DIRTY' || state === 'ERROR').length

  function changeValue(nodeId: string, value: string) {
    setValues(current => ({ ...current, [nodeId]: value }))
    setSaveStates(current => ({ ...current, [nodeId]: 'DIRTY' }))
  }

  async function resolveNode(nodeId: string) {
    if (!preparation || !setupApi.resolveBlueprint) return
    const value = values[nodeId] ?? nodes.find(node => node.id === nodeId)?.value ?? ''
    if (!value) {
      setMessage('저장할 값을 먼저 선택하거나 입력하세요.')
      return
    }
    setSaveStates(current => ({ ...current, [nodeId]: 'SAVING' }))
    try {
      await setupApi.resolveBlueprint(packageId, nodeId, value, preparation.characterCreationBlueprint.revision ?? 0)
      setPreparation(await setupApi.getPlayPreparation(packageId))
      setSaveStates(current => ({ ...current, [nodeId]: 'SAVED' }))
      setMessage('검토값을 저장했습니다.')
    } catch (error) {
      setSaveStates(current => ({ ...current, [nodeId]: 'ERROR' }))
      setMessage(error instanceof Error ? error.message : '검토값 저장에 실패했습니다.')
    }
  }

  async function addChild(parentId: string) {
    if (!preparation || !setupApi.addBlueprintChild) return
    const key = window.prompt('추가할 시나리오 전용 필드 key')?.trim()
    if (!key) return
    const label = window.prompt('표시 이름', key)?.trim() || key
    try {
      await setupApi.addBlueprintChild(packageId, preparation.characterCreationBlueprint.revision ?? 0, parentId, key, label)
      setPreparation(await setupApi.getPlayPreparation(packageId))
      setMessage('하위 필드를 추가했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '하위 필드를 추가하지 못했습니다.')
    }
  }

  async function publish() {
    if (!setupApi.publishBlueprint) return
    try {
      await setupApi.publishBlueprint(packageId)
      setPreparation(await setupApi.getPlayPreparation(packageId))
      setMessage('캐릭터 생성 설정을 게시했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '설정 게시에 실패했습니다.')
    }
  }

  async function generateDraft() {
    const selected = catalogRulebooks.find(item => item.rulebookId === selectedCatalogRulebookId)
    if (!selected || !setupApi.generateBlueprintDraft) {
      setMessage('기본 스키마에 사용할 공개 룰북을 선택하세요.')
      return
    }
    setGeneratingDraft(true)
    try {
      await setupApi.generateBlueprintDraft(packageId, selected.rulebookId!, selected.extractionVersion)
      setPreparation(await setupApi.getPlayPreparation(packageId))
      setValues({})
      setSaveStates({})
      setMessage('룰북 기본 스키마와 스토리북 추가 필드를 새로 추출했습니다. 스토리북 제안을 검토하세요.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '캐릭터 시트 스키마를 생성하지 못했습니다.')
    } finally {
      setGeneratingDraft(false)
    }
  }

  async function createSession() {
    const blueprint = preparation?.characterCreationBlueprint
    if (!blueprint?.available || blueprint.status !== 'PUBLISHED' || blueprint.revision == null) return
    setCreatingSession(true)
    try {
      const session = await sessionApi.create({
        scenarioPackageId: packageId,
        blueprintId: packageId,
        blueprintRevision: blueprint.revision,
      })
      onSessionCreated(session.sessionId)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '세션 초안을 생성하지 못했습니다.')
    } finally {
      setCreatingSession(false)
    }
  }

  if (!preparation) return <p role="status">{message || '캐릭터 생성 설정을 불러오는 중…'}</p>

  const blueprint = preparation.characterCreationBlueprint
  return (
    <section aria-labelledby="package-blueprint-review-heading">
      <h2 id="package-blueprint-review-heading">캐릭터 생성 설정 검토</h2>
      <p>시나리오 패키지 {packageId}</p>
      <p><a href="#/setup">새 패키지 만들기</a></p>
      <p>룰북은 기본 시트 구조와 선택지를 만들고, 스토리북은 별도의 검토 가능한 추가 필드를 제안합니다.</p>
      <label>
        기본 룰북
        <select aria-label="기본 룰북" value={selectedCatalogRulebookId} onChange={event => setSelectedCatalogRulebookId(event.target.value)}>
          <option value="">선택하세요</option>
          {catalogRulebooks.map(rulebook => <option key={rulebook.catalogRevisionId} value={rulebook.rulebookId!}>{rulebook.displayName} · {rulebook.edition}</option>)}
        </select>
      </label>
      <button type="button" onClick={() => void generateDraft()} disabled={generatingDraft || !selectedCatalogRulebookId}>
        {generatingDraft ? '룰북·스토리북 분석 중…' : '룰북으로 기본 스키마 생성'}
      </button>
      {message && <p role="status">{message}</p>}
      {preparation.blockers.length > 0 && (
        <ul aria-label="준비 차단 사유">{preparation.blockers.map(blocker => <li key={blocker}>{blocker}</li>)}</ul>
      )}
      {blueprint.diagnostics.length > 0 && (
        <ul aria-label="설정 진단">{blueprint.diagnostics.map(item => <li key={item}>{item}</li>)}</ul>
      )}
      <CharacterInputTree
        nodes={blueprint.roots ?? []}
        values={values}
        onChange={changeValue}
        onResolve={resolveNode}
        onAddChild={addChild}
        canResolve={blueprint.status === 'NEEDS_REVIEW'}
      />
      {blueprint.status !== 'PUBLISHED' ? (
        <button type="button" onClick={() => void publish()} disabled={dirtyCount > 0 || preparation.status !== 'READY'}>
          검토 완료 후 게시
        </button>
      ) : (
        <button type="button" onClick={() => void createSession()} disabled={creatingSession}>
          {creatingSession ? '세션 생성 중…' : '세션 생성 후 캐릭터 만들기로 이동'}
        </button>
      )}
    </section>
  )
}

function flatten(nodes: CharacterInputNodeView[]): CharacterInputNodeView[] {
  return nodes.flatMap(node => [node, ...flatten(node.children)])
}
