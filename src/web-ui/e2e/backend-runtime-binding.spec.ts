import { expect, test } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const adventureId = process.env.BACKEND_E2E_ADVENTURE_ID
const playerId = process.env.BACKEND_E2E_PLAYER_ID
const scenarioPackageId = process.env.BACKEND_E2E_SCENARIO_PACKAGE_ID
const rulebookId = process.env.BACKEND_E2E_RULEBOOK_ID

test('real backend accepts party-only RuntimeBinding request', async ({ request }) => {
  test.skip(!backend || !adventureId || !playerId || !scenarioPackageId || !rulebookId,
    'set BACKEND_E2E_* variables against a running app-all backend')

  const response = await request.post(`${backend}/api/v1/adventures/${adventureId}/runtime-bindings`, {
    headers: { Authorization: `Bearer ${process.env.BACKEND_E2E_TOKEN ?? ''}` },
    data: {
      playerId,
      scenarioPackageId,
      rulebookIds: [rulebookId],
      engineId: 'ollama',
      toolIds: ['search'],
    },
  })

  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.party).toBeInstanceOf(Array)
  expect(body).not.toHaveProperty('characterSheetId')
})
