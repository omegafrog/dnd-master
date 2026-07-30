import { expect, test } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const token = process.env.BACKEND_E2E_TOKEN
const playerId = process.env.BACKEND_E2E_PLAYER_ID
const bundleId = process.env.BACKEND_E2E_BUNDLE_ID
const scenarioPackageId = process.env.BACKEND_E2E_SCENARIO_PACKAGE_ID
const sessionId = process.env.BACKEND_E2E_SESSION_ID
const expectedTerms = (process.env.BACKEND_E2E_EXPECTED_BLUEPRINT_TERMS ?? '').split(',').map(term => term.trim()).filter(Boolean)

const headers = () => ({ Authorization: `Bearer ${token ?? ''}` })

async function attachJson(name: string, value: unknown) {
  await test.info().attach(name, {
    body: Buffer.from(JSON.stringify(value, null, 2)),
    contentType: 'application/json',
  })
}

test('real backend preserves document bundle, blueprint evidence and creation payload', async ({ request }) => {
  test.skip(!backend || !token || !playerId || !bundleId || !scenarioPackageId || !sessionId,
    'set BACKEND_E2E_URL, TOKEN, PLAYER_ID, BUNDLE_ID, SCENARIO_PACKAGE_ID and SESSION_ID against a seeded app-all backend')

  const bundleResponse = await request.get(`${backend}/api/v1/adventures/scenario-bundles/${bundleId}`, { headers: headers() })
  expect(bundleResponse.ok()).toBeTruthy()
  const bundle = await bundleResponse.json()
  await attachJson('026-4-real-bundle.json', bundle)
  expect(bundle.documents).toEqual(expect.arrayContaining([expect.objectContaining({ role: 'MAP' })]))
  expect(bundle.documents).toEqual(expect.arrayContaining([
    expect.objectContaining({ documentType: 'RULEBOOK', status: 'INDEXED' }),
    expect.objectContaining({ documentType: 'STORYBOOK', status: 'INDEXED' }),
  ]))
  expect(bundle.documents).not.toEqual(expect.arrayContaining([expect.objectContaining({ originalFilename: expect.stringMatching(/printer/i) })]))

  const preparationResponse = await request.get(`${backend}/api/v1/scenario-packages/${scenarioPackageId}/play-preparation`, { headers: headers() })
  expect(preparationResponse.ok()).toBeTruthy()
  const preparation = await preparationResponse.json()
  await attachJson('026-4-real-blueprint-before-publish.json', preparation)
  const blueprintText = JSON.stringify(preparation.characterCreationBlueprint)
  for (const term of expectedTerms) expect(blueprintText).toContain(term)
  const roots = preparation.characterCreationBlueprint.roots ?? []
  const evidenceNodes = JSON.stringify(roots)
  expect(evidenceNodes).toMatch(/sourceEvidence|sourceEvidence/i)
  expect(evidenceNodes).toMatch(/RULEBOOK/)
  expect(evidenceNodes).toMatch(/STORYBOOK/)

  const publishResponse = await request.post(`${backend}/api/v1/scenario-packages/${scenarioPackageId}/character-blueprint/publish`, { headers: headers() })
  expect(publishResponse.ok()).toBeTruthy()
  const publishedBlueprint = await publishResponse.json()
  await attachJson('026-4-real-blueprint-published.json', publishedBlueprint)
  expect(publishedBlueprint.status).toBe('PUBLISHED')

  const creationRequest = {
    adventureId: sessionId,
    ownerPlayerId: playerId,
    edition: 'DND_5E_2024',
    characterName: 'Playwright document character',
    level: 1,
    inspiration: false,
    blueprintRevision: publishedBlueprint.revision,
    blueprintValues: Object.fromEntries(roots.flatMap((node: { id: string; children?: typeof roots }) => [
      [node.id, node.value ?? ''],
      ...(node.children ?? []).map(child => [child.id, child.value ?? '']),
    ])),
  }
  const creationResponse = await request.post(`${backend}/internal/v1/adventure-sessions/${sessionId}/character-sheets`, {
    headers: { ...headers(), 'Content-Type': 'application/json' },
    data: creationRequest,
  })
  expect(creationResponse.ok()).toBeTruthy()
  const creation = await creationResponse.json()
  await attachJson('026-4-real-creation.json', { request: creationRequest, response: creation })
  expect(creation.characterSheetId).toBeTruthy()
})
