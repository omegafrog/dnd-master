import { expect, test } from '@playwright/test'
import { existsSync } from 'node:fs'
import { readFile, writeFile } from 'node:fs/promises'
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
const journeyStatePath = process.env.BACKEND_E2E_STATE_PATH ?? '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/potent-brew-browser-state.json'

type BrowserJourneyState = { sessionId: string; authSession: string; adventureId?: string }

async function createDirectCompanion(
  page: import('@playwright/test').Page,
  sessionId: string,
  name: string,
) {
  const responseTimeout = 120_000
  await page.getByRole('button', { name: '새 캐릭터 만들기', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`#\\/sessions\\/${sessionId}\\/character`), { timeout: responseTimeout })
  await page.getByRole('button', { name: '기본 정보', exact: true }).click()
  const characterName = page.getByPlaceholder('이름을 입력하세요')
  await expect(characterName).toBeVisible({ timeout: responseTimeout })
  await characterName.fill(name)
  await page.getByLabel('직업', { exact: true }).selectOption({ label: '파이터' })
  await page.getByLabel('종족', { exact: true }).selectOption({ label: '인간' })
  await page.getByLabel('배경', { exact: true }).selectOption({ label: '학자' })
  await page.getByLabel('성향', { exact: true }).selectOption({ label: '중립 선' })
  await page.getByRole('button', { name: '능력치', exact: true }).click()
  for (const [label, value] of [['근력', '15'], ['민첩', '14'], ['건강', '13'], ['지능', '12'], ['지혜', '10'], ['매력', '8']] as const) {
    await page.getByLabel(`${label} 능력치`, { exact: true }).selectOption(value)
  }
  const characterResponse = page.waitForResponse(
    response => response.request().method() === 'POST' && response.url().includes(`/adventure-sessions/${sessionId}/character-sheets`),
    { timeout: responseTimeout },
  )
  const partyResponse = page.waitForResponse(
    response => response.request().method() === 'POST' && response.url().endsWith(`/adventure-sessions/${sessionId}/party`),
    { timeout: responseTimeout },
  )
  await page.getByRole('button', { name: /캐릭터 저장하기/ }).click()
  const characterResult = await characterResponse
  if (!characterResult.ok()) {
    throw new Error(`캐릭터 ${name} 생성 API 실패 (${characterResult.status()}): ${await characterResult.text()}`)
  }

  const partyResult = await partyResponse
  if (!partyResult.ok()) {
    throw new Error(`캐릭터 ${name} 파티 추가 API 실패 (${partyResult.status()}): ${await partyResult.text()}`)
  }
  await expect(page).toHaveURL(new RegExp(`#\\/sessions\\/${sessionId}\\/party`), { timeout: responseTimeout })
  await expect(page.getByRole('heading', { name: '모험을 함께할 파티' })).toBeVisible({ timeout: responseTimeout })
}

async function readJourneyState() {
  if (!existsSync(journeyStatePath)) throw new Error(`browser journey state is missing: ${journeyStatePath}`)
  return JSON.parse(await readFile(journeyStatePath, 'utf8')) as BrowserJourneyState
}

async function installJourneyAuth(page: import('@playwright/test').Page, state: BrowserJourneyState) {
  await page.addInitScript(({ authSession }) => {
    window.localStorage.setItem('dnd-master.auth-session', authSession)
  }, { authSession: state.authSession })
}

