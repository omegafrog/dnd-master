import { expect, type APIRequestContext } from '@playwright/test'
import { basename } from 'node:path'
import { readFile } from 'node:fs/promises'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const storybooks = JSON.parse(process.env.BACKEND_E2E_STORYBOOKS_JSON ?? '[]') as Array<{ path: string, role: string }>
const root = new URL('../../../..', import.meta.url).pathname
const composeFile = `${root}src/infra/compose.yaml`

type Auth = { ownerPlayerId: string, headers: Record<string, string> }
type PreparedPackage = { packageId: string, blueprintRevision: number }

export function hasRuntimeEnvironment() {
  return Boolean(backend && email && password && storybooks.length)
}

export function assertPotentBrewStorybooks(sources = storybooks) {
  const requiredRoles = ['MAIN_SCENARIO', 'MAP', 'HANDOUT']
  const assetRoot = '/home/jiwoo/workspace/dnd-master/docs/assets/'
  const roles = new Set(sources.map(storybook => storybook.role))
  const missingRoles = requiredRoles.filter(role => !roles.has(role))
  const invalidPath = sources.find(storybook => !storybook.path.startsWith(assetRoot))
  if (invalidPath) throw new Error(`storybook ${invalidPath.role} must use a Linux docs/assets path`)
  if (missingRoles.length) throw new Error(`BACKEND_E2E_STORYBOOKS_JSON is missing required roles: ${missingRoles.join(', ')}`)
}

export async function bootstrapStartedAdventure(request: APIRequestContext) {
  const auth = await login(request)
  const prepared = await createPreparedPackage(request, auth)
  const session = await createSession(request, auth, prepared)
  const sheetId = await createCharacter(request, auth, session.sessionId)
  const party = await request.post(`${backend}/api/v1/adventure-sessions/${session.sessionId}/party`, {
    headers: { ...auth.headers, 'If-Match-Version': String(session.version) },
    data: { characterSheetId: sheetId, controlMode: 'DIRECT', nameMutableAfterStart: false, raceMutableAfterStart: false,
      characterClassMutableAfterStart: false, backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false },
  })
  expect(party.ok(), await party.text()).toBeTruthy()
  const ready = await party.json() as { version: number }
  const adventureId = crypto.randomUUID()
  const started = await request.post(`${backend}/api/v1/adventure-sessions/${session.sessionId}/start`, {
    headers: { ...auth.headers, 'If-Match-Version': String(ready.version), 'Idempotency-Key': crypto.randomUUID() },
    data: { adventureId, prepareMapOnly: false },
  })
  expect(started.ok(), await started.text()).toBeTruthy()
  const view = await started.json() as { adventureId: string, status: string }
  expect(view.status).toBe('STARTED')
  return { adventureId: view.adventureId, sessionId: session.sessionId }
}

export async function readPersistedLongTermFacts(adventureId: string) {
  const sql = `SELECT fact_kind || '|' || relevance || '|' || player_visible || '|' || source_adventure_version
    FROM adventure_long_term_fact WHERE adventure_id='${adventureId}'::uuid ORDER BY fact_version, fact_id`
  const { stdout } = await execFileAsync('docker', ['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  return stdout.split('\n').map(row => row.trim()).filter(Boolean).map(row => {
    const [kind, relevance, playerVisible, sourceAdventureVersion] = row.split('|')
    return { kind, relevance, playerVisible: playerVisible === 't', sourceAdventureVersion: Number(sourceAdventureVersion) }
  })
}

export async function readPersistedCompactionJobs(adventureId: string) {
  const sql = `SELECT source_end || '|' || status || '|' || COALESCE(last_error, '')
    FROM adventure_conversation_compaction_job WHERE adventure_id='${adventureId}'::uuid ORDER BY source_end`
  const { stdout } = await execFileAsync('docker', ['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  return stdout.split('\n').map(row => row.trim()).filter(Boolean).map(row => {
    const [sourceEnd, status, error] = row.split('|')
    return { sourceEnd: Number(sourceEnd), status, error }
  })
}

