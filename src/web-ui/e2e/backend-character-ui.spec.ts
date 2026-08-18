import { expect, test, type APIRequestContext } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const packageId = process.env.BACKEND_E2E_SCENARIO_PACKAGE_ID

function requireEnvironment() {
  const missing = [
    ['BACKEND_E2E_URL', backend],
    ['BACKEND_E2E_EMAIL', email],
    ['BACKEND_E2E_PASSWORD', password],
    ['BACKEND_E2E_SCENARIO_PACKAGE_ID', packageId],
  ].filter(([, value]) => !value).map(([name]) => name)

  test.skip(
    missing.length > 0,
    `real character UI E2E requires a running backend and seeded published package; missing: ${missing.join(', ')}`,
  )
}

async function authenticate(request: APIRequestContext) {
  const response = await request.post(`${backend}/api/v1/auth/login`, { data: { username: email, password } })
  expect(response.ok(), await response.text()).toBeTruthy()
  const session = await response.json() as { token?: string }
  expect(session.token, 'login response did not include token').toBeTruthy()
  return { Authorization: `Bearer ${session.token}` }
}

test('published character blueprint opens the real character-generation entry', async ({ page, request }) => {
  requireEnvironment()

  const authHeaders = await authenticate(request)

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  const preparationResponse = await request.get(
    `${backend}/api/v1/scenario-packages/${packageId}/play-preparation`,
    { headers: authHeaders },
  )
  expect(preparationResponse.ok(), await preparationResponse.text()).toBeTruthy()
  const preparation = await preparationResponse.json() as {
    status: string
    characterCreationBlueprint: { available: boolean; status: string; revision?: number }
  }
  expect(preparation.status).toBe('READY')
  expect(preparation.characterCreationBlueprint).toMatchObject({ available: true, status: 'PUBLISHED' })
  expect(preparation.characterCreationBlueprint.revision).toBeGreaterThan(0)

  await page.goto(`/#/scenario-packages/${packageId}/character-blueprint`)
  await expect(page.getByRole('heading', { name: '캐릭터 생성 설정 검토' })).toBeVisible()
  await expect(page.getByRole('button', { name: '캐릭터 생성 시작' })).toBeEnabled()
  await page.getByRole('button', { name: '캐릭터 생성 시작' }).click()

  await expect(page).toHaveURL(/#\/sessions\/[^/]+\/character-blueprint$/)
  const sessionId = new URL(page.url()).hash.split('/')[2]
  const sessionResponse = await request.get(`${backend}/api/v1/adventure-sessions/${sessionId}`, { headers: authHeaders })
  expect(sessionResponse.ok(), await sessionResponse.text()).toBeTruthy()
  const session = await sessionResponse.json() as { scenarioPackageId: string; blueprintRevision: number }
  expect(session.scenarioPackageId).toBe(packageId)
  expect(session.blueprintRevision).toBe(preparation.characterCreationBlueprint.revision)
})
