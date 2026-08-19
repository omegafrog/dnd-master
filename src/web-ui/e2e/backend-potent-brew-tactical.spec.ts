import { expect, test, type APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const backend = process.env.BACKEND_E2E_URL
const token = process.env.BACKEND_E2E_TOKEN
const sessionId = process.env.BACKEND_E2E_SESSION_ID
const packageId = process.env.BACKEND_E2E_SCENARIO_PACKAGE_ID
const bundleId = process.env.BACKEND_E2E_BUNDLE_ID
const playerId = process.env.BACKEND_E2E_PLAYER_ID
const stagePosition = Number(process.env.BACKEND_E2E_TACTICAL_STAGE_POSITION ?? '1')
const expectedPartySize = Number(process.env.BACKEND_E2E_PARTY_SIZE ?? '4')

const isPotentBrewStorybook = (document: { originalFilename?: string; documentType?: string }) =>
  document.documentType === 'STORYBOOK' && /potent[ _-]?brew/i.test(document.originalFilename ?? '')

const isDndRulebook = (document: { originalFilename?: string; documentType?: string }) =>
  document.documentType === 'RULEBOOK' && /dnd5th|dnd.*5e|d\s*&\s*d\s*5e|rulebook/i.test(document.originalFilename ?? '')

test('identifies Potent Brew storybook documents with underscore filenames', () => {
  const documents = [
    { documentType: 'STORYBOOK', originalFilename: '892902-A_Most_Potent_Brew.pdf' },
    { documentType: 'STORYBOOK', originalFilename: '892902-A_Most_Potent_Brew_Player_Handout.pdf' },
    { documentType: 'STORYBOOK', originalFilename: '892902-A_Most_Potent_Brew_GM_Handout.pdf' },
  ]

  expect(documents.filter(isPotentBrewStorybook)).toHaveLength(3)
})

test('identifies the D&D 5e rulebook filename used by the live bundle', () => {
  expect(isDndRulebook({ documentType: 'RULEBOOK', originalFilename: 'D&D 5e (2014)' })).toBeTruthy()
})

async function ensureCompleteParty(request: APIRequestContext) {
  let sessionResponse = await request.get(`${backend}/api/v1/adventure-sessions/${sessionId}`, { headers: { Authorization: `Bearer ${token}` } })
  expect(sessionResponse.ok()).toBeTruthy()
  let session = await sessionResponse.json()
  expect(session.characterLimit).toBe(expectedPartySize)
  expect(session.party.length).toBeLessThanOrEqual(expectedPartySize)

  while (session.party.length < expectedPartySize) {
    const candidateResponse = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/party/ai-candidates`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(candidateResponse.ok()).toBeTruthy()
    const candidate = await candidateResponse.json()
    const adopted = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/party/ai-candidates/adopt`, {
      headers: { Authorization: `Bearer ${token}`, 'If-Match-Version': String(session.version) },
      data: { ...candidate, controlMode: 'AGENT' },
    })
    expect(adopted.ok()).toBeTruthy()
    session = await adopted.json()
  }

  expect(session.party).toHaveLength(expectedPartySize)
  return session
}

