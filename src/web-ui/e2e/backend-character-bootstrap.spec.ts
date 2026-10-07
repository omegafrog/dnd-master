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
const assetRoot = '/home/jiwoo/workspace/dnd-master/docs/assets/'
const catalogAdminToken = process.env.BACKEND_E2E_CATALOG_ADMIN_TOKEN
const catalogPdf = process.env.BACKEND_E2E_CATALOG_PDF ?? `${assetRoot}DnD_BasicRules_2018.pdf`
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
    if (typeof path !== 'string' || !path.startsWith(assetRoot)) {
      throw new Error(`storybook entry ${index} must use a Linux docs/assets path`)
    }
    if (typeof role !== 'string' || !storybookRoles.has(role)) {
      throw new Error(`storybook entry ${index} has unsupported role: ${String(role)}`)
    }
    return { path: path.trim(), role }
  })
}

function hasEnvironment() {
  const missing = [
    ['BACKEND_E2E_URL', backend],
    ['BACKEND_E2E_EMAIL', email],
    ['BACKEND_E2E_PASSWORD', password],
    ['BACKEND_E2E_STORYBOOKS_JSON', storybooks.length > 0 ? 'configured' : ''],
  ]
    .filter(([, value]) => !value)
    .map(([name]) => name)

  return missing.length === 0
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
  await publishCatalogRevisionWhenConfigured(request)
  // 룰북은 사용자 업로드가 아니라 공유 목록에서 먼저 선택한다.
  const catalogResponse = await request.get(`${backend}/api/v1/rulebook-catalog`)
  expect(catalogResponse.ok(), await catalogResponse.text()).toBeTruthy()
  const catalog = await catalogResponse.json() as Array<{
    edition: string
    rulebookId: string | null
    status: string
  }>
  const rulebook = catalog.find(item => item.edition === 'DND_5E_2014' && item.status === 'READY' && item.rulebookId)
  expect(rulebook, 'published DND_5E_2014 catalog rulebook is required').toBeTruthy()

  const inputs = [
    ...storybooks.map(storybook => ({ ...storybook, documentType: 'STORYBOOK' })),
  ]
  const uploaded: Array<{ knowledgeDocumentId: string; role: string }> = []
  for (const input of inputs) {
    const buffer = await readFile(input.path)
    const metadata = [{
      idempotencyKey: crypto.randomUUID(),
      documentType: input.documentType,
      originalFilename: basename(input.path),
    }]
    const multipart = new FormData()
    multipart.append('documents', new Blob([JSON.stringify(metadata)], { type: 'application/json' }), 'documents.json')
    multipart.append('files', new Blob([buffer], { type: mimeType(input.path) }), basename(input.path))
    const response = await request.post(`${backend}/api/v1/rulebooks?ownerPlayerId=${ownerPlayerId}`, {
      headers: authHeaders,
      multipart,
    })
    expect(response.ok(), await response.text()).toBeTruthy()
    const body = await response.json() as { documents: Array<{ knowledgeDocumentId: string | null; status: string; failureReason?: string }> }
    expect(body.documents).toHaveLength(1)
    const document = body.documents[0]
    expect(document.knowledgeDocumentId, JSON.stringify(document)).toBeTruthy()
    expect(document.status, document.failureReason).toBe('ACCEPTED')
    uploaded.push({ knowledgeDocumentId: document.knowledgeDocumentId!, role: input.role })
  }
  const primary = uploaded.find(document => document.role === 'MAIN_SCENARIO')
  expect(primary, 'MAIN_SCENARIO storybook is required').toBeTruthy()
  return {
    documents: [{ knowledgeDocumentId: rulebook!.rulebookId!, role: 'RULEBOOK' }, ...uploaded],
    primaryStorybookId: primary!.knowledgeDocumentId,
  }
}

