import { useEffect, useMemo, useState } from 'react'
import type { AdventureSessionView } from '../adventure-session/AdventureSessionApi'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { PlayPreparationView, SetupApi } from '../rulebooks/SetupApi'
import { CharacterInputTree } from './CharacterInputTree'

type SessionApi = Pick<AdventureSessionApi, 'read'>
type ReviewSetupApi = {
  getPlayPreparation: NonNullable<SetupApi['getPlayPreparation']>
  resolveBlueprint?: SetupApi['resolveBlueprint']
  addBlueprintChild?: SetupApi['addBlueprintChild']
  publishBlueprint?: SetupApi['publishBlueprint']
}
type SaveState = 'DIRTY' | 'SAVING' | 'SAVED' | 'ERROR'

export function CharacterBlueprintReviewPage({ sessionId, setupApi, sessionApi }: { sessionId: string; setupApi: ReviewSetupApi; sessionApi: SessionApi }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [preparation, setPreparation] = useState<PlayPreparationView | null>(null)
  const [values, setValues] = useState<Record<string, string>>({})
  const [saveStates, setSaveStates] = useState<Record<string, SaveState>>({})
  const [message, setMessage] = useState('')
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    void sessionApi.read(sessionId).then(next => {
      if (!active) return undefined
      setSession(next)
      return next.scenarioPackageId ? setupApi.getPlayPreparation(next.scenarioPackageId) : undefined
    }).then(next => { if (active && next) setPreparation(next) })
      .catch(error => { if (active) setMessage(error instanceof Error ? error.message : '검토 정보를 불러오지 못했습니다.') })
    return () => { active = false }
  }, [sessionApi, sessionId, setupApi])

  const nodes = useMemo(() => flatten(preparation?.characterCreationBlueprint.roots ?? []), [preparation])
  const reviewNodes = nodes.filter(node => node.status === 'CONFLICT_REVIEW' || node.status === 'PARTIALLY_EXTRACTED' || node.diagnostics.length > 0)
  const reviewedCount = reviewNodes.filter(node => node.status === 'REVIEWED' || saveStates[node.id] === 'SAVED').length
  const dirtyCount = Object.values(saveStates).filter(state => state === 'DIRTY' || state === 'ERROR').length

  function changeValue(nodeId: string, value: string) {
    setValues(current => ({ ...current, [nodeId]: value }))
    setSaveStates(current => ({ ...current, [nodeId]: 'DIRTY' }))
  }

  async function resolveNode(nodeId: string) {
    if (!session?.scenarioPackageId || !preparation || !setupApi.resolveBlueprint) return
    const value = values[nodeId] ?? nodes.find(node => node.id === nodeId)?.value ?? ''
    if (!value) { setMessage('저장할 값을 먼저 선택하거나 입력하세요.'); return }
    setSaveStates(current => ({ ...current, [nodeId]: 'SAVING' }))
    try {
      await setupApi.resolveBlueprint(session.scenarioPackageId, nodeId, value, preparation.characterCreationBlueprint.revision ?? 0)
      const next = await setupApi.getPlayPreparation(session.scenarioPackageId)
      setPreparation(next)
      setSaveStates(current => ({ ...current, [nodeId]: 'SAVED' }))
      setLastSavedAt(new Date().toLocaleTimeString())
      setMessage('검토값을 저장했습니다.')
    } catch (error) {
      setSaveStates(current => ({ ...current, [nodeId]: 'ERROR' }))
      setMessage(error instanceof Error ? error.message : '검토값 저장에 실패했습니다.')
    }
  }

  async function addChild(parentId: string) {
    if (!session?.scenarioPackageId || !preparation || !setupApi.addBlueprintChild) return
    const key = window.prompt('추가할 시나리오 전용 필드 key')?.trim()
    if (!key) return
    const label = window.prompt('표시 이름', key)?.trim() || key
    try {
      await setupApi.addBlueprintChild(session.scenarioPackageId, preparation.characterCreationBlueprint.revision ?? 0, parentId, key, label)
      setPreparation(await setupApi.getPlayPreparation(session.scenarioPackageId))
      setMessage('하위 필드를 추가했습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '하위 필드를 추가하지 못했습니다.') }
  }

  async function publish() {
    if (!session?.scenarioPackageId || !setupApi.publishBlueprint) return
    try {
      await setupApi.publishBlueprint(session.scenarioPackageId)
      setPreparation(await setupApi.getPlayPreparation(session.scenarioPackageId))
      setMessage('캐릭터 생성 설정을 게시했습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '설정 게시에 실패했습니다.') }
  }

  if (!session || !preparation) return <p role="status">{message || '캐릭터 생성 설정을 불러오는 중…'}</p>
  const blueprint = preparation.characterCreationBlueprint
  return <section aria-labelledby="blueprint-review-heading">
    <h2 id="blueprint-review-heading">캐릭터 생성 설정 검토</h2>
    <p>이 페이지에서는 스토리북 제안과 추가 필드만 검토합니다. 실제 종족·클래스·장비 선택은 캐릭터 생성 페이지에서 합니다.</p>
    <section aria-label="검토 저장 상태">
      <p>검토 완료 {reviewedCount} / {reviewNodes.length} · 저장되지 않은 변경 {dirtyCount}개</p>
      <p>마지막 저장: {lastSavedAt ?? '아직 저장하지 않음'}</p>
      {message && <p role="status">{message}</p>}
    </section>
    {blueprint.diagnostics.length > 0 && <ul aria-label="설정 진단">{blueprint.diagnostics.map(item => <li key={item}>{item}</li>)}</ul>}
    <CharacterInputTree nodes={blueprint.roots ?? []} values={values} onChange={changeValue} onResolve={resolveNode} onAddChild={addChild} canResolve={blueprint.status === 'NEEDS_REVIEW'} />
    <ul aria-label="항목별 저장 상태">{nodes.map(node => <li key={node.id}>{node.label}: {statusLabel(node.status, saveStates[node.id])}</li>)}</ul>
    {blueprint.status !== 'PUBLISHED' && <button type="button" onClick={() => void publish()} disabled={dirtyCount > 0 || preparation.status !== 'READY'}>검토 완료 후 게시</button>}
    {blueprint.status === 'PUBLISHED' && <><p>설정이 게시되었습니다.</p><a href={`#/sessions/${sessionId}/character`}>캐릭터 생성 페이지로 이동</a></>}
  </section>
}

function flatten(nodes: NonNullable<PlayPreparationView['characterCreationBlueprint']['roots']>): typeof nodes[number][] { return nodes.flatMap(node => [node, ...flatten(node.children)]) }
function statusLabel(serverStatus: string, state?: SaveState) {
  if (state === 'DIRTY') return '저장되지 않음'
  if (state === 'SAVING') return '저장 중'
  if (state === 'SAVED' || serverStatus === 'REVIEWED') return '저장됨'
  if (state === 'ERROR') return '저장 실패'
  return serverStatus === 'CONFLICT_REVIEW' || serverStatus === 'PARTIALLY_EXTRACTED' ? '미검토' : '검토 불필요'
}
