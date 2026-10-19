import { expect, test } from '@playwright/test'
import { bootstrapAdventureSession, explorationGroupStorybooks, hasRuntimeCredentials, isStorybookReady,
  explorationGroupLearnedAbilityIds, readStoredMapDefinitions, sessionCreationPayload } from './support/runtime-adventure'

const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD

test('storybook extraction warnings are terminal when the indexed content remains usable', () => {
  expect(isStorybookReady('INDEXED_WITH_WARNINGS')).toBe(true)
  expect(isStorybookReady('PROCESSING')).toBe(false)
})

test('group-flow session creation binds an empty action-preparation record', () => {
  expect(sessionCreationPayload('package-1', 3, 2, 'empty-preparation')).toEqual({
    scenarioPackageId: 'package-1', blueprintId: 'package-1', blueprintRevision: 3, partySize: 2,
    preparationId: 'empty-preparation',
  })
})

test('exploration group bootstrap uses a non-combat map fixture', () => {
  expect(explorationGroupStorybooks.map(source => [source.role, source.path.split('/').at(-1)])).toEqual([
    ['MAIN_SCENARIO', 'exploration-groups-main.pdf'],
    ['MAP', 'exploration-groups-map.pdf'],
  ])
})

test('group-flow fixture starts with a learned ability for adventure preparation', () => {
  expect(explorationGroupLearnedAbilityIds).toEqual(['spell-node-2810-fireball'])
})