export async function readPersistedAdventureState(adventureId: string) {
  const sql = `SELECT current_scene || '|' || version FROM adventure WHERE adventure_id='${adventureId}'::uuid`
  const { stdout } = await execFileAsync('docker', ['compose', '-f', composeFile, 'exec', '-T', 'postgres', 'psql',
    '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql])
  const [currentScene, version] = stdout.trim().split('|')
  return { currentScene, version: Number(version) }
}

async function login(request: APIRequestContext): Promise<Auth> {
  const response = await request.post(`${backend}/api/v1/auth/login`, { data: { username: email, password } })
  expect(response.ok(), await response.text()).toBeTruthy()
  const result = await response.json() as { token?: string, playerId?: string }
  expect(result.token).toBeTruthy(); expect(result.playerId).toBeTruthy()
  return { ownerPlayerId: result.playerId!, headers: { Authorization: `Bearer ${result.token}` } }
}

async function createPreparedPackage(request: APIRequestContext, auth: Auth): Promise<PreparedPackage> {
  const catalog = await request.get(`${backend}/api/v1/rulebook-catalog`)
  expect(catalog.ok(), await catalog.text()).toBeTruthy()
  const rulebook = (await catalog.json() as Array<{ edition: string, rulebookId: string | null, status: string }>)
    .find(entry => entry.edition === 'DND_5E_2014' && entry.status === 'READY' && entry.rulebookId)
  expect(rulebook).toBeTruthy()
  const documents: Array<{ knowledgeDocumentId: string, role: string }> = [{ knowledgeDocumentId: rulebook!.rulebookId!, role: 'RULEBOOK' }]
  for (const source of storybooks) {
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
  if (preparation.characterCreationBlueprint.status !== 'PUBLISHED') {
    const publish = await request.post(`${backend}/api/v1/scenario-packages/${packageId}/character-blueprint/publish`, { headers: auth.headers })
    expect(publish.ok(), await publish.text()).toBeTruthy()
  }
  const published = await waitForPreparation(request, auth, packageId)
  return { packageId, blueprintRevision: published.characterCreationBlueprint.revision! }
}

async function createSession(request: APIRequestContext, auth: Auth, prepared: PreparedPackage) {
  const response = await request.post(`${backend}/api/v1/adventure-sessions`, { headers: auth.headers,
    data: { scenarioPackageId: prepared.packageId, blueprintId: prepared.packageId, blueprintRevision: prepared.blueprintRevision, partySize: 1 } })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{ sessionId: string, version: number }>
}

async function createCharacter(request: APIRequestContext, auth: Auth, sessionId: string) {
  const equippedItems = { armor: '', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: null }
  const response = await request.post(`${backend}/internal/v1/adventure-sessions/${sessionId}/character-sheets`, { headers: auth.headers, data: {
    ownerPlayerId: auth.ownerPlayerId, edition: 'DND_5E_2014', characterName: `기록 검증 파이터 ${Date.now()}`, level: 1, inspiration: false, race: '인간', characterClass: '파이터', background: '현자',
    startingAbilities: 'strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8',
    derivedStatistics: JSON.stringify({ armorClass: 999, hitPointMaximum: 999 }),
    characterBuild: JSON.stringify({ schemaVersion: 2, rulesetRevision: 1, subrace: '', subclass: '', skillProficiencies: ['운동', '지각'], expertise: [], equipmentSelections: { weapon: 'default' }, ruleChoices: {}, ownedEquipment: [], ownedWeaponIds: [], equippedItems, cantrips: [], learnedOrPreparedSpells: [] }),
    characterState: JSON.stringify({ currentHitPoints: 1, temporaryHitPoints: 0, experience: 0, equippedItems, ammunition: {}, spellSlots: [] }), blueprintValues: {},
  } })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json() as { characterSheetId: string }).characterSheetId
}

async function waitForDocuments(request: APIRequestContext, auth: Auth, ids: string[]) {
  for (;;) {
    const response = await request.get(`${backend}/internal/v1/rulebooks?ownerId=${auth.ownerPlayerId}`, { headers: auth.headers })
    expect(response.ok(), await response.text()).toBeTruthy()
    const items = (await response.json() as { rulebooks: Array<{ knowledgeDocumentId: string, status: string, failureReason?: string }> }).rulebooks.filter(item => ids.includes(item.knowledgeDocumentId))
    const failed = items.find(item => ['FAILED', 'REJECTED', 'NEEDS_INPUT', 'PARTIAL_AWAITING_CONFIRMATION'].includes(item.status))
    if (failed) throw new Error(`storybook ${failed.knowledgeDocumentId} stopped at ${failed.status}: ${failed.failureReason ?? ''}`)
    if (items.length === ids.length && items.every(item => ['EXTRACTED', 'INDEXED', 'PARTIAL_CONFIRMED'].includes(item.status))) return
    await delay(2_000)
  }
}

async function waitForCompilation(request: APIRequestContext, auth: Auth, compilationId: string) {
  for (;;) {
    const response = await request.get(`${backend}/api/v1/adventures/compilations/${compilationId}`, { headers: auth.headers })
    expect(response.ok(), await response.text()).toBeTruthy()
    const value = await response.json() as { status: string, packageId?: string, failureReason?: string }
    if (value.status === 'COMPLETED' || value.status === 'PUBLISHED') { expect(value.packageId).toBeTruthy(); return value.packageId! }
    if (!['QUEUED', 'PROCESSING', 'REQUESTED', 'RUNNING', 'WAITING_RETRY'].includes(value.status)) throw new Error(`compilation stopped at ${value.status}: ${value.failureReason ?? ''}`)
    await delay(5_000)
  }
}

async function waitForPreparation(request: APIRequestContext, auth: Auth, packageId: string) {
  for (;;) {
    const response = await request.get(`${backend}/api/v1/scenario-packages/${packageId}/play-preparation`, { headers: auth.headers })
    expect(response.ok(), await response.text()).toBeTruthy()
    const value = await response.json() as { status: string, characterCreationBlueprint: { available: boolean, status: string, revision?: number } }
    if (value.status === 'READY' && value.characterCreationBlueprint.available) return value
    await delay(2_000)
  }
}

function delay(milliseconds: number) { return new Promise(resolve => setTimeout(resolve, milliseconds)) }
