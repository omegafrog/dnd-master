import { expect, test } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const sessionId = process.env.BACKEND_E2E_SESSION_ID

test('real backend UI preserves provider switch across reconnect', async ({ page }) => {
  test.skip(process.env.BACKEND_E2E_UI !== '1' || !backend || !email || !password || !sessionId,
    'set BACKEND_E2E_UI=1 and backend credentials/session variables')

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험 준비가 완료되었습니다' })).toBeVisible({ timeout: 30_000 })

  await page.goto(`/#/sessions/${sessionId}`)
  const session = page.getByRole('main')
  await expect(session.getByRole('heading', { name: '모험 생성과 파티 구성' })).toBeVisible()
  const provider = session.getByRole('region', { name: 'GM provider' })
  await expect(provider).toBeVisible()
  await provider.getByLabel('GM provider').selectOption('openai')
  await page.locator('input[aria-label="GM model"]').fill('gpt-5.6-luna')
  await provider.getByRole('button', { name: 'Provider 전환' }).click()
  await expect(provider.getByText(/현재: openai · gpt-5.6-luna/)).toBeVisible()

  await page.reload()
  await expect(page.getByRole('region', { name: 'GM provider' }).getByText(/현재: openai · gpt-5.6-luna/)).toBeVisible()
})