test('지도에서 나눈 그룹과 대열을 저장하고 다시 불러온다', async ({ page, request }) => {
  test.skip(!hasRuntimeCredentials(), '실제 백엔드 로그인 정보가 필요합니다')
  test.setTimeout(0)

  const session = await bootstrapAdventureSession(request, 2, explorationGroupStorybooks, explorationGroupLearnedAbilityIds)
  const mapDefinitions = await readStoredMapDefinitions(session.packageId)
  const mapDefinition = mapDefinitions[0]
  const sourceDocumentReference = mapDefinition?.source?.knowledgeDocumentId
  const sourceDocumentId = typeof sourceDocumentReference === 'string' ? sourceDocumentReference : sourceDocumentReference?.value
  const assetLocator = mapDefinition?.assetLocator
  const pageNumber = assetLocator?.match(/\bpage\s+(\d+)\b/i)?.[1]
  const sourceLocator = pageNumber ? `page ${pageNumber}` : assetLocator
  const sourceResponse = sourceDocumentId && sourceLocator
    ? await request.get(`${process.env.BACKEND_E2E_URL}/internal/v1/story-sources/${sourceDocumentId}/assets?locator=${encodeURIComponent(sourceLocator)}`, {
      headers: { 'X-Internal-Token': process.env.INTERNAL_SERVICE_TOKEN ?? 'local-development-internal-token' },
    })
    : null
  console.info('[367-map-source]', JSON.stringify({
    mapDefinitionCount: mapDefinitions.length,
    mapDefinitionId: mapDefinition?.id ?? null,
    assetId: mapDefinition?.assetId ?? null,
    assetLocator: assetLocator ?? null,
    requestedAssetLocator: sourceLocator ?? null,
    sourceDocumentId: sourceDocumentId ?? null,
    sourceEvidenceLocator: mapDefinition?.source?.locator ?? null,
    assetStatus: sourceResponse?.status() ?? null,
    assetContentType: sourceResponse?.headers()['content-type'] ?? null,
    assetBytes: Number(sourceResponse?.headers()['content-length'] ?? 0) || null,
  }))
  expect(sourceResponse?.status(), 'the prepared map asset locator should retrieve its source image').toBe(200)
  expect(sourceResponse?.headers()['content-type']).toContain('image/png')
  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.goto(`/#/sessions/${session.sessionId}`)
  await expect(page.getByRole('button', { name: '시나리오 런타임 시작' })).toBeEnabled()
  await page.getByRole('button', { name: '시나리오 런타임 시작' }).click()
  await expect(page).toHaveURL(new RegExp(`#\\/sessions\\/${session.sessionId}\\?mode=play`), { timeout: 180_000 })

  const adventureId = await page.evaluate(async ({ sessionId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventure-sessions/${sessionId}`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error(`session read failed: ${response.status}`)
    return (await response.json() as { adventureId: string }).adventureId
  }, { sessionId: session.sessionId })
  expect(await hasActiveMap(page, adventureId), 'the opening Situation is mapless').toBe(false)
  const playerAction = '테스트에서 준비한 다음 시츄에이션 안으로 들어갑니다.'
  await page.getByLabel('무엇을 하시겠어요?').fill(playerAction)
  await page.getByRole('button', { name: '행동 보내기' }).click()
  const placement = page.getByRole('region', { name: '시츄에이션 진입 시작 위치 선택' })
  const groupsPanel = page.getByRole('region', { name: '탐험 그룹 관리' })
  await expect(placement).toBeVisible({ timeout: 180_000 })
  await page.screenshot({
    path: `${process.env.HARNESS_SESSION_DIR}/runtime/e2e/367-map-entry-placement.png`,
    fullPage: true,
  })
  await expect(placement.getByRole('button', { name: '전체 지도' })).toBeVisible({ timeout: 15_000 })
  await placement.getByRole('button', { name: '전체 지도' }).click()
  await placement.getByRole('button', { name: '자르기 적용' }).click()
  const gridPreparation = placement.getByRole('region', { name: '맵 격자 맞추기' }).first()
  await gridPreparation.getByRole('button', { name: '격자 맞추기' }).first().click()
  await placement.locator('.map-grid-alignment-actions').getByRole('button', { name: '적용' }).click()
  await placement.locator('button[aria-label^="빈 격자 "]:not([disabled])').first().click()
  await expect(placement.getByRole('status').filter({ hasText: '수동 대안으로 선택한 시작 칸' })).toBeVisible()
  await placement.getByRole('button', { name: '벽·문 편집' }).click()
  await placement.getByRole('button', { name: '맵 초안 저장' }).click()
  await expect(placement.getByRole('button', { name: '맵 준비 완료, 모험 시작' })).toBeEnabled()
  await placement.getByRole('button', { name: '맵 준비 완료, 모험 시작' }).click()

  await expect(groupsPanel).toBeVisible({ timeout: 60_000 })
  const formation = groupsPanel.getByRole('combobox', { name: /^대열 / }).nth(1)
  const formationSaved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await formation.selectOption('MIDDLE')
  const formationResponse = await formationSaved
  expect(formationResponse.ok(), await formationResponse.text()).toBeTruthy()

  const savedBeforeSplit = await readGroups(page, adventureId)
  const mapBeforeSplit = await readMap(page, adventureId)
  expect(savedBeforeSplit.groups).toHaveLength(1)
  expect(mapBeforeSplit.tokens).toHaveLength(1)
  expect(mapBeforeSplit.tokens[0].type).toBe('PLAYER')
  expect(savedBeforeSplit.groups[0].mapTokenId).toBe(mapBeforeSplit.tokens[0].id)

  const splitSaved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await groupsPanel.getByRole('button', { name: '현재 위치에서 분리' }).click()
  const splitResponse = await splitSaved
  expect(splitResponse.ok(), await splitResponse.text()).toBeTruthy()
  const split = await readGroups(page, adventureId)
  expect(split.groups).toHaveLength(2)
  expect(split.groups.flatMap(group => group.members)).toHaveLength(2)
  expect(split.groups.map(group => group.members[0].formation).sort()).toEqual(['FRONT', 'MIDDLE'])
  const splitMap = await readMap(page, adventureId)
  const groupTokens = split.groups.map(group => splitMap.tokens.find(token => token.id === group.mapTokenId))
  expect(groupTokens.every(Boolean)).toBe(true)
  expect(groupTokens.map(token => `${token!.x},${token!.y}`).every(position => position === `${groupTokens[0]!.x},${groupTokens[0]!.y}`)).toBe(true)

  const middleMemberId = split.groups.flatMap(group => group.members).find(member => member.formation === 'MIDDLE')!.characterSheetId
  const initiativeFormationSaved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await groupsPanel.getByRole('combobox', { name: `대열 ${middleMemberId}` }).selectOption('FRONT')
  expect((await initiativeFormationSaved).ok()).toBeTruthy()
  const initiativeEligibleGroups = await readGroups(page, adventureId)
  expect(initiativeEligibleGroups.groups).toHaveLength(2)
  expect(initiativeEligibleGroups.groups.every(group => group.members.some(member => member.formation === 'FRONT'))).toBe(true)

  const explorationCycle = groupsPanel.getByRole('region', { name: '탐험 순환' })
  const cycleStarted = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-cycle/start`)
    && response.request().method() === 'POST')
  await explorationCycle.getByRole('button', { name: '탐험 순환 시작' }).click()
  expect((await cycleStarted).ok()).toBeTruthy()
  const startedCycle = await readGroups(page, adventureId)
  const expectedGroupOrder = startedCycle.groups.map(group => group.id)
  expect(startedCycle.cycle?.phase).toBe('GROUP_TURNS')
  expect(startedCycle.cycle?.groupOrder).toHaveLength(2)
  expect([...startedCycle.cycle!.groupOrder].sort()).toEqual([...expectedGroupOrder].sort())

  const firstGroupId = startedCycle.cycle!.groupOrder[0]
  const firstGroupTurnFinished = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-cycle/groups/${firstGroupId}/finish`)
    && response.request().method() === 'POST')
  await explorationCycle.getByRole('button', { name: '현재 그룹 차례 완료' }).click()
  expect((await firstGroupTurnFinished).ok()).toBeTruthy()
  const secondGroupTurn = await readGroups(page, adventureId)
  expect(secondGroupTurn.cycle?.phase).toBe('GROUP_TURNS')
  expect(secondGroupTurn.cycle?.completedGroups).toEqual([firstGroupId])
  expect(secondGroupTurn.cycle?.groupOrder[1]).toBeTruthy()

  const secondGroupId = startedCycle.cycle!.groupOrder[1]
  const secondGroupTurnFinished = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-cycle/groups/${secondGroupId}/finish`)
    && response.request().method() === 'POST')
  await explorationCycle.getByRole('button', { name: '현재 그룹 차례 완료' }).click()
  expect((await secondGroupTurnFinished).ok()).toBeTruthy()
  expect((await readGroups(page, adventureId)).cycle?.phase).toBe('ENVIRONMENT')

  const formationRestored = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await groupsPanel.getByRole('combobox', { name: `대열 ${middleMemberId}` }).selectOption('MIDDLE')
  expect((await formationRestored).ok()).toBeTruthy()

  const mergeSaved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await groupsPanel.getByRole('button', { name: '그룹 합류' }).click()
  const mergeResponse = await mergeSaved
  expect(mergeResponse.ok(), await mergeResponse.text()).toBeTruthy()

  await page.reload()
  const restored = await readGroups(page, adventureId)
  expect(restored.groups).toHaveLength(1)
  expect(restored.groups[0].members).toHaveLength(2)
  expect(restored.groups[0].members.map(member => member.formation).sort()).toEqual(['FRONT', 'MIDDLE'])
  const restoredMap = await readMap(page, adventureId)
  expect(restoredMap.tokens.filter(token => token.type === 'PLAYER').map(token => token.id)).toEqual([restored.groups[0].mapTokenId])
})

async function readGroups(page: import('@playwright/test').Page, adventureId: string) {
  return page.evaluate(async ({ adventureId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventures/${adventureId}/exploration-groups`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error(`group read failed: ${response.status}`)
    return response.json() as Promise<{
      version: number
      groups: Array<{ id: string, mapTokenId: string, members: Array<{ characterSheetId: string, formation: string }> }>
      cycle: { cycleNumber: number, groupOrder: string[], completedGroups: string[], phase: string, elapsedSeconds: number } | null
    }>
  }, { adventureId })
}

async function readMap(page: import('@playwright/test').Page, adventureId: string) {
  return page.evaluate(async ({ adventureId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventures/${adventureId}/combat-map`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error(`map read failed: ${response.status}`)
    return response.json() as Promise<{ tokens: Array<{ id: string, type: string, x: number, y: number }> }>
  }, { adventureId })
}

async function hasActiveMap(page: import('@playwright/test').Page, adventureId: string) {
  return page.evaluate(async ({ adventureId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventures/${adventureId}/combat-map`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error(`map read failed: ${response.status}`)
    return Boolean((await response.json() as { mapId: string | null }).mapId)
  }, { adventureId })
}
