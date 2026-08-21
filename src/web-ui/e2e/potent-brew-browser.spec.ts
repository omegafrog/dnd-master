import { expect, test } from '@playwright/test'
import { basename } from 'node:path'

const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const internalToken = process.env.INTERNAL_SERVICE_TOKEN
const liveAdventureId = process.env.BACKEND_E2E_ADVENTURE_ID
const liveSessionId = process.env.BACKEND_E2E_SESSION_ID
const livePlayerId = process.env.BACKEND_E2E_PLAYER_ID
const assetRoot = '/home/jiwoo/workspace/dnd-master/docs/assets/'

type StorybookInput = { path: string; role: 'MAIN_SCENARIO' | 'MAP' | 'HANDOUT' }

function readStorybooks(): StorybookInput[] {
  const raw = process.env.BACKEND_E2E_STORYBOOKS_JSON ?? ''
  if (!raw.trim()) return []
  let parsed: unknown
  try { parsed = JSON.parse(raw) } catch (error) { throw new Error(`BACKEND_E2E_STORYBOOKS_JSON must be valid JSON: ${String(error)}`) }
  if (!Array.isArray(parsed)) throw new Error('BACKEND_E2E_STORYBOOKS_JSON must be an array')
  return parsed.map((entry, index) => {
    if (!entry || typeof entry !== 'object') throw new Error(`storybook entry ${index} must be an object`)
    const path = 'path' in entry ? entry.path : undefined
    const role = 'role' in entry ? entry.role : undefined
    if (typeof path !== 'string' || !path.startsWith(assetRoot)) throw new Error(`storybook entry ${index} must use a Linux docs/assets path`)
    if (role !== 'MAIN_SCENARIO' && role !== 'MAP' && role !== 'HANDOUT') throw new Error(`storybook entry ${index} has an unsupported Potent Brew role`)
    return { path, role }
  })
}

const storybooks = readStorybooks()
if (storybooks.length === 3 && new Set(storybooks.map(asset => asset.role)).size !== 3) {
  throw new Error('BACKEND_E2E_STORYBOOKS_JSON must contain exactly MAIN_SCENARIO, MAP, and HANDOUT roles')
}
const hasEnvironment = Boolean(backend && email && password && internalToken && storybooks.length === 3)

