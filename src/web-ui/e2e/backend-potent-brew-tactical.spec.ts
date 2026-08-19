import { expect, test } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const token = process.env.BACKEND_E2E_TOKEN
const sessionId = process.env.BACKEND_E2E_SESSION_ID
const stagePosition = Number(process.env.BACKEND_E2E_TACTICAL_STAGE_POSITION ?? '1')
const combatMapId = process.env.BACKEND_E2E_COMBAT_MAP_ID
const commandId = process.env.BACKEND_E2E_TACTICAL_COMMAND_ID

test('real Potent Brew backend preserves tactical retry, activation, projection, trigger, and revision flow', async ({ request }) => {
  test.skip(!backend || !token || !sessionId || !combatMapId || !commandId,
    'set BACKEND_E2E_URL, BACKEND_E2E_TOKEN, BACKEND_E2E_SESSION_ID, BACKEND_E2E_COMBAT_MAP_ID, and BACKEND_E2E_TACTICAL_COMMAND_ID')

  const headers = { Authorization: `Bearer ${token}` }
  const plan = await request.get(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan`, { headers })
  expect(plan.ok()).toBeTruthy()
  const planBody = await plan.json()
  expect(planBody.status).toBe('READY')
  expect(planBody.stages[stagePosition - 1].tacticalScenePlan?.status ?? planBody.stages[stagePosition - 1].groundingStatus).toBeTruthy()

  const retry = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/retry`, {
    headers,
    data: { endingCount: planBody.endingCount, adventureLength: planBody.adventureLength },
  })
  expect(retry.ok()).toBeTruthy()
  const retriedBody = await retry.json()
  expect(retriedBody.status).toBe('READY')
  expect(retriedBody.version).toBeGreaterThan(planBody.version)

  const activation = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${stagePosition}/activate-map`, { headers })
  expect(activation.ok()).toBeTruthy()
  expect((await activation.json()).combatMapId).toBe(combatMapId)

  const playerProjection = await request.get(`${backend}/internal/v1/combat-maps/${combatMapId}/player-view?ownerId=${process.env.BACKEND_E2E_PLAYER_ID}`, { headers: { 'X-Internal-Token': process.env.INTERNAL_SERVICE_TOKEN ?? 'local-dev-internal-token' } })
  expect(playerProjection.ok()).toBeTruthy()
  const projected = await playerProjection.json()
  expect(projected.tokens).toBeInstanceOf(Array)
  expect(projected.tokens.every((token: { discovery?: string }) => token.discovery !== 'HIDDEN')).toBeTruthy()

  const trigger = await request.post(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/stages/${stagePosition}/triggers/entry/apply`, {
    headers,
    data: { combatMapId, commandId, expectedVersion: 0 },
  })
  expect(trigger.ok()).toBeTruthy()
  expect((await trigger.json()).triggerId).toBe('entry')

  const revision = await request.get(`${backend}/api/v1/adventure-sessions/${sessionId}/story-plan/history`, { headers })
  expect(revision.ok()).toBeTruthy()
  expect((await revision.json()).length).toBeGreaterThanOrEqual(1)
})