test('real Potent Brew backend preserves tactical retry, activation, projection, trigger, and revision flow', async ({ request }) => {
  test.setTimeout(180_000)
  test.skip(!backend || !token || !sessionId || !packageId || !bundleId || !playerId,
    'set established BACKEND_E2E_URL, TOKEN, PLAYER_ID, BUNDLE_ID, SCENARIO_PACKAGE_ID and SESSION_ID')

  const headers = { Authorization: `Bearer ${token}` }
  let session = await ensureCompleteParty(request)
  const bundleResponse = await request.get(`${backend}/api/v1/adventures/scenario-bundles/${bundleId}`, { headers })
  expect(bundleResponse.ok()).toBeTruthy()
  const bundle = await bundleResponse.json()
  const potentBrew = bundle.documents.filter(isPotentBrewStorybook)
  expect(potentBrew).toHaveLength(3)
  expect(bundle.documents).toEqual(expect.arrayContaining([
    expect.objectContaining({ documentType: 'RULEBOOK', originalFilename: expect.stringMatching(/dnd5th|dnd.*5e|d\s*&\s*d\s*5e|rulebook/i) }),
  ]))
  const compiled = await request.get(`${backend}/api/v1/adventures/scenario-packages/${packageId}`, { headers })
  expect(compiled.ok()).toBeTruthy()
  const compiledBody = await compiled.json()
  expect(compiledBody.packageId).toBe(packageId)
  expect(compiledBody.bundleId).toBe(bundleId)
  expect(compiledBody.reportStatus).toBe('COMPLETE')
  expect(compiledBody.units.length).toBeGreaterThan(0)
  let plan = await request.get(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/gm`, { headers })
  if (!plan.ok()) {
    expect(plan.status()).toBe(409)
    expect((await plan.json()).error).toBe('ADVENTURE_START_BLOCKED')
    plan = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan`, {
      headers,
      data: { endingCount: 2, adventureLength: 'STANDARD' },
    })
  }
  expect(plan.ok()).toBeTruthy()
  const planBody = await plan.json()
  expect(planBody.status).toBe('READY')
  const initialStage = planBody.stages[stagePosition - 1]
  expect(initialStage.tacticalScene.status).toBe('READY')
  expect(initialStage.tacticalScene.placements.length).toBeGreaterThan(0)
  expect(initialStage.tacticalScene.triggers.length).toBeGreaterThan(0)
  expect(initialStage.tacticalScene.outcomes.length).toBeGreaterThan(0)

  const retry = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/retry`, {
    headers,
    data: { endingCount: planBody.endingCount, adventureLength: planBody.adventureLength },
  })
  expect(retry.ok()).toBeTruthy()
  const retriedBody = await retry.json()
  expect(retriedBody.status).toBe('READY')
  expect(retriedBody.version).toBeGreaterThan(planBody.version)
  const refreshedPlanResponse = await request.get(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/gm`, { headers })
  expect(refreshedPlanResponse.ok()).toBeTruthy()
  const refreshedPlanBody = await refreshedPlanResponse.json()
  const activeStage = refreshedPlanBody.stages[stagePosition - 1]
  expect(activeStage.tacticalScene.status).toBe('READY')

  const sessionResponse = await request.get(`${backend}/api/v1/adventure-sessions/${sessionId}`, { headers })
  expect(sessionResponse.ok()).toBeTruthy()
  session = await sessionResponse.json()
  expect(session.characterLimit).toBe(expectedPartySize)
  expect(session.party).toHaveLength(expectedPartySize)
  if (session.status === 'DRAFT') {
    const started = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/start`, {
      headers: { ...headers, 'If-Match-Version': String(session.version), 'Idempotency-Key': randomUUID() },
      data: { adventureId: randomUUID() },
    })
    expect(started.ok()).toBeTruthy()
    expect((await started.json()).status).toBe('STARTED')
  }

  const activation = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${stagePosition}/activate-map`, { headers })
  expect(activation.ok()).toBeTruthy()
  const activationBody = await activation.json()
  const activeCombatMapId = activationBody.combatMapId
  expect(activeCombatMapId).toBeTruthy()

  const playerProjection = await request.get(`${backend}/internal/v1/combat-maps/${activeCombatMapId}/player-view?ownerId=${playerId}`, { headers: { 'X-Internal-Token': process.env.INTERNAL_SERVICE_TOKEN ?? 'local-dev-internal-token' } })
  expect(playerProjection.ok()).toBeTruthy()
  const projected = await playerProjection.json()
  expect(projected.tokens).toBeInstanceOf(Array)
  expect(projected.tokens.every((token: { discovery?: string }) => token.discovery !== 'HIDDEN')).toBeTruthy()

  const triggerId = activeStage.tacticalScene.triggers[0].id
  const commandId = randomUUID()
  const trigger = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${stagePosition}/triggers/${triggerId}/apply`, {
    headers,
    data: { combatMapId: activeCombatMapId, commandId, expectedVersion: 0 },
  })
  expect(trigger.ok()).toBeTruthy()
  expect((await trigger.json()).triggerId).toBe(triggerId)

  const futureStage = refreshedPlanBody.stages.find((stage: { position: number }) => stage.position > stagePosition)
  expect(futureStage).toBeTruthy()
  const revision = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${futureStage.position}/tactical-scene/revise`, {
    headers, data: futureStage.tacticalScene.plan,
  })
  expect(revision.ok()).toBeTruthy()
  const revisedBody = await revision.json()
  expect(revisedBody.version).toBeGreaterThan(refreshedPlanBody.version)
  expect(revisedBody.stages[stagePosition - 1].title).toBe(refreshedPlanBody.stages[stagePosition - 1].title)
})
