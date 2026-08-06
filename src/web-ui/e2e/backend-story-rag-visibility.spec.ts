import { expect, test } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const token = process.env.BACKEND_E2E_TOKEN
const adventureId = process.env.BACKEND_E2E_ADVENTURE_ID
const playerId = process.env.BACKEND_E2E_PLAYER_ID
const hiddenExcerpt = process.env.BACKEND_E2E_HIDDEN_STORY_EXCERPT
const revealedSourceRef = process.env.BACKEND_E2E_REVEALED_STORY_REF ?? 'storybook:e2e:hidden-story'
const rerankedSourceRef = process.env.BACKEND_E2E_RERANKED_SOURCE_REF

test('browser turn keeps unrevealed story evidence out of response and persisted conversation', async ({ page, request }) => {
  test.skip(!backend || !token || !adventureId || !playerId || !hiddenExcerpt,
    'set BACKEND_E2E_URL, TOKEN, PLAYER_ID, ADVENTURE_ID and HIDDEN_STORY_EXCERPT against a seeded app-all/PostgreSQL instance')

  await page.route('**/api/v1/adventures/**', async route => {
    const source = route.request()
    const url = new URL(source.url())
    const response = await request.fetch(`${backend}${url.pathname}${url.search}`, {
      method: source.method(),
      headers: { ...source.headers(), authorization: `Bearer ${token}` },
      postData: source.postData() ?? undefined,
    })
    await route.fulfill({ response })
  })

  const turnResponses: Array<Record<string, unknown>> = []
  page.on('response', async response => {
    if (response.request().method() !== 'POST' || !response.url().endsWith(`/adventures/${adventureId}/turns`)) return
    expect(response.status()).toBe(202)
    turnResponses.push(await response.json() as Record<string, unknown>)
  })

  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('backend-e2e@example.com')
  await page.getByLabel('비밀번호').fill('backend-e2e')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.getByLabel('행동 또는 대화').fill('I inspect the sealed chamber')
  await page.getByRole('button', { name: '보내기' }).click()

  await expect.poll(() => turnResponses.length).toBe(1)
  expect(turnResponses[0].narration).toBeTruthy()
  expect(turnResponses[0].judgment).toBeTruthy()
  expect(turnResponses[0].currentScene).toBeTruthy()
  expect(JSON.stringify(turnResponses[0])).not.toContain(hiddenExcerpt)
  expect(turnResponses[0].sourceRefs).not.toContain(revealedSourceRef)

  const conversation = page.getByRole('list', { name: '대화 기록' })
  await expect(conversation).toContainText('I inspect the sealed chamber')
  await expect(conversation).not.toContainText(hiddenExcerpt)

  await page.getByLabel('행동 또는 대화').fill('I open the revealed chamber')
  await page.getByRole('button', { name: '보내기' }).click()
  await expect.poll(() => turnResponses.length).toBe(2)
  expect(turnResponses[1].narration).toBeTruthy()
  expect(turnResponses[1].sourceRefs).toContain(revealedSourceRef)

  await page.reload()
  await expect(page.getByRole('list', { name: '대화 기록' })).toContainText(hiddenExcerpt)
})

test('browser turn uses reranked context when distractors are present', async ({ page, request }) => {
  test.skip(!backend || !token || !adventureId || !playerId || !rerankedSourceRef,
    'set backend E2E variables plus RERANKED_SOURCE_REF against a seeded app-all/PostgreSQL instance')

  await page.route('**/api/v1/adventures/**', async route => {
    const source = route.request()
    const url = new URL(source.url())
    const response = await request.fetch(`${backend}${url.pathname}${url.search}`, {
      method: source.method(), headers: { ...source.headers(), authorization: `Bearer ${token}` },
      postData: source.postData() ?? undefined,
    })
    await route.fulfill({ response })
  })
  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().endsWith(`/adventures/${adventureId}/turns`))
  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('backend-e2e@example.com')
  await page.getByLabel('비밀번호').fill('backend-e2e')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.getByLabel('행동 또는 대화').fill('I attack the goblin with my sword and inspect the sealed chamber')
  await page.getByRole('button', { name: '보내기' }).click()

  const response = await responsePromise
  expect(response.status()).toBe(202)
  const body = await response.json() as Record<string, unknown>
  expect(body.sourceRefs).toContain(rerankedSourceRef)
})
