import { expect, test, type APIRequestContext } from '@playwright/test'
import { basename } from 'node:path'
import { readFile } from 'node:fs/promises'

const backend = process.env.BACKEND_E2E_URL
const ownerPlayerId = process.env.BACKEND_E2E_PLAYER_ID
const token = process.env.BACKEND_E2E_TOKEN ?? ''
const rulebookPath = process.env.BACKEND_E2E_RULEBOOK_FILE
const storybookPath = process.env.BACKEND_E2E_STORYBOOK_FILE

const authHeaders = { Authorization: `Bearer ${token}` }
const terminalDocumentStates = new Set(['EXTRACTED', 'INDEXED', 'PARTIAL_CONFIRMED'])
const failedDocumentStates = new Set(['FAILED', 'REJECTED', 'NEEDS_INPUT', 'PARTIAL_AWAITING_CONFIRMATION'])

function requireEnvironment() {
  test.skip(
    !backend || !ownerPlayerId || !rulebookPath || !storybookPath,
    'set BACKEND_E2E_URL, BACKEND_E2E_PLAYER_ID, BACKEND_E2E_RULEBOOK_FILE, and BACKEND_E2E_STORYBOOK_FILE',
  )
}

async function uploadDocuments(request: APIRequestContext) {
  const rulebook = await readFile(rulebookPath!)
  const storybook = await readFile(storybookPath!)
  const metadata = [
    { idempotencyKey: crypto.randomUUID(), documentType: 'RULEBOOK', originalFilename: basename(rulebookPath!) },
    { idempotencyKey: crypto.randomUUID(), documentType: 'STORYBOOK', originalFilename: basename(storybookPath!) },
  ]
  const response = await request.post(`${backend}/api/v1/rulebooks?ownerPlayerId=${ownerPlayerId}`, {
    headers: authHeaders,
    multipart: {
      documents: {
        name: 'documents.json',
        mimeType: 'application/json',
        buffer: Buffer.from(JSON.stringify(metadata)),
      },
      files: [
        { name: basename(rulebookPath!), mimeType: mimeType(rulebookPath!), buffer: rulebook },
        { name: basename(storybookPath!), mimeType: mimeType(storybookPath!), buffer: storybook },
      ],
    },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  const body = await response.json() as { documents: Array<{ knowledgeDocumentId: string | null; documentType: string; status: string; failureReason?: string }> }
  expect(body.documents).toHaveLength(2)
  for (const document of body.documents) {
    expect(document.knowledgeDocumentId, JSON.stringify(document)).toBeTruthy()
    expect(document.status, document.failureReason).toBe('ACCEPTED')
  }
  return body.documents.map(document => document.knowledgeDocumentId!)
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

async function createBundle(request: APIRequestContext, rulebookId: string, storybookId: string) {
  const response = await request.post(`${backend}/api/v1/adventures/scenario-bundles`, {
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
    data: {
      playerId: ownerPlayerId,
      documents: [
        { knowledgeDocumentId: rulebookId, role: 'RULEBOOK' },
        { knowledgeDocumentId: storybookId, role: 'MAIN_SCENARIO' },
      ],
    },
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
  const compilation = await start.json() as { compilationId: string; status: string; packageId?: string | null; failureReason?: string | null }
  let publishedPackageId = compilation.packageId ?? null
  await expect.poll(async () => {
    const response = await request.get(`${backend}/api/v1/adventures/compilations/${compilation.compilationId}`, { headers: authHeaders })
    expect(response.ok(), await response.text()).toBeTruthy()
    const current = await response.json() as { status: string; packageId?: string | null; failureReason?: string | null }
    if (current.status === 'FAILED') throw new Error(current.failureReason ?? 'scenario compilation failed')
    publishedPackageId = current.packageId ?? publishedPackageId
    return current.status
  }, { timeout: 180_000, intervals: [1000, 2000, 5000] }).toBe('PUBLISHED')
  expect(publishedPackageId).toBeTruthy()
  return publishedPackageId!
}

async function prepareBlueprint(request: APIRequestContext, packageId: string) {
  let preparation = await getPreparation(request, packageId)
  if (!preparation.characterCreationBlueprint.available) {
    const draft = await request.post(`${backend}/api/v1/scenario-packages/${packageId}/character-blueprint/draft`, { headers: authHeaders })
    expect(draft.ok(), await draft.text()).toBeTruthy()
    preparation = await getPreparation(request, packageId)
  }
  if (preparation.characterCreationBlueprint.status !== 'PUBLISHED') {
    const publish = await request.post(`${backend}/api/v1/scenario-packages/${packageId}/character-blueprint/publish`, { headers: authHeaders })
    expect(publish.ok(), await publish.text()).toBeTruthy()
    preparation = await getPreparation(request, packageId)
  }
  expect(preparation.status).toBe('READY')
  expect(preparation.characterCreationBlueprint.status).toBe('PUBLISHED')
  return preparation
}

async function getPreparation(request: APIRequestContext, packageId: string) {
  const response = await request.get(`${backend}/api/v1/scenario-packages/${packageId}/play-preparation`, { headers: authHeaders })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{
    status: string
    characterCreationBlueprint: { available: boolean; revision?: number; status?: string }
  }>
}

async function createSession(request: APIRequestContext, packageId: string, blueprintRevision: number) {
  const response = await request.post(`${backend}/api/v1/adventure-sessions`, {
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
    data: {
      scenarioPackageId: packageId,
      blueprintId: packageId,
      blueprintRevision,
    },
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

  const [rulebookId, storybookId] = await uploadDocuments(request)
  await waitForDocuments(request, [rulebookId, storybookId])
  const bundle = await createBundle(request, rulebookId, storybookId)
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
})
