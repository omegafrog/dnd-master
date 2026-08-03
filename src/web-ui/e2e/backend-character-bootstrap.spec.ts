import { expect, test, type APIRequestContext } from '@playwright/test'
import { basename } from 'node:path'
import { readFile } from 'node:fs/promises'

const storybookRoles = new Set([
  'MAIN_SCENARIO',
  'MAP',
  'HANDOUT',
  'APPENDIX',
  'REFERENCE',
  'CHARACTER_SHEET',
  'UNDETERMINED',
])

const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const rulebookPath = process.env.BACKEND_E2E_RULEBOOK_FILE
const storybooks = parseStorybooks(process.env.BACKEND_E2E_STORYBOOKS_JSON ?? '')

let ownerPlayerId = ''
let authHeaders: Record<string, string> = {}

const terminalDocumentStates = new Set(['EXTRACTED', 'INDEXED', 'PARTIAL_CONFIRMED'])
const failedDocumentStates = new Set(['FAILED', 'REJECTED', 'NEEDS_INPUT', 'PARTIAL_AWAITING_CONFIRMATION'])

type StorybookInput = {
  path: string
  role: string
}

function parseStorybooks(value: string): StorybookInput[] {
  if (!value.trim()) return []
  let parsed: unknown
  try {
    parsed = JSON.parse(value)
  } catch (error) {
    throw new Error(`BACKEND_E2E_STORYBOOKS_JSON must be valid JSON: ${String(error)}`)
  }
  if (!Array.isArray(parsed)) {
    throw new Error('BACKEND_E2E_STORYBOOKS_JSON must be a JSON array')
  }
  return parsed.map((entry, index) => {
    if (!entry || typeof entry !== 'object') {
      throw new Error(`storybook entry ${index} must be an object`)
    }
    const path = 'path' in entry ? entry.path : undefined
    const role = 'role' in entry ? entry.role : undefined
    if (typeof path !== 'string' || !path.trim()) {
      throw new Error(`storybook entry ${index} must have a non-empty path`)
    }
    if (typeof role !== 'string' || !storybookRoles.has(role)) {
      throw new Error(`storybook entry ${index} has unsupported role: ${String(role)}`)
    }
    return { path: path.trim(), role }
  })
}

function requireEnvironment() {
  const missing = [
    ['BACKEND_E2E_URL', backend],
    ['BACKEND_E2E_EMAIL', email],
    ['BACKEND_E2E_PASSWORD', password],
    ['BACKEND_E2E_RULEBOOK_FILE', rulebookPath],
    ['BACKEND_E2E_STORYBOOKS_JSON', storybooks.length > 0 ? 'configured' : ''],
  ]
    .filter(([, value]) => !value)
    .map(([name]) => name)

  if (missing.length > 0) {
    throw new Error(`missing required E2E configuration: ${missing.join(', ')}`)
  }
}

