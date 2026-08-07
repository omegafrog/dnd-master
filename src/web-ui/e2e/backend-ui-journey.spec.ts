import { expect, test } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const sessionId = process.env.BACKEND_E2E_SESSION_ID
const adventureId = process.env.BACKEND_E2E_ADVENTURE_ID

test('real backend UI preserves provider switch across reconnect', async ({ page }) => {
  test.skip(process.env.BACKEND_E2E_UI !== '1' || !backend || !email || !password || !sessionId,
    'set BACKEND_E2E_UI=1 and backend credentials/session variables')

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()

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

test('real backend UI completes grounded GM turns without leaking private fields', async ({ page }) => {
  test.skip(process.env.BACKEND_E2E_ACCEPTANCE !== '1' || !backend || !email || !password || !adventureId,
    'set BACKEND_E2E_ACCEPTANCE=1 and backend credentials/adventure variables')

  const turnResponses: Array<{ status: number; body: string }> = []
  const turnRequests: Array<{ url: string; body: string; headers: Record<string, string> }> = []
  page.on('request', request => {
    if (!request.url().includes(`/api/v1/adventures/${adventureId}/turns`)) return
    turnRequests.push({ url: request.url(), body: request.postData() ?? '', headers: request.headers() })
  })
  page.on('response', async response => {
    if (!response.url().includes(`/api/v1/adventures/${adventureId}/turns`)) return
    turnResponses.push({ status: response.status(), body: await response.text().catch(() => '') })
  })
  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.goto(`/#/adventures/${adventureId}`)

  for (const action of [
    '양조장 주변에서 움직임을 듣고 지각한다.',
    '이상한 증기를 견디며 내성 굴림을 한다.',
    '새 발자국을 따라간다.',
    '적대적인 생물의 행동 순서를 확인한다.',
    '검으로 공격하고 피해를 적용한다.',
  ]) {
    await page.getByLabel('행동 또는 대화').fill(action)
    await page.getByRole('button', { name: '보내기' }).click()
    await expect(page.getByRole('list', { name: '대화 기록' }).getByText(`플레이어: ${action}`)).toBeVisible()
  }
  await expect.poll(() => turnResponses.length, { timeout: 120_000 }).toBe(5)
  expect(turnRequests).toHaveLength(5)
  expect(turnRequests.map(request => request.headers['if-match-version'])).toEqual(['0', '1', '2', '3', '4'])
  expect(new Set(turnRequests.map(request => request.headers['idempotency-key'])).size).toBe(5)
  expect(turnRequests.every(request => !/systemPrompt|rawPrompt|secret|private/i.test(request.body))).toBeTruthy()
  expect(turnResponses.every(response => response.status >= 200 && response.status < 300)).toBeTruthy()
  for (const response of turnResponses) {
    expect(response.body).not.toMatch(/rawPrompt|systemPrompt|hidden|secret|private|internalReasoning/i)
    const body = JSON.parse(response.body) as { sourceRefs?: unknown[]; version?: number }
    if (body.sourceRefs) expect(body.sourceRefs.length).toBeGreaterThan(0)
  }
  await expect(page.locator('body')).not.toContainText(/DC\s*\d+|내부 추론|비공개 정보/i)
})
