import { expect } from '@playwright/test'
import { basename } from 'node:path'
import { resolve } from 'node:path'
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const storybooks = JSON.parse(process.env.BACKEND_E2E_STORYBOOKS_JSON ?? '[]') as Array<{ path: string, role: string }>
const root = new URL('../../../..', import.meta.url).pathname
const assets = resolve(root, '../dnd-master/docs/assets')
export const explorationGroupStorybooks = [
  { path: resolve(assets, '892902-A_Most_Potent_Brew.pdf'), role: 'MAIN_SCENARIO' },
  { path: resolve(assets, '892902-A_Potent_Brew_Map.pdf'), role: 'MAP' },
  { path: resolve(assets, '892902-A_Most_Potent_Brew_Player_Handout.pdf'), role: 'HANDOUT' },
] as const
export const explorationGroupRulebookPath = resolve(assets, 'DnD_BasicRules_2018.pdf')
export const explorationGroupLearnedAbilityIds = ['spell-node-2810-fireball'] as const
const composeFile = `${root}src/infra/compose.yaml`

type Auth = { ownerPlayerId: string, headers: Record<string, string> }
type PreparedPackage = { packageId: string, blueprintRevision: number, rulebookId: string, mapDocumentId: string }
type DirectApiResponse = { ok(): boolean, text(): Promise<string>, json(): Promise<unknown> }
export type DirectApi = {
  get(url: string, options?: { headers?: Record<string, string> }): Promise<DirectApiResponse>
  post(url: string, options?: { headers?: Record<string, string>, data?: unknown, multipart?: FormData }): Promise<DirectApiResponse>
}

export function createDirectApi(): DirectApi {
  const send = async (method: string, url: string, options: { headers?: Record<string, string>, data?: unknown, multipart?: FormData } = {}) => {
    const headers: Record<string, string> = { ...options.headers }
    const body = options.multipart ?? (options.data === undefined ? undefined : JSON.stringify(options.data))
    if (options.data !== undefined) headers['Content-Type'] ??= 'application/json'
    const response = await fetch(url, { method, headers, body, signal: AbortSignal.timeout(120_000) })
    let bodyText: Promise<string> | undefined
    const readBody = () => bodyText ??= response.text()
    return { ok: () => response.ok, text: readBody, json: async () => JSON.parse(await readBody()) as unknown }
  }
  return { get: (url, options) => send('GET', url, options), post: (url, options) => send('POST', url, options) }
}

export function hasRuntimeEnvironment() {
  return hasRuntimeCredentials() && storybooks.length > 0
}

export function hasRuntimeCredentials() {
  return Boolean(backend && email && password)
}

export function assertPotentBrewStorybooks(sources = storybooks) {
  const expected = new Map([
    ['MAIN_SCENARIO', '892902-A_Most_Potent_Brew.pdf'],
    ['MAP', '892902-A_Potent_Brew_Map.pdf'],
    ['HANDOUT', '892902-A_Most_Potent_Brew_Player_Handout.pdf'],
  ])
  const roles = new Set(sources.map(source => source.role))
  const missingRoles = [...expected.keys()].filter(role => !roles.has(role))
  if (missingRoles.length) throw new Error(`BACKEND_E2E_STORYBOOKS_JSON is missing required roles: ${missingRoles.join(', ')}`)
  const invalidPath = sources.find(source => !source.path.startsWith(`${assets}/`))
  if (invalidPath) throw new Error(`storybook ${invalidPath.role} must use a Linux docs/assets path`)
  if (sources.length !== expected.size || sources.some(source => expected.get(source.role) !== basename(source.path))) {
    throw new Error('BACKEND_E2E_STORYBOOKS_JSON must use the original Potent Brew file for each role')
  }
}

export function isStorybookReady(status: string) {
  return ['EXTRACTED', 'INDEXED', 'INDEXED_WITH_WARNINGS', 'PARTIAL_CONFIRMED'].includes(status)
}