test.describe.serial('fresh Potent Brew browser journey', () => {
test('prepares the story package, characters, and party', async ({ page }) => {
  test.skip(!hasEnvironment, 'missing BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD, INTERNAL_SERVICE_TOKEN, or three Linux Potent Brew storybooks')
  test.setTimeout(1_800_000)

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
    const roleSelect = scenario.getByLabel(`${basename(asset.path)} 역할`)
    await roleSelect.selectOption(asset.role)
    await expect(roleSelect).toHaveValue(asset.role)
    await page.waitForTimeout(100)
  }
  await page.waitForTimeout(250)
  const bundleSaveResponse = page.waitForResponse(
    response => response.request().method() === 'POST' && response.url().endsWith('/api/v1/adventures/scenario-bundles'),
  )
  await scenario.getByRole('button', { name: '모험 자료 저장', exact: true }).click()
  const savedBundleResponse = await bundleSaveResponse
  if (!savedBundleResponse.ok()) throw new Error(`자료 역할 저장 API 실패 (${savedBundleResponse.status()}): ${await savedBundleResponse.text()}`)
  console.log(`[potent-brew] bundle save request=${savedBundleResponse.request().postData()}`)
  console.log(`[potent-brew] bundle save response=${await savedBundleResponse.text()}`)
  await expect(scenario.getByRole('button', { name: '모험 자료 다시 저장', exact: true })).toBeVisible({ timeout: 30_000 })
  const savedDocuments = page.getByRole('list', { name: '저장된 모험 자료 목록' })
  await expect(savedDocuments.getByText(/자료 4개/).first()).toBeVisible()
  for (const asset of storybooks) {
    await expect(scenario.getByLabel(`${basename(asset.path)} 역할`)).toHaveValue(asset.role)
  }

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
  const preparationSucceeded = preparation.getByText(/게임 준비 완료|모험 준비 결과 .* · COMPLETE/)
  const preparationFailed = preparation.getByText('게임 준비 실패', { exact: true })
  await expect.poll(async () => {
    if (await preparationFailed.isVisible()) return `FAILED: ${await preparationFailed.innerText()}`
    if (await preparationSucceeded.isVisible()) return 'PUBLISHED'
    return 'WAITING'
  }, {
    timeout: 180_000,
    message: '게임 준비가 PUBLISHED 또는 FAILED 상태에 도달하지 않았습니다.',
  }).toBe('PUBLISHED')
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
  const authSession = await page.evaluate(() => window.localStorage.getItem('dnd-master.auth-session') ?? '')
  await writeFile(journeyStatePath, JSON.stringify({ sessionId, authSession }), 'utf8')
  await page.goto(`/#/sessions/${sessionId}/party`)
  await expect(page.getByRole('heading', { name: '모험을 함께할 파티' })).toBeVisible({ timeout: 60_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/09-party-ready-for-plan.png', fullPage: true })
  await createDirectCompanion(page, sessionId!, 'Aria')
  for (const name of ['Borin', 'Celia', 'Darin']) {
    await createDirectCompanion(page, sessionId!, name)
  }
  await expect(page.locator('.party-slot-grid .party-slot:not(.party-slot-empty)')).toHaveCount(4)
  for (const name of ['Aria', 'Borin', 'Celia', 'Darin']) {
    await expect(page.locator('.party-slot-grid').getByText(name, { exact: true })).toBeVisible()
  }
  await writeFile(journeyStatePath, JSON.stringify({ sessionId, authSession }), 'utf8')
})

test('generates the story plan until play preparation is complete', async ({ page }) => {
  test.skip(!hasEnvironment, 'missing BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD, INTERNAL_SERVICE_TOKEN, or three Linux Potent Brew storybooks')
  test.setTimeout(600_000)
  const state = await readJourneyState()
  await installJourneyAuth(page, state)
  await page.goto(`/#/sessions/${state.sessionId}/party`)
  await expect(page.getByRole('heading', { name: '모험을 함께할 파티' })).toBeVisible({ timeout: 60_000 })
  await page.getByRole('button', { name: '모험 계획 만들기', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험 계획 설정' })).toBeVisible({ timeout: 60_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/10-adventure-plan-settings.png', fullPage: true })
  await page.getByRole('button', { name: '모험 계획 생성', exact: true }).click()
  await expect(page.locator('.preparation-progress[role="status"]').getByText('플레이 준비 완료', { exact: true })).toBeVisible({ timeout: 600_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/11-adventure-plan-generated.png', fullPage: true })
})

test('starts the prepared adventure and reconnects to the map', async ({ page }) => {
  test.skip(!hasEnvironment, 'missing BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD, INTERNAL_SERVICE_TOKEN, or three Linux Potent Brew storybooks')
  test.setTimeout(300_000)
  const state = await readJourneyState()
  await installJourneyAuth(page, state)
  await page.goto(`/#/sessions/${state.sessionId}/story-plan`)
  await expect(page.getByRole('heading', { name: '모험 계획 준비' })).toBeVisible({ timeout: 60_000 })
  await page.getByRole('button', { name: '모험 시작', exact: true }).click()
  await expect(page).toHaveURL(/#\/adventures\//, { timeout: 120_000 })
  const adventureId = page.url().match(/#\/adventures\/([^/]+)/)?.[1]
  expect(adventureId, 'starting the prepared adventure did not produce an adventure id').toBeTruthy()
  await expect(page.getByRole('region', { name: '현재 전장' })).toBeVisible({ timeout: 120_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/12-adventure-started-map-entry.png', fullPage: true })
  await page.reload()
  await expect(page.getByRole('region', { name: '현재 전장' })).toBeVisible({ timeout: 120_000 })
  await page.screenshot({ path: '/home/jiwoo/workspace/dnd-master/docs/evidence/product-plan-journey/13-adventure-reconnected.png', fullPage: true })
  const currentState = await readJourneyState()
  await writeFile(journeyStatePath, JSON.stringify({ ...currentState, adventureId }), 'utf8')
})

test('continues the prepared adventure for five browser conversation turns', async ({ page }) => {
  test.skip(!hasEnvironment, 'missing BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD, INTERNAL_SERVICE_TOKEN, or three Linux Potent Brew storybooks')
  test.setTimeout(900_000)
  const state = await readJourneyState()
  expect(state.adventureId, 'prepared adventure state is missing an adventure id').toBeTruthy()
  await installJourneyAuth(page, state)
  await page.goto(`/#/adventures/${state.adventureId}`)

  const conversation = page.getByRole('region', { name: '모험 대화' })
  await expect(conversation).toBeVisible({ timeout: 120_000 })
  const initialGmCount = await conversation.locator('li.adventure-chat-message.gm').count()
  const turns = [
    '주변을 천천히 살펴본다.',
    '위험이 없는지 확인하며 앞으로 이동한다.',
    '주변의 소리와 흔적을 자세히 살핀다.',
    '발견한 단서를 동료들과 공유한다.',
    '다음에 갈 곳을 신중하게 결정한다.',
  ]

  for (const [index, action] of turns.entries()) {
    const beforeGmMessages = conversation.locator('li.adventure-chat-message.gm')
    const beforeCount = await beforeGmMessages.count()
    await conversation.getByLabel('무엇을 하시겠어요?').fill(action)
    await conversation.getByRole('button', { name: '행동 보내기', exact: true }).click()
    await expect.poll(() => beforeGmMessages.count(), {
      timeout: 180_000,
      intervals: [500, 1_000, 2_000, 5_000],
      message: `conversation turn ${index + 1} did not receive a new GM response`,
    }).toBeGreaterThan(beforeCount)
    const latestGmMessage = beforeGmMessages.last()
    await expect.poll(async () => (await latestGmMessage.innerText()).trim(), {
      timeout: 30_000,
      message: `conversation turn ${index + 1} received an empty GM response`,
    }).not.toBe('')
    await expect(conversation.getByRole('status')).not.toContainText(/턴 처리 실패|실패|오류|error/i)
    await expect(conversation.getByRole('alert')).not.toContainText(/실패|오류|error/i)
  }

  await expect.poll(() => conversation.locator('li.adventure-chat-message.gm').count()).toBeGreaterThanOrEqual(initialGmCount + turns.length)
  await expect(conversation.getByRole('status')).toContainText(/직접 플레이 입력 대기 중/)
})
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