test('fresh Potent Brew browser journey selects three assets and saves their roles', async ({ page }) => {
  test.skip(!hasEnvironment, 'missing BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD, INTERNAL_SERVICE_TOKEN, or three Linux Potent Brew storybooks')
  test.setTimeout(600_000)

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험 준비가 완료되었습니다' })).toBeVisible({ timeout: 30_000 })
  const session = await page.evaluate(() => JSON.parse(window.localStorage.getItem('dnd-master.auth-session') ?? '{}') as { accessToken?: string; playerId?: string })
  expect(session.accessToken, 'browser login did not create an access token').toBeTruthy()
  expect(session.playerId, 'browser login did not create a player id').toBeTruthy()
  await page.goto('/#/setup')
  await expect(page.getByRole('heading', { name: '자료와 모험 설정' })).toBeVisible()
  await page.getByRole('checkbox', { name: /D&D 5e \(2014\) 선택/ }).check()

  const fileInput = page.getByLabel('자료 파일')
  await fileInput.setInputFiles(storybooks.map(asset => asset.path))
  await page.getByRole('button', { name: '자료 업로드', exact: true }).click()

  const documentList = page.getByRole('list', { name: '문서 상태 목록' })
  for (const asset of storybooks) {
    await expect(documentList.getByText(basename(asset.path), { exact: true })).toBeVisible({ timeout: 120_000 })
  }

  for (const asset of storybooks) {
    await expect.poll(async () => documentList.getByText(basename(asset.path), { exact: true }).locator('..').innerText(), {
      timeout: 120_000,
      intervals: [1000, 2000, 5000],
    }).toMatch(/사용 준비 완료|READY\s+100%/)
    await documentList.getByLabel(`${basename(asset.path)} 모험 자료 선택`).check()
  }

  const scenario = page.getByRole('region', { name: '모험 자료 구성' })
  await expect(scenario).toBeVisible()
  for (const asset of storybooks) {
    await scenario.getByLabel(`${basename(asset.path)} 역할`).selectOption(asset.role)
  }
  await scenario.getByRole('button', { name: '모험 자료 저장', exact: true }).click()
  await expect(scenario.getByRole('button', { name: '모험 자료 다시 저장', exact: true })).toBeVisible({ timeout: 30_000 })
  await expect(page.getByRole('list', { name: '저장된 모험 자료 목록' }).getByText(/자료 4개/).first()).toBeVisible()

  // Continue through the real browser preparation surface. The package action
  // must remain gated by COMPLETE; the API-only acceptance covers the later
  // authenticated tactical calls once this UI journey has created a package.
  await page.getByRole('link', { name: /현재 자료/ }).click()
  await expect(page.getByRole('heading', { name: '모험 자료 구성' })).toBeVisible()
  const preparationPanel = page.getByRole('heading', { name: '모험 준비 결과', exact: true }).locator('xpath=../..')
  await preparationPanel.getByRole('button', { name: '게임 준비', exact: true }).click()
  const preparation = page.getByRole('dialog', { name: '게임 준비' })
  await expect(preparation).toBeVisible()
  await preparation.getByRole('button', { name: '게임 준비 시작', exact: true }).click()
  await expect(preparation.getByText(/게임 준비 완료|모험 준비 결과 .* · COMPLETE/)).toBeVisible({ timeout: 180_000 })
  const characterSetup = preparation.getByRole('button', { name: '캐릭터 생성 시작', exact: true })
  await expect(characterSetup).toBeVisible()
  await characterSetup.click()
  await expect(page).toHaveURL(/#\/scenario-packages\/[^/]+\/character-blueprint/)
  await expect(page.getByRole('heading', { name: '캐릭터 생성 설정 검토' })).toBeVisible()
  const confirmSettings = page.getByRole('button', { name: '캐릭터 생성에 사용할 설정 확정', exact: true })
  await expect(confirmSettings).toBeVisible({ timeout: 30_000 })
  await expect(confirmSettings).toBeEnabled()
  await confirmSettings.click()
  await expect(page.getByRole('heading', { name: '설정이 확정되었습니다' })).toBeVisible({ timeout: 30_000 })
  await page.getByRole('button', { name: '캐릭터 생성 시작', exact: true }).click()
  await expect(page).toHaveURL(/#\/sessions\/[^/]+\/character-blueprint/)
  const sessionId = page.url().match(/#\/sessions\/([^/]+)\/character-blueprint/)?.[1]
  expect(sessionId).toBeTruthy()
  await page.goto(`/#/sessions/${sessionId}/party`)
  await expect(page.getByRole('heading', { name: '모험을 함께할 파티' })).toBeVisible({ timeout: 60_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/09-party-ready-for-plan.png', fullPage: true })
  await page.getByRole('button', { name: '새 캐릭터 만들기', exact: true }).click()
  await page.getByLabel('캐릭터 이름').fill('Aria')
  await page.getByLabel('직업', { exact: true }).selectOption({ label: '파이터' })
  await page.getByLabel('종족', { exact: true }).selectOption({ label: '인간' })
  await page.getByLabel('배경', { exact: true }).selectOption({ label: '학자' })
  await page.getByLabel('성향', { exact: true }).selectOption({ label: '중립 선' })
  await page.getByRole('button', { name: '능력치', exact: true }).click()
  for (const [label, value] of [['근력', '15'], ['민첩', '14'], ['건강', '13'], ['지능', '12'], ['지혜', '10'], ['매력', '8']] as const) {
    await page.getByLabel(`${label} 능력치`, { exact: true }).selectOption(value)
  }
  await page.getByRole('button', { name: /캐릭터 저장하기/ }).click()
  await expect(page.getByRole('heading', { name: '모험을 함께할 파티' })).toBeVisible({ timeout: 60_000 })
  for (let i = 0; i < 3; i++) {
    await page.getByRole('button', { name: 'AI 동료 제안받기', exact: true }).click()
    await expect(page.getByRole('button', { name: 'AI 동료로 채택', exact: true })).toBeVisible({ timeout: 60_000 })
    await page.getByRole('button', { name: 'AI 동료로 채택', exact: true }).click()
  }
  await page.getByRole('button', { name: '모험 계획 만들기', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험 계획 설정' })).toBeVisible({ timeout: 60_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/10-adventure-plan-settings.png', fullPage: true })
  await page.getByRole('button', { name: '모험 계획 생성', exact: true }).click()
  await expect(page.locator('.preparation-progress[role="status"]').getByText('플레이 준비 완료', { exact: true })).toBeVisible({ timeout: 600_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/11-adventure-plan-generated.png', fullPage: true })
})

test('live Potent Brew browser journey exposes only safe map data and completes tactical contracts', async ({ page }) => {
  const ready = Boolean(backend && email && password && internalToken && liveAdventureId && liveSessionId && livePlayerId)
  test.skip(!ready, 'missing BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD, INTERNAL_SERVICE_TOKEN, BACKEND_E2E_ADVENTURE_ID, BACKEND_E2E_SESSION_ID, or BACKEND_E2E_PLAYER_ID')
  test.setTimeout(180_000)

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험 준비가 완료되었습니다' })).toBeVisible({ timeout: 30_000 })
  await page.goto(`/#/adventures/${liveAdventureId}`)
  await expect(page.getByRole('region', { name: '현재 전장' })).toBeVisible()
  await expect(page.getByText(/모험 ID:/)).toBeVisible()
  await expect(page.locator('body')).not.toContainText('HIDDEN')
  await expect(page.locator('body')).not.toContainText('groundingStatus')
  await expect(page.locator('body')).not.toContainText('playerSpawnX')

  // The browser-authenticated journey now verifies the internal tactical
  // continuation contracts after the safe player UI is visible.
  const request = page.request
  const bearer = await page.evaluate(() => window.localStorage.getItem('dnd-master.auth-session'))
  const authorization = bearer ? { Authorization: `Bearer ${JSON.parse(bearer).accessToken}` } : {}
  const internalHeaders = { ...authorization, 'X-Internal-Token': internalToken! }
  const planResponse = await request.get(`${backend}/api/v1/adventure-sessions/${liveSessionId}/story-plan/gm`, { headers: internalHeaders })
  await expect(planResponse).toBeOK()
  const plan = await planResponse.json()
  const stagePosition = Number(process.env.BACKEND_E2E_TACTICAL_STAGE_POSITION ?? '1')
  const activeStage = plan.stages[stagePosition - 1]
  const activation = await request.post(`${backend}/api/v1/adventure-sessions/${liveSessionId}/story-plan/stages/${stagePosition}/activate-map`, { headers: authorization })
  await expect(activation).toBeOK()
  const activationBody = await activation.json()
  const projection = await request.get(`${backend}/internal/v1/combat-maps/${activationBody.combatMapId}/player-view?ownerId=${livePlayerId}`, { headers: internalHeaders })
  await expect(projection).toBeOK()
  const projected = await projection.json()
  expect(projected.tokens.every((token: { discovery?: string }) => token.discovery !== 'HIDDEN')).toBeTruthy()
  const triggerId = activeStage.tacticalScene.triggers[0].id
  const trigger = await request.post(`${backend}/api/v1/adventure-sessions/${liveSessionId}/story-plan/stages/${stagePosition}/triggers/${triggerId}/apply`, {
    headers: authorization,
    data: { combatMapId: activationBody.combatMapId, commandId: crypto.randomUUID(), expectedVersion: projected.version },
  })
  await expect(trigger).toBeOK()
  const futureStage = plan.stages.find((stage: { position: number }) => stage.position > stagePosition)
  expect(futureStage).toBeTruthy()
  const revision = await request.post(`${backend}/api/v1/adventure-sessions/${liveSessionId}/story-plan/stages/${futureStage.position}/tactical-scene/revise`, {
    headers: internalHeaders,
    data: futureStage.tacticalScene.plan,
  })
  await expect(revision).toBeOK()
})