export function sessionCreationPayload(packageId: string, blueprintRevision: number, partySize: number, preparationId: string) {
  return { scenarioPackageId: packageId, blueprintId: packageId, blueprintRevision, partySize, preparationId }
}

export async function bootstrapStartedAdventure(request: DirectApi, partyMemberCount = 1, sources = storybooks,
    learnedAbilityIds: readonly string[] = []) {
  const { auth, sessionId, version } = await createAdventureSession(request, partyMemberCount, sources, learnedAbilityIds)
  const started = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/start`, {
    headers: { ...auth.headers, 'If-Match-Version': String(version), 'Idempotency-Key': crypto.randomUUID() },
    data: { adventureId: crypto.randomUUID(), prepareMapOnly: false },
  })
  expect(started.ok(), await started.text()).toBeTruthy()
  const view = await started.json() as { adventureId: string, status: string }
  expect(view.status).toBe('STARTED')
  return { adventureId: view.adventureId, sessionId }
}

export async function bootstrapAdventureSession(request: DirectApi, partyMemberCount = 1, sources = storybooks,
    learnedAbilityIds: readonly string[] = []) {
  const { auth, sessionId, packageId, rulebookId, mapDocumentId, version } = await createAdventureSession(request, partyMemberCount, sources, learnedAbilityIds)
  return { sessionId, packageId, rulebookId, mapDocumentId, version, authHeaders: auth.headers }
}

async function createAdventureSession(request: DirectApi, partyMemberCount: number, sources: ReadonlyArray<{ path: string, role: string }>,
    learnedAbilityIds: readonly string[]) {
  const auth = await login(request)
  const prepared = await createPreparedPackage(request, auth, sources)
  const session = await createSession(request, auth, prepared, partyMemberCount)
  let version = session.version
  for (let member = 0; member < partyMemberCount; member++) {
    const sheetId = await createCharacter(request, auth, session.sessionId, member === 0 ? learnedAbilityIds : [])
    const party = await request.post(`${backend}/api/v1/adventure-sessions/${session.sessionId}/party`, {
      headers: { ...auth.headers, 'If-Match-Version': String(version) },
      data: { characterSheetId: sheetId, controlMode: 'DIRECT', nameMutableAfterStart: false, raceMutableAfterStart: false,
        characterClassMutableAfterStart: false, backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false },
    })
    expect(party.ok(), await party.text()).toBeTruthy()
    version = (await party.json() as { version: number }).version
  }
  if (learnedAbilityIds.length > 0) {
    const provider = await request.get(`${backend}/api/v1/adventure-sessions/${session.sessionId}/gm-provider`, { headers: auth.headers })
    expect(provider.ok(), await provider.text()).toBeTruthy()
    expect(await provider.json()).toEqual(expect.objectContaining({ provider: 'codex-cli', model: 'gpt-6-luna', version: expect.any(Number) }))
  }
  return { auth, sessionId: session.sessionId, packageId: prepared.packageId, rulebookId: prepared.rulebookId,
    mapDocumentId: prepared.mapDocumentId, version }
}

export async function readStoredMapDefinitions(packageId: string) {
  const sql = `SELECT map_definitions_json FROM scenario_package WHERE package_id = '${packageId}'::uuid`
  const { stdout } = await dockerExec([
    'compose', '-p', composeProjectName(),
    '-f', composeFile, 'exec', '-T', 'postgres', 'psql', '--username', 'postgres', '--dbname', 'postgres',
    '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql,
  ])
  expect(stdout.trim(), 'scenario package map definitions should be persisted').not.toBe('')
  return JSON.parse(stdout.trim()) as Array<{
    id: string
    assetId?: string
    assetLocator?: string
    source?: { knowledgeDocumentId?: string | { value?: string }, locator?: string }
  }>
}

export async function readPersistedMapVisibilityRuleTurn(mapId: string) {
  const sql = `SELECT visibility_rule_turn FROM combat_map WHERE map_id = '${mapId}'::uuid`
  const { stdout } = await dockerExec([
    'compose', '-p', composeProjectName(),
    '-f', composeFile, 'exec', '-T', 'postgres', 'psql', '--username', 'postgres', '--dbname', 'postgres',
    '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql,
  ])
  const value = stdout.trim()
  expect(value, 'the adventure map time cursor should be persisted').not.toBe('')
  return Number(value)
}

export async function readPersistedAdventureRuleSetId(adventureId: string) {
  const sql = `SELECT rule_set_id FROM adventure WHERE adventure_id = '${adventureId}'::uuid`
  const { stdout } = await dockerExec([
    'compose', '-p', composeProjectName(), '-f', composeFile, 'exec', '-T', 'postgres', 'psql', '--username', 'postgres', '--dbname', 'postgres',
    '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql,
  ])
  const value = stdout.trim()
  expect(value, 'the started adventure should have a runtime rule-set id').toMatch(/^[0-9a-f-]{36}$/i)
  return value
}

function dockerExec(args: string[]) {
  return execFileAsync('docker', args, { timeout: 30_000 })
}

function composeProjectName() {
  return process.env.COMPOSE_PROJECT_NAME
    ?? `dnd-master-${createHash('sha256').update(resolve(root, 'src')).digest('hex').slice(0, 8)}`
}

export async function readPersistedLongTermFacts(adventureId: string) {
  const sql = `SELECT fact_id || '|' || established_turn_id || '|' || fact_kind || '|' || player_visible || '|' || source_adventure_version || '|' || fact_version
    FROM adventure_long_term_fact WHERE adventure_id='${adventureId}'::uuid ORDER BY fact_version, fact_id`
  const { stdout } = await dockerExec(['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  return stdout.split('\n').map(row => row.trim()).filter(Boolean).map(row => {
    const [factId, establishedTurnId, kind, playerVisible, sourceAdventureVersion, version] = row.split('|')
    return { factId, establishedTurnId, kind, playerVisible: ['t', 'true'].includes(playerVisible.toLowerCase()), sourceAdventureVersion: Number(sourceAdventureVersion), version: Number(version) }
  })
}

export async function readPersistedConversationSummaries(adventureId: string) {
  const sql = `SELECT summary_version || '|' || source_start || '|' || source_end || '|' || source_adventure_version || '|' || length(summary_text)
    FROM adventure_conversation_summary WHERE adventure_id='${adventureId}'::uuid ORDER BY summary_version`
  const { stdout } = await dockerExec(['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  return stdout.split('\n').map(row => row.trim()).filter(Boolean).map(row => {
    const [version, sourceStart, sourceEnd, sourceAdventureVersion, textLength] = row.split('|').map(Number)
    return { version, sourceStart, sourceEnd, sourceAdventureVersion, textLength }
  })
}

export async function readPersistedCompactionJobs(adventureId: string) {
  const sql = `SELECT source_start || '|' || source_end || '|' || status || '|' || COALESCE(last_error, '')
    FROM adventure_conversation_compaction_job WHERE adventure_id='${adventureId}'::uuid ORDER BY source_end`
  const { stdout } = await dockerExec(['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  return stdout.split('\n').map(row => row.trim()).filter(Boolean).map(row => {
    const [sourceStart, sourceEnd, status, error] = row.split('|')
    return { sourceStart: Number(sourceStart), sourceEnd: Number(sourceEnd), status, error }
  })
}

export async function readPersistedRuntimeTurns(adventureId: string) {
  const sql = `SELECT runtime_turn_json FROM adventure_runtime_turn
    WHERE adventure_id='${adventureId}'::uuid ORDER BY created_at`
  const { stdout } = await dockerExec(['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  return stdout.split('\n').map(row => row.trim()).filter(Boolean).map(row => JSON.parse(row) as unknown)
}

export async function readPersistedAdventureState(adventureId: string) {
  const sql = `SELECT current_scene || '|' || version FROM adventure WHERE adventure_id='${adventureId}'::uuid`
  const { stdout } = await dockerExec(['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  const [currentScene, version] = stdout.trim().split('|')
  return { currentScene, version: Number(version) }
}

async function login(request: DirectApi): Promise<Auth> {
  const response = await request.post(`${backend}/api/v1/auth/login`, { data: { username: email, password } })
  expect(response.ok(), await response.text()).toBeTruthy()
  const result = await response.json() as { token?: string, playerId?: string }
  expect(result.token).toBeTruthy(); expect(result.playerId).toBeTruthy()
  return { ownerPlayerId: result.playerId!, headers: { Authorization: `Bearer ${result.token}` } }
}

async function createPreparedPackage(request: DirectApi, auth: Auth, sources: ReadonlyArray<{ path: string, role: string }>): Promise<PreparedPackage> {
  const catalog = await request.get(`${backend}/api/v1/rulebook-catalog`)
  expect(catalog.ok(), await catalog.text()).toBeTruthy()
  const expectedRulebookHash = createHash('sha256').update(await readFile(explorationGroupRulebookPath)).digest('hex')
  const rulebookEntries = (await catalog.json() as Array<{ catalogRevisionId: string, edition: string, rulebookId: string | null, status: string }>)
    .filter(entry => entry.edition === 'DND_5E_2014' && entry.status === 'READY' && entry.rulebookId)
  let rulebook: typeof rulebookEntries[number] | undefined
  for (const entry of rulebookEntries) {
    const source = await fetch(`${backend}/api/v1/backoffice/rulebook-catalog/${entry.catalogRevisionId}/source`, {
      headers: auth.headers, signal: AbortSignal.timeout(30_000),
    })
    if (!source.ok) continue
    const sourceHash = createHash('sha256').update(Buffer.from(await source.arrayBuffer())).digest('hex')
    if (sourceHash === expectedRulebookHash) { rulebook = entry; break }
  }
  expect(rulebook, 'a READY catalog entry must use the shared D&D Basic Rules November 2018 PDF').toBeTruthy()
  const documents: Array<{ knowledgeDocumentId: string, role: string }> = [{ knowledgeDocumentId: rulebook!.rulebookId!, role: 'RULEBOOK' }]
  for (const source of sources) {
    const contents = await readFile(source.path)
    const multipart = new FormData()
    multipart.append('documents', new Blob([JSON.stringify([{ idempotencyKey: crypto.randomUUID(), documentType: 'STORYBOOK', originalFilename: basename(source.path) }])], { type: 'application/json' }), 'documents.json')
    multipart.append('files', new Blob([contents], { type: 'application/pdf' }), basename(source.path))
    const upload = await request.post(`${backend}/api/v1/rulebooks?ownerPlayerId=${auth.ownerPlayerId}`, { headers: auth.headers, multipart })
    expect(upload.ok(), await upload.text()).toBeTruthy()
    const body = await upload.json() as { documents: Array<{ knowledgeDocumentId: string }> }
    documents.push({ knowledgeDocumentId: body.documents[0].knowledgeDocumentId, role: source.role })
  }
  await waitForDocuments(request, auth, documents.filter(document => document.role !== 'RULEBOOK').map(document => document.knowledgeDocumentId))
  const bundleResponse = await request.post(`${backend}/api/v1/adventures/scenario-bundles`, { headers: auth.headers, data: { playerId: auth.ownerPlayerId, documents } })
  expect(bundleResponse.ok(), await bundleResponse.text()).toBeTruthy()
  const bundle = await bundleResponse.json() as { bundleId: string }
  const primary = documents.find(document => document.role === 'MAIN_SCENARIO')!
  const compile = await request.post(`${backend}/api/v1/adventures/scenario-bundles/${bundle.bundleId}/compilation-jobs`, { headers: auth.headers, data: { playerId: auth.ownerPlayerId, inputFingerprint: crypto.randomUUID(), primaryStorybookId: primary.knowledgeDocumentId } })
  expect(compile.ok(), await compile.text()).toBeTruthy()
  const job = await compile.json() as { compilationId: string }
  const packageId = await waitForCompilation(request, auth, job.compilationId)
  const preparation = await waitForPreparation(request, auth, packageId)
  expect(preparation.spellDefinitions, 'the group-flow scenario must not declare package-wide spell/action definitions').toEqual([])
  if (preparation.characterCreationBlueprint.status !== 'PUBLISHED') {
    const publish = await request.post(`${backend}/api/v1/scenario-packages/${packageId}/character-blueprint/publish`, { headers: auth.headers })
    expect(publish.ok(), await publish.text()).toBeTruthy()
  }
  const published = await waitForPreparation(request, auth, packageId)
  const mapDocumentId = documents.find(document => document.role === 'MAP')?.knowledgeDocumentId
  expect(mapDocumentId, 'the E2E package must retain the uploaded map document ID').toBeTruthy()
  return { packageId, blueprintRevision: published.characterCreationBlueprint.revision!,
    rulebookId: rulebook!.rulebookId!, mapDocumentId: mapDocumentId! }
}

async function createSession(request: DirectApi, auth: Auth, prepared: PreparedPackage, partyMemberCount: number) {
  const preparationResponse = await request.post(`${backend}/api/v1/scenario-packages/${prepared.packageId}/play-preparations`, {
    headers: { ...auth.headers, 'Idempotency-Key': crypto.randomUUID() },
  })
  expect(preparationResponse.ok(), await preparationResponse.text()).toBeTruthy()
  const preparation = await preparationResponse.json() as { preparationId: string, spellDefinitions: unknown[] }
  expect(preparation.spellDefinitions, 'the group-flow preparation record must contain no unrelated package definitions').toEqual([])
  const response = await request.post(`${backend}/api/v1/adventure-sessions`, { headers: auth.headers,
    data: sessionCreationPayload(prepared.packageId, prepared.blueprintRevision, partyMemberCount, preparation.preparationId) })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{ sessionId: string, version: number }>
}

async function createCharacter(request: DirectApi, auth: Auth, sessionId: string, learnedAbilityIds: readonly string[] = []) {
  const equippedItems = { armor: '', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: null }
  const response = await request.post(`${backend}/internal/v1/adventure-sessions/${sessionId}/character-sheets`, { headers: auth.headers, data: {
    ownerPlayerId: auth.ownerPlayerId, edition: 'DND_5E_2014', characterName: `기록 검증 파이터 ${Date.now()}`, level: 1, inspiration: false, race: '인간', characterClass: '파이터', background: '현자',
    startingAbilities: 'strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8',
    derivedStatistics: JSON.stringify({ armorClass: 999, hitPointMaximum: 999 }),
    characterBuild: JSON.stringify({ schemaVersion: 2, rulesetRevision: 1, subrace: '', subclass: '', skillProficiencies: ['운동', '지각'], expertise: [], equipmentSelections: { weapon: 'default' }, ruleChoices: {}, ownedEquipment: [], ownedWeaponIds: [], equippedItems, cantrips: [], learnedOrPreparedSpells: [] }),
    characterState: JSON.stringify({ currentHitPoints: 1, temporaryHitPoints: 0, experience: 0, equippedItems, ammunition: {}, spellSlots: [], learnedAbilityIds }), blueprintValues: {},
  } })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { characterSheetId: string }).characterSheetId
}

async function waitForDocuments(request: DirectApi, auth: Auth, ids: string[]) {
  await waitUntil('storybook_extraction', 10 * 60_000, 2_000, async () => {
    const response = await request.get(`${backend}/internal/v1/rulebooks?ownerId=${auth.ownerPlayerId}`, { headers: auth.headers })
    expect(response.ok(), await response.text()).toBeTruthy()
    const items = (await response.json() as { rulebooks: Array<{ knowledgeDocumentId: string, status: string, failureReason?: string }> }).rulebooks.filter(item => ids.includes(item.knowledgeDocumentId))
    const failed = items.find(item => ['FAILED', 'REJECTED', 'NEEDS_INPUT', 'PARTIAL_AWAITING_CONFIRMATION'].includes(item.status))
    const statuses = items.map(item => item.status).join(',') || 'missing'
    if (failed) return { status: statuses, failed: true, failureReasonPresent: Boolean(failed.failureReason) }
    return { status: statuses, ready: items.length === ids.length && items.every(item => isStorybookReady(item.status)), value: true }
  })
}

async function waitForCompilation(request: DirectApi, auth: Auth, compilationId: string) {
  return waitUntil('scenario_compilation', 10 * 60_000, 5_000, async () => {
    const response = await request.get(`${backend}/api/v1/adventures/compilations/${compilationId}`, { headers: auth.headers })
    expect(response.ok(), await response.text()).toBeTruthy()
    const value = await response.json() as { status: string, packageId?: string, failureReason?: string }
    if (value.status === 'COMPLETED' || value.status === 'PUBLISHED') {
      expect(value.packageId).toBeTruthy()
      return { status: value.status, ready: true, value: value.packageId! }
    }
    return { status: value.status, failed: !['QUEUED', 'PROCESSING', 'REQUESTED', 'RUNNING', 'WAITING_RETRY'].includes(value.status), failureReasonPresent: Boolean(value.failureReason) }
  })
}

async function waitForPreparation(request: DirectApi, auth: Auth, packageId: string) {
  return waitUntil('play_preparation', 10 * 60_000, 2_000, async () => {
    const response = await request.get(`${backend}/api/v1/scenario-packages/${packageId}/play-preparation`, { headers: auth.headers })
    expect(response.ok(), await response.text()).toBeTruthy()
    const value = await response.json() as { status: string, failureReason?: string, spellDefinitions: unknown[],
      characterCreationBlueprint: { available: boolean, status: string, revision?: number } }
    const status = `${value.status}/${value.characterCreationBlueprint.status}`
    if (value.status === 'READY' && value.characterCreationBlueprint.available) return { status, ready: true, value }
    return { status, failed: ['FAILED', 'REJECTED', 'NEEDS_INPUT'].includes(value.status)
      || ['FAILED', 'REJECTED', 'NEEDS_INPUT'].includes(value.characterCreationBlueprint.status),
    failureReasonPresent: Boolean(value.failureReason) }
  })
}

async function waitUntil<T>(stage: string, timeoutMs: number, intervalMs: number,
  poll: () => Promise<{ status: string, ready?: boolean, failed?: boolean, failureReasonPresent?: boolean, value?: T }>): Promise<T> {
  const startedAt = Date.now()
  let pollCount = 0
  let lastStatus = 'not_checked'
  let nextProgressAt = 30_000
  console.info('[367-wait]', JSON.stringify({ stage, outcome: 'started', timeoutMs, intervalMs }))
  for (;;) {
    const state = await poll()
    pollCount++
    lastStatus = state.status
    const elapsedMs = Date.now() - startedAt
    if (state.ready) {
      console.info('[367-wait]', JSON.stringify({ stage, outcome: 'completed', elapsedMs, pollCount, finalStatus: lastStatus }))
      return state.value as T
    }
    if (state.failed) {
      console.info('[367-wait]', JSON.stringify({ stage, outcome: 'failed', elapsedMs, pollCount, finalStatus: lastStatus,
        failureReasonPresent: state.failureReasonPresent ?? false, nextAction: 'stop_and_report_status' }))
      throw new Error(`${stage} entered terminal status=${lastStatus}`)
    }
    if (elapsedMs >= timeoutMs) {
      console.info('[367-wait]', JSON.stringify({ stage, outcome: 'timeout', elapsedMs, timeoutMs, pollCount,
        lastStatus, nextAction: 'stop_and_report_timeout' }))
      throw new Error(`${stage} exceeded max wait ${timeoutMs}ms; lastStatus=${lastStatus}`)
    }
    if (elapsedMs >= nextProgressAt) {
      console.info('[367-wait]', JSON.stringify({ stage, outcome: 'waiting', elapsedMs, timeoutMs, pollCount, lastStatus }))
      nextProgressAt += 30_000
    }
    await delay(Math.min(intervalMs, timeoutMs - elapsedMs))
  }
}

function delay(milliseconds: number) { return new Promise(resolve => setTimeout(resolve, milliseconds)) }
