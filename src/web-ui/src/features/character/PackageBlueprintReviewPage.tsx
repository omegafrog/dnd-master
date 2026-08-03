import { useEffect, useMemo, useState } from 'react'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CharacterInputNodeView, PlayPreparationView, SetupApi } from '../rulebooks/SetupApi'
import { CharacterInputTree } from './CharacterInputTree'

type PackageSetupApi = {
  getPlayPreparation: NonNullable<SetupApi['getPlayPreparation']>
  resolveBlueprint?: SetupApi['resolveBlueprint']
  addBlueprintChild?: SetupApi['addBlueprintChild']
  publishBlueprint?: SetupApi['publishBlueprint']
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

  useEffect(() => {
    let active = true
    void setupApi.getPlayPreparation(packageId)
      .then(next => { if (active) setPreparation(next) })
      .catch(error => { if (active) setMessage(error instanceof Error ? error.message : '캐릭터 생성 설정을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [packageId, setupApi])

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
      <p>스토리북 제안과 추가 필드를 검토합니다. 실제 캐릭터 선택은 다음 캐릭터 생성 페이지에서 진행합니다.</p>
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
