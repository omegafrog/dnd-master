import { useEffect, useState } from 'react'
import type { AdventureSessionView, SessionControlMode } from '../adventure-session/AdventureSessionApi'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CharacterCreationDraft, CreatedCharacterSheetView, PlayPreparationView, SetupApi } from '../rulebooks/SetupApi'
import { CharacterInputTree } from './CharacterInputTree'

type SessionApi = Pick<AdventureSessionApi, 'read' | 'addMember' | 'start'>
type CharacterSetupApi = Pick<SetupApi, 'getPlayPreparation' | 'createCharacterSheet' | 'resolveBlueprint' | 'addBlueprintChild' | 'publishBlueprint'>

export function CharacterCreationPage({ sessionId, setupApi, sessionApi }: { sessionId: string; setupApi: CharacterSetupApi; sessionApi: SessionApi }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [preparation, setPreparation] = useState<PlayPreparationView | null>(null)
  const [values, setValues] = useState<Record<string, string>>({})
  const [name, setName] = useState('')
  const [level, setLevel] = useState(1)
  const [edition, setEdition] = useState<CharacterCreationDraft['edition']>('DND_5E_2024')
  const [mode, setMode] = useState<SessionControlMode>('DIRECT')
  const [created, setCreated] = useState<CreatedCharacterSheetView | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    let active = true
    void sessionApi.read(sessionId).then(next => {
      if (!active) return
      setSession(next)
      return next.scenarioPackageId ? setupApi.getPlayPreparation?.(next.scenarioPackageId) : undefined
    }).then(next => { if (active && next) setPreparation(next) })
      .catch(error => { if (active) setMessage(error instanceof Error ? error.message : '세션 준비를 불러오지 못했습니다.') })
    return () => { active = false }
  }, [sessionApi, sessionId, setupApi])

  async function create() {
    if (!session || !preparation || !name.trim() || !setupApi.createCharacterSheet) return
    try {
      const flattenNodes = (nodes: NonNullable<typeof preparation.characterCreationBlueprint.roots>): typeof nodes[number][] => nodes.flatMap(node => [node, ...flattenNodes(node.children)])
      const nodes = flattenNodes(preparation.characterCreationBlueprint.roots ?? [])
      const resolvedValues = Object.fromEntries(nodes
        .map(node => [node.id, values[node.id] ?? node.value ?? ''])
        .filter(([, value]) => value !== ''))
      const nodeValue = (...keys: string[]) => keys.map(key => {
        const direct = values[key]
        if (direct != null) return direct
        const node = nodes.find(item => item.id === key || item.key === key)
        return node ? values[node.id] ?? node.value ?? '' : undefined
      }).find(value => value != null) ?? ''
      const scoreRoot = nodes.find(node => node.key === 'starting_ability_scores')
      const nestedStartingAbilities = (scoreRoot ? flattenNodes(scoreRoot.children) : [])
        .filter(node => (values[node.id] ?? node.value ?? '').trim())
        .map(node => `${node.key}=${values[node.id] ?? node.value ?? ''}`)
        .join(',')
      const next = await setupApi.createCharacterSheet({
        sessionId,
        edition,
        characterName: name.trim(),
        level,
        inspiration: false,
        race: nodeValue('race'),
        characterClass: nodeValue('characterClass', 'class'),
        background: nodeValue('background'),
        startingAbilities: nodeValue('startingAbilities', 'starting_ability_scores') || nestedStartingAbilities,
        blueprintRevision: session.blueprintRevision,
        blueprintValues: resolvedValues,
      })
      setCreated(next)
      setMessage(`캐릭터 시트 ${next.characterSheetId} 생성 완료.`)
    } catch (error) { setMessage(error instanceof Error ? error.message : '캐릭터를 생성하지 못했습니다.') }
  }

  async function resolveNode(nodeId: string) {
    if (!session?.scenarioPackageId || !preparation || !setupApi.resolveBlueprint || !setupApi.getPlayPreparation) return
    const value = values[nodeId] ?? ''
    if (!value) return
    try {
      await setupApi.resolveBlueprint(session.scenarioPackageId, nodeId, value, preparation.characterCreationBlueprint.revision ?? 0)
      setPreparation(await setupApi.getPlayPreparation(session.scenarioPackageId))
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Blueprint 검토를 저장하지 못했습니다.') }
  }

  async function addChild(parentId: string) {
    if (!session?.scenarioPackageId || !preparation || !setupApi.addBlueprintChild || !setupApi.getPlayPreparation) return
    const key = window.prompt('하위 필드 key')?.trim()
    if (!key) return
    const label = window.prompt('하위 필드 이름', key)?.trim() || key
    try {
      await setupApi.addBlueprintChild(session.scenarioPackageId, preparation.characterCreationBlueprint.revision ?? 0, parentId, key, label)
      setPreparation(await setupApi.getPlayPreparation(session.scenarioPackageId))
    } catch (error) { setMessage(error instanceof Error ? error.message : '하위 필드를 추가하지 못했습니다.') }
  }

  async function publish() {
    if (!session?.scenarioPackageId || !setupApi.publishBlueprint || !setupApi.getPlayPreparation) return
    try {
      await setupApi.publishBlueprint(session.scenarioPackageId)
      setPreparation(await setupApi.getPlayPreparation(session.scenarioPackageId))
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Blueprint 게시에 실패했습니다.') }
  }

  async function addToParty() {
    if (!session || !created) return
    try {
      setSession(await sessionApi.addMember(sessionId, session.version, {
        characterSheetId: created.characterSheetId,
        controlMode: mode,
        nameMutableAfterStart: false,
        raceMutableAfterStart: false,
        characterClassMutableAfterStart: false,
        backgroundMutableAfterStart: false,
        startingAbilitiesMutableAfterStart: false,
        levelMutableAfterStart: false,
      }))
      setMessage('캐릭터를 파티에 추가했습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '파티 추가에 실패했습니다.') }
  }

  if (!session || !preparation) return <p role="status">{message || '캐릭터 생성 준비를 불러오는 중…'}</p>
  const blueprint = preparation.characterCreationBlueprint
  const blocked = preparation.status !== 'READY' || !blueprint.available || blueprint.status !== 'PUBLISHED'
  return <section aria-labelledby="character-creation-heading">
    <h2 id="character-creation-heading">캐릭터 생성</h2>
    <p>세션 ID: {session.sessionId}</p>
    <p>Blueprint revision: {session.blueprintRevision} · {blueprint.summary ?? 'Blueprint'}</p>
    {blueprint.diagnostics.length > 0 && <ul aria-label="Blueprint 진단">{blueprint.diagnostics.map(item => <li key={item}>{item}</li>)}</ul>}
    {blocked && <p role="alert">Blueprint 검토 또는 게시가 완료되지 않아 캐릭터를 생성할 수 없습니다.</p>}
    {(blueprint.roots ?? []).length > 0 ? <CharacterInputTree
      nodes={blueprint.roots ?? []}
      values={values}
      onChange={(id, value) => setValues(current => ({ ...current, [id]: value }))}
      onResolve={resolveNode}
      onAddChild={addChild}
      canResolve={Boolean(setupApi.resolveBlueprint && blueprint.status === 'NEEDS_REVIEW')}
    /> : <fieldset aria-label="Blueprint 캐릭터 필드">
      <legend>Blueprint 필드</legend>
      {(blueprint.fields ?? []).map(field => <label key={field.key}>{field.key}
        {(field.inputMode ?? 'FREE_TEXT') === 'SINGLE_SELECT' ? <select aria-label={field.key} required={field.required} value={values[field.key] ?? ''} onChange={event => { const value = event.currentTarget.value; setValues(current => ({ ...current, [field.key]: value })) }}><option value="">선택하세요</option>{field.options.map(option => <option key={option} value={option}>{option}</option>)}</select>
          : (field.inputMode ?? 'FREE_TEXT') === 'MULTI_SELECT' ? <select multiple aria-label={field.key} required={field.required} value={(values[field.key] ?? '').split(',').filter(Boolean)} onChange={event => { const value = Array.from(event.currentTarget.selectedOptions, option => option.value).join(','); setValues(current => ({ ...current, [field.key]: value })) }}>{field.options.map(option => <option key={option} value={option}>{option}</option>)}</select>
            : <input aria-label={field.key} required={field.required} value={values[field.key] ?? ''} onChange={event => { const value = event.currentTarget.value; setValues(current => ({ ...current, [field.key]: value })) }} />}
        {(field.suggestions ?? []).length > 0 && <small>추천: {field.suggestions?.join(', ')}</small>}
        {field.sourceQuote && <small>원문 근거: {field.sourceQuote}</small>}
        {field.inputStatus === 'MANUAL_INPUT_REQUIRED' && <small>수동 입력 필요 · 근거: {field.sourceType}</small>}
        {(field.constraints ?? []).map(item => <small key={item}>제약: {item}</small>)}
        {(field.evidence ?? []).map(item => <small key={`${item.knowledgeDocumentId}-${item.locator}`}>근거: {item.knowledgeDocumentId} v{item.extractionVersion} · {item.locator}</small>)}
        {field.diagnostics.map(item => <small key={item}>{item}</small>)}
      </label>)}
    </fieldset>}
    {setupApi.publishBlueprint && blueprint.status !== 'PUBLISHED' ? <button type="button" onClick={() => void publish()} disabled={preparation.status !== 'READY'}>Blueprint 게시</button> : null}
    <label>이름 <input aria-label="캐릭터 이름" value={name} onChange={event => setName(event.currentTarget.value)} required /></label>
    <label>레벨 <input aria-label="캐릭터 레벨" type="number" min={1} max={20} value={level} onChange={event => setLevel(Number(event.currentTarget.value))} /></label>
    <label>에디션 <select aria-label="캐릭터 에디션" value={edition} onChange={event => setEdition(event.currentTarget.value as CharacterCreationDraft['edition'])}><option value="DND_5E_2024">DND 5E 2024</option><option value="DND_5E_2014">DND 5E 2014</option></select></label>
    <button type="button" disabled={blocked || !name.trim()} onClick={() => void create()}>캐릭터 시트 생성</button>
    {created && <section aria-label="파티 준비"><p role="status">{message}</p><label>플레이 방식 <select aria-label="플레이 방식" value={mode} onChange={event => setMode(event.currentTarget.value as SessionControlMode)}><option value="DIRECT">직접 플레이</option><option value="AGENT">에이전트</option></select></label><button type="button" disabled={session.party.length >= session.characterLimit} onClick={() => void addToParty()}>파티에 추가</button><a href={`#/sessions/${sessionId}`}>파티 전체 관리</a></section>}
    {message && !created && <p role="alert">{message}</p>}
  </section>
}
