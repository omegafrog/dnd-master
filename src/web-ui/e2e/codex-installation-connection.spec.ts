import { expect, test } from '@playwright/test'
import { fileURLToPath } from 'node:url'

const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const fakeCodexExecutable = fileURLToPath(new URL('./fixtures/codex-app-server-already-authenticated.sh', import.meta.url))

test('real app gates protected routes until the existing Codex login links this installation', async ({ page, request }) => {
  test.skip(process.env.BACKEND_E2E_CODEX_CONNECTION !== '1',
    'run the dedicated live Codex connection contract with BACKEND_E2E_CODEX_CONNECTION=1')
  expect(backend, 'BACKEND_E2E_URL is required').toBeTruthy()
  expect(email, 'BACKEND_E2E_EMAIL is required').toBeTruthy()
  expect(password, 'BACKEND_E2E_PASSWORD is required').toBeTruthy()
  expect(process.env.CODEX_EXECUTABLE, 'start-dev.sh must use the test-only authenticated app-server fixture')
    .toBe(fakeCodexExecutable)

  const login = await request.post(`${backend}/api/v1/auth/login`, {
    data: { username: email, password },
  })
  expect(login.ok(), 'the seeded demo user must be available').toBeTruthy()
  const { token } = await login.json() as { token: string }
  expect(token).toBeTruthy()

  const disconnected = await request.delete(`${backend}/api/v1/profile/codex-connection`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(disconnected.ok()).toBeTruthy()
  expect(await disconnected.json()).toMatchObject({ status: 'DISCONNECTED', cliAvailable: true })

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toBeVisible()

  const protectedRoute = '/#/setup?mode=create'
  await page.goto(protectedRoute)
  await expect(page.getByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toHaveCount(0)

  const connectWithExistingLogin = async () => {
    const operationResponse = page.waitForResponse(response =>
      response.url().endsWith('/api/v1/profile/codex-connection/operations')
      && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Codex 계정 연결', exact: true }).click()
    const operation = await (await operationResponse).json() as {
      status: string
      pending: boolean
      authUrl?: string | null
    }
    expect(operation).toMatchObject({ status: 'CONNECTED', pending: false })
    expect(operation.authUrl).toBeFalsy()
    await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: '주요 메뉴' }).getByText(email!, { exact: true })).toBeVisible()
  }

  await connectWithExistingLogin()
  await expect(page).toHaveURL(/#\/setup\?mode=create$/)

  await page.goto('/#/profile')
  await expect(page.getByRole('heading', { name: 'Codex 계정 연결' })).toBeVisible()
  await page.getByRole('button', { name: '이 설치의 연결 해제' }).click()
  await expect(page.getByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '로그인' })).toHaveCount(0)

  await connectWithExistingLogin()
  await expect(page.getByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toHaveCount(0)
})