async function publishCatalogRevisionWhenConfigured(request: APIRequestContext) {
  // The normal local launcher seeds the shared catalog. An optional admin token
  // enables this same journey to prove upload -> publish without printing or
  // persisting the credential in the browser.
  if (!catalogAdminToken) return
  if (!catalogPdf.startsWith(assetRoot)) {
    throw new Error('BACKEND_E2E_CATALOG_PDF must use a Linux docs/assets path')
  }
  const buffer = await readFile(catalogPdf)
  const upload = await request.post(`${backend}/api/v1/backoffice/rulebook-catalog`, {
    headers: { Authorization: `Bearer ${catalogAdminToken}` },
    multipart: {
      edition: 'DND_5E_2014',
      file: { name: basename(catalogPdf), mimeType: 'application/pdf', buffer },
    },
  })
  expect(upload.ok(), await upload.text()).toBeTruthy()
  const revision = await upload.json() as { id?: string; catalogRevisionId?: string }
  const revisionId = revision.id ?? revision.catalogRevisionId
  expect(revisionId, 'catalog upload did not return a revision id').toBeTruthy()
  const publish = await request.post(`${backend}/api/v1/backoffice/rulebook-catalog/${revisionId}/publish`, {
    headers: { Authorization: `Bearer ${catalogAdminToken}` },
  })
  expect(publish.ok(), await publish.text()).toBeTruthy()
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

async function compilePackage(request: APIRequestContext, bundleId: string, primaryStorybookId: string) {
  expect(primaryStorybookId, 'MAIN_SCENARIO storybook is required').toBeTruthy()
  const start = await request.post(`${backend}/api/v1/adventures/scenario-bundles/${bundleId}/compilation-jobs`, {
    headers: { ...authHeaders, 'Content-Type': 'application/json' },
    data: {
      playerId: ownerPlayerId,
      inputFingerprint: `playwright-${Date.now()}`,
      primaryStorybookId,
    },
  })
  expect(start.ok(), await start.text()).toBeTruthy()
  const compilation = await start.json() as { compilationId: string; packageId?: string | null }
  let packageId = compilation.packageId ?? null
  const startedAt = Date.now()
  const observations: Array<{ observedAt: string; elapsedMs: number; status: string; attempt?: number; packageId: string | null }> = []
  const runningStatuses = new Set(['QUEUED', 'PROCESSING', 'REQUESTED', 'RUNNING', 'WAITING_RETRY'])

  try {
    for (;;) {
      const response = await request.get(`${backend}/api/v1/adventures/compilations/${compilation.compilationId}`, { headers: authHeaders })
      expect(response.ok(), await response.text()).toBeTruthy()
      const current = await response.json() as {
        status: string
        attempt?: number
        packageId?: string | null
        failureReason?: string | null
        diagnostics?: Array<{ code?: string; message?: string }>
      }
      packageId = current.packageId ?? packageId
      const last = observations.at(-1)
      if (!last || last.status !== current.status || last.attempt !== current.attempt || last.packageId !== packageId) {
        observations.push({
          observedAt: new Date().toISOString(),
          elapsedMs: Date.now() - startedAt,
          status: current.status,
          attempt: current.attempt,
          packageId,
        })
      }

      if (current.status === 'COMPLETED' || current.status === 'PUBLISHED') {
        expect(packageId, `scenario compilation ${compilation.compilationId} finished without a package id`).toBeTruthy()
        return packageId!
      }
      if (!runningStatuses.has(current.status)) {
        const detail = current.failureReason ?? current.diagnostics?.map(item => `${item.code ?? ''} ${item.message ?? ''}`).join('; ')
        throw new Error(detail || `scenario compilation ${compilation.compilationId} stopped at ${current.status}`)
      }
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
  } finally {
    await test.info().attach(`scenario-compilation-${compilation.compilationId}.json`, {
      body: Buffer.from(JSON.stringify({ compilationId: compilation.compilationId, bundleId, observations }, null, 2)),
      contentType: 'application/json',
    })
  }
}

async function getPreparation(request: APIRequestContext, packageId: string) {
  const response = await request.get(`${backend}/api/v1/scenario-packages/${packageId}/play-preparation`, { headers: authHeaders })
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{
    status: string
    characterCreationBlueprint: { available: boolean; revision?: number; status?: string }
    scenarioPackageId: string
    spellDefinitions: Array<{
      id: string
      name: string
      sourceDocumentId: string
      sourceLocator: string
      extractionVersion: number
      ownerPlanNumbers: number[]
      executable: boolean
      reviewStatus: string
    }>
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
  return response.json() as Promise<{ sessionId: string; version: number; characterLimit: number }>
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

test('fresh database bootstraps scenario package, preserves spell sources, and completes character creation', async ({ request, page }) => {
  test.skip(!hasEnvironment(),
    'set BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD and BACKEND_E2E_STORYBOOKS_JSON')
  test.setTimeout(0)

  await login(request)
  const uploaded = await uploadDocuments(request)
  await waitForDocuments(request, uploaded.documents
    .filter(document => document.role !== 'RULEBOOK')
    .map(document => document.knowledgeDocumentId))
  const bundle = await createBundle(request, uploaded.documents)
  const packageId = await compilePackage(request, bundle.bundleId, uploaded.primaryStorybookId)
  const preparation = await prepareBlueprint(request, packageId)

  const rulebookDocumentId = uploaded.documents.find(document => document.role === 'RULEBOOK')!.knowledgeDocumentId
  const spellPreparation = await getPreparation(request, packageId)
  expect(spellPreparation.scenarioPackageId).toBe(packageId)
  expect(spellPreparation.spellDefinitions).toHaveLength(126)
  expect(new Set(spellPreparation.spellDefinitions.map(spell => spell.id)).size).toBe(126)
  expect(spellPreparation.spellDefinitions).toEqual(expect.arrayContaining([
    expect.objectContaining({ sourceDocumentId: rulebookDocumentId }),
  ]))
  for (const spell of spellPreparation.spellDefinitions) {
    expect(spell.sourceDocumentId).toBe(rulebookDocumentId)
    expect(spell.sourceLocator).toMatch(/^page=\d+;node=.+$/)
    expect(spell.extractionVersion).toBeGreaterThan(0)
    expect(spell.ownerPlanNumbers.length).toBeGreaterThan(0)
    expect(spell.executable).toBe(false)
    expect(spell.reviewStatus).toBe('PENDING')
  }

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.goto(`/#/scenario-packages/${packageId}/character-blueprint`)
  await expect(page.getByRole('heading', { name: '캐릭터 생성 설정 검토' })).toBeVisible()
  const spellList = page.getByRole('region', { name: '기본 룰북 주문 목록' })
  await spellList.getByText('126개 주문과 출처 보기', { exact: true }).click()
  const spellRows = spellList.locator('ol > li')
  await expect(spellRows).toHaveCount(126)
  const firstSpell = spellPreparation.spellDefinitions[0]
  await expect(spellRows.first()).toContainText(firstSpell.name)
  await expect(spellRows.first()).toContainText(`원문 문서 ${rulebookDocumentId}, 위치 ${firstSpell.sourceLocator}, 추출 ${firstSpell.extractionVersion}`)

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

  let fullParty = partyWithCompanion
  for (let index = 2; index < session.characterLimit; index += 1) {
    const additionalCompanionResponse = await request.post(`${backend}/internal/v1/adventure-sessions/${session.sessionId}/character-sheets`, {
      headers: authHeaders,
      data: companionDraft(),
    })
    expect(additionalCompanionResponse.ok(), await additionalCompanionResponse.text()).toBeTruthy()
    const additionalCompanion = await additionalCompanionResponse.json() as { characterSheetId: string }
    const additionalPartyResponse = await request.post(`${backend}/api/v1/adventure-sessions/${session.sessionId}/party`, {
      headers: { ...authHeaders, 'If-Match-Version': String(fullParty.version) },
      data: {
        characterSheetId: additionalCompanion.characterSheetId,
        controlMode: 'AGENT',
        nameMutableAfterStart: false,
        raceMutableAfterStart: false,
        characterClassMutableAfterStart: false,
        backgroundMutableAfterStart: false,
        startingAbilitiesMutableAfterStart: false,
        levelMutableAfterStart: false,
      },
    })
    expect(additionalPartyResponse.ok(), await additionalPartyResponse.text()).toBeTruthy()
    fullParty = await additionalPartyResponse.json()
  }

  const startResponse = await request.post(`${backend}/api/v1/adventure-sessions/${session.sessionId}/start`, {
    headers: {
      ...authHeaders,
      'If-Match-Version': String(fullParty.version),
      'Idempotency-Key': crypto.randomUUID(),
      'Content-Type': 'application/json',
    },
    data: { adventureId: crypto.randomUUID(), prepareMapOnly: true },
  })
  expect(startResponse.ok(), await startResponse.text()).toBeTruthy()
  const started = await startResponse.json() as { status: string }
  expect(['STARTING', 'STARTED']).toContain(started.status)
})