async function login(request: APIRequestContext) {
  const response = await request.post(`${backend}/api/v1/auth/login`, {
    data: { username: email, password },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  const session = await response.json() as { token?: string; playerId?: string }
  expect(session.token, 'login response did not include token').toBeTruthy()
  expect(session.playerId, 'login response did not include playerId').toBeTruthy()
  ownerPlayerId = session.playerId!
  authHeaders = { Authorization: `Bearer ${session.token}` }
}

async function uploadDocuments(request: APIRequestContext) {
  const inputs = [
    { path: rulebookPath!, documentType: 'RULEBOOK', role: 'RULEBOOK' },
    ...storybooks.map(storybook => ({ ...storybook, documentType: 'STORYBOOK' })),
  ]
  const buffers = await Promise.all(inputs.map(input => readFile(input.path)))
  const metadata = inputs.map(input => ({
    idempotencyKey: crypto.randomUUID(),
    documentType: input.documentType,
    originalFilename: basename(input.path),
  }))

  const multipart = new FormData()
  multipart.append(
    'documents',
    new Blob([JSON.stringify(metadata)], { type: 'application/json' }),
    'documents.json',
  )
  inputs.forEach((input, index) => {
    multipart.append(
      'files',
      new Blob([buffers[index]], { type: mimeType(input.path) }),
      basename(input.path),
    )
  })

  const response = await request.post(`${backend}/api/v1/rulebooks?ownerPlayerId=${ownerPlayerId}`, {
    headers: authHeaders,
    multipart,
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  const body = await response.json() as {
    documents: Array<{
      knowledgeDocumentId: string | null
      documentType: string
      status: string
      failureReason?: string
    }>
  }
  expect(body.documents).toHaveLength(inputs.length)
  body.documents.forEach(document => {
    expect(document.knowledgeDocumentId, JSON.stringify(document)).toBeTruthy()
    expect(document.status, document.failureReason).toBe('ACCEPTED')
  })

  return body.documents.map((document, index) => ({
    knowledgeDocumentId: document.knowledgeDocumentId!,
    role: inputs[index].role,
  }))
}

async function waitForDocuments(request: APIRequestContext, ids: string[]) {
  await expect.poll(async () => {
    const response = await request.get(`${backend}/internal/v1/rulebooks?ownerId=${ownerPlayerId}`, { headers: authHeaders })
    expect(response.ok(), await response.text()).toBeTruthy()
    const body = await response.json() as { rulebooks: Array<{ knowledgeDocumentId: string; status: string; failureReason?: string }> }
    const documents = body.rulebooks.filter(document => ids.includes(document.knowledgeDocumentId))
    const failed = documents.find(document => failedDocumentStates.has(document.status))
    if (failed) throw new Error(`document ${failed.knowledgeDocumentId} stopped at ${failed.status}: ${failed.failureReason ?? ''}`)
    return documents.length === ids.length && documents.every(document => terminalDocumentStates.has(document.status))
  }, { timeout: 120_000, intervals: [500, 1000, 2000, 5000] }).toBe(true)
}

async function createBundle(
  request: APIRequestContext,
  documents: Array<{ knowledgeDocumentId: string; role: string }>,
) {
  const response = await request.post(`${backend}/api/v1/adventures/scenario-bundles`, {
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
    data: { playerId: ownerPlayerId, documents },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{ bundleId: string; currentRevision: number }>
}

async function compilePackage(request: APIRequestContext, bundleId: string) {
  const start = await request.post(`${backend}/api/v1/adventures/scenario-bundles/${bundleId}/compilation-jobs`, {
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
    data: { playerId: ownerPlayerId, inputFingerprint: `playwright-${Date.now()}` },
  })
  expect(start.ok(), await start.text()).toBeTruthy()
  const compilation = await start.json() as { compilationId: string; packageId?: string | null }
  let packageId = compilation.packageId ?? null
  await expect.poll(async () => {
    const response = await request.get(`${backend}/api/v1/adventures/compilations/${compilation.compilationId}`, { headers: authHeaders })
    expect(response.ok(), await response.text()).toBeTruthy()
    const current = await response.json() as { status: string; packageId?: string | null; failureReason?: string | null }
    if (current.status === 'FAILED') throw new Error(current.failureReason ?? 'scenario compilation failed')
    packageId = current.packageId ?? packageId
    return current.status
  }, { timeout: 180_000, intervals: [1000, 2000, 5000] }).toBe('PUBLISHED')
  expect(packageId).toBeTruthy()
  return packageId!
}

async function getPreparation(request: APIRequestContext, packageId: string) {
  const response = await request.get(`${backend}/api/v1/scenario-packages/${packageId}/play-preparation`, { headers: authHeaders })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{
    status: string
    characterCreationBlueprint: { available: boolean; revision?: number; status?: string }
  }> 
}

async function waitForPreparationReady(request: APIRequestContext, packageId: string) {
  let latest: Awaited<ReturnType<typeof getPreparation>> | undefined
  await expect.poll(async () => {
    latest = await getPreparation(request, packageId)
    return latest.status
  }, { timeout: 180_000, intervals: [1000, 2000, 5000] }).toBe('READY')
  return latest!
}

async function prepareBlueprint(request: APIRequestContext, packageId: string) {
  let preparation = await waitForPreparationReady(request, packageId)
  if (!preparation.characterCreationBlueprint.available) {
    const draft = await request.post(`${backend}/api/v1/scenario-packages/${packageId}/character-blueprint/draft`, { headers: authHeaders })
    expect(draft.ok(), await draft.text()).toBeTruthy()
    preparation = await waitForPreparationReady(request, packageId)
  }
  if (preparation.characterCreationBlueprint.status !== 'PUBLISHED') {
    const publish = await request.post(`${backend}/api/v1/scenario-packages/${packageId}/character-blueprint/publish`, { headers: authHeaders })
    expect(publish.ok(), await publish.text()).toBeTruthy()
    preparation = await waitForPreparationReady(request, packageId)
  }
  expect(preparation.status).toBe('READY')
  expect(preparation.characterCreationBlueprint.status).toBe('PUBLISHED')
  return preparation
}

async function createSession(request: APIRequestContext, packageId: string, blueprintRevision: number) {
  const response = await request.post(`${backend}/api/v1/adventure-sessions`, {
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
    data: { scenarioPackageId: packageId, blueprintId: packageId, blueprintRevision },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{ sessionId: string; version: number }>
}

function fighterDraft() {
  const equippedItems = { armor: '', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: null }
  return {
    ownerPlayerId,
    edition: 'DND_5E_2014',
    characterName: `Fresh DB 파이터 ${Date.now()}`,
    level: 1,
    inspiration: false,
    race: '인간',
    characterClass: '파이터',
    background: '현자',
    startingAbilities: 'strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8',
    derivedStatistics: JSON.stringify({ armorClass: 999, hitPointMaximum: 999 }),
    characterBuild: JSON.stringify({
      schemaVersion: 2,
      rulesetRevision: 1,
      subrace: '',
      subclass: '',
      skillProficiencies: ['운동', '지각'],
      expertise: [],
      equipmentSelections: { weapon: 'default' },
      ruleChoices: {},
      ownedEquipment: [],
      ownedWeaponIds: [],
      equippedItems,
      cantrips: [],
      learnedOrPreparedSpells: [],
    }),
    characterState: JSON.stringify({ currentHitPoints: 1, temporaryHitPoints: 0, experience: 0, equippedItems, ammunition: {}, spellSlots: [] }),
    blueprintValues: {},
  }
}

function companionDraft() {
  const draft = fighterDraft()
  return {
    ...draft,
    characterName: `AI 동료 ${Date.now()}`,
  }
}

function mimeType(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.pdf')) return 'application/pdf'
  if (lower.endsWith('.docx')) return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg'
  return 'text/plain'
}

test('fresh database bootstraps scenario package and completes character creation', async ({ request }) => {
  requireEnvironment()
  test.setTimeout(360_000)

  await login(request)
  const documents = await uploadDocuments(request)
  await waitForDocuments(request, documents.map(document => document.knowledgeDocumentId))
  const bundle = await createBundle(request, documents)
  const packageId = await compilePackage(request, bundle.bundleId)
  const preparation = await prepareBlueprint(request, packageId)
  const session = await createSession(request, packageId, preparation.characterCreationBlueprint.revision ?? 0)

  const evaluationResponse = await request.post(`${backend}/internal/v1/adventure-sessions/${session.sessionId}/character-builds/evaluate`, {
    headers: authHeaders,
    data: fighterDraft(),
  })
  expect(evaluationResponse.ok(), await evaluationResponse.text()).toBeTruthy()
  const evaluation = await evaluationResponse.json()
  expect(evaluation.valid).toBe(true)
  expect(evaluation.derived.armorClass).not.toBe(999)

  const createResponse = await request.post(`${backend}/internal/v1/adventure-sessions/${session.sessionId}/character-sheets`, {
    headers: authHeaders,
    data: fighterDraft(),
  })
  expect(createResponse.ok(), await createResponse.text()).toBeTruthy()
  const created = await createResponse.json() as { characterSheetId: string }

  const partyResponse = await request.post(`${backend}/api/v1/adventure-sessions/${session.sessionId}/party`, {
    headers: { ...authHeaders, 'If-Match-Version': String(session.version) },
    data: {
      characterSheetId: created.characterSheetId,
      controlMode: 'DIRECT',
      nameMutableAfterStart: false,
      raceMutableAfterStart: false,
      characterClassMutableAfterStart: false,
      backgroundMutableAfterStart: false,
      startingAbilitiesMutableAfterStart: false,
      levelMutableAfterStart: false,
    },
  })
  expect(partyResponse.ok(), await partyResponse.text()).toBeTruthy()
  const updated = await partyResponse.json()
  expect(updated.party).toEqual(expect.arrayContaining([expect.objectContaining({ characterSheetId: created.characterSheetId })]))

  const companionResponse = await request.post(`${backend}/internal/v1/adventure-sessions/${session.sessionId}/character-sheets`, {
    headers: authHeaders,
    data: companionDraft(),
  })
  expect(companionResponse.ok(), await companionResponse.text()).toBeTruthy()
  const companion = await companionResponse.json() as { characterSheetId: string }

  const companionPartyResponse = await request.post(`${backend}/api/v1/adventure-sessions/${session.sessionId}/party`, {
    headers: { ...authHeaders, 'If-Match-Version': String(updated.version) },
    data: {
      characterSheetId: companion.characterSheetId,
      controlMode: 'AGENT',
      nameMutableAfterStart: false,
      raceMutableAfterStart: false,
      characterClassMutableAfterStart: false,
      backgroundMutableAfterStart: false,
      startingAbilitiesMutableAfterStart: false,
      levelMutableAfterStart: false,
    },
  })
  expect(companionPartyResponse.ok(), await companionPartyResponse.text()).toBeTruthy()
  const partyWithCompanion = await companionPartyResponse.json()
  expect(partyWithCompanion.party).toEqual(expect.arrayContaining([
    expect.objectContaining({ characterSheetId: created.characterSheetId, controlMode: 'DIRECT' }),
    expect.objectContaining({ characterSheetId: companion.characterSheetId, controlMode: 'AGENT' }),
  ]))
})
