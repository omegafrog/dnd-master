import { expect, test } from '@playwright/test'
import { createHash } from 'node:crypto'
import { bootstrapAdventureSession, explorationGroupStorybooks, hasRuntimeCredentials, isStorybookReady,
  explorationGroupLearnedAbilityIds, readPersistedAdventureRuleSetId, readPersistedMapVisibilityRuleTurn, readStoredMapDefinitions, sessionCreationPayload } from './support/runtime-adventure'

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
    assetLocatorKind: assetLocator?.match(/\bpage\s+\d+\b/i) ? 'page' : assetLocator ? 'other' : 'missing',
    assetLocatorLength: assetLocator?.length ?? 0,
    assetLocatorFingerprint: createHash('sha256').update(assetLocator ?? '').digest('hex').slice(0, 16),
    sourceDocumentId: sourceDocumentId ?? null,
    sourceEvidenceLocatorLength: mapDefinition?.source?.locator?.length ?? 0,
    sourceEvidenceLocatorFingerprint: createHash('sha256').update(mapDefinition?.source?.locator ?? '').digest('hex').slice(0, 16),
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
  // This #367 fixture marks its prepared opening as map-required; use the
  // session UI's existing start-position recovery before testing the cycle.
  const openingMapPreparation = page.getByRole('region', { name: '시작 위치 수동 선택' })
  await expect(openingMapPreparation).toBeVisible({ timeout: 180_000 })
  await page.screenshot({
    path: `${process.env.HARNESS_SESSION_DIR}/runtime/e2e/367-opening-map-preparation.png`,
    fullPage: true,
  })
  const playerMap = openingMapPreparation.getByRole('region', { name: '플레이어 전투 맵' })
  await playerMap.getByRole('button', { name: '전체 지도' }).click()
  await playerMap.getByRole('button', { name: '자르기 적용' }).click()
  const openingGridPreparation = playerMap.getByRole('region', { name: '맵 격자 맞추기' })
  await openingGridPreparation.getByRole('button', { name: '격자 맞추기' }).click()
  await playerMap.locator('.map-grid-alignment-actions').getByRole('button', { name: '적용' }).click()
  await playerMap.getByRole('button', { name: '빈 격자 3,3' }).click()
  await expect(playerMap.getByRole('status').filter({ hasText: '수동 대안으로 선택한 시작 칸' })).toBeVisible()
  await playerMap.getByRole('button', { name: '벽·문 편집' }).click()
  await playerMap.getByRole('button', { name: '맵 초안 저장' }).click()
  await expect(playerMap.getByRole('button', { name: '맵 준비 완료, 모험 시작' })).toBeEnabled()
  await playerMap.getByRole('button', { name: '맵 준비 완료, 모험 시작' }).click()
  await expect(page).toHaveURL(new RegExp(`#\\/sessions\\/${session.sessionId}\\?mode=play`), { timeout: 180_000 })

  const startedSession = await page.evaluate(async ({ sessionId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventure-sessions/${sessionId}`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error(`session read failed: ${response.status}`)
    return await response.json() as { status: string, adventureId: string }
  }, { sessionId: session.sessionId })
  expect(startedSession.status).toBe('STARTED')
  const adventureId = startedSession.adventureId
  const ruleSetId = await readPersistedAdventureRuleSetId(adventureId)
  const appliedRuleSet = await page.evaluate(async ({ adventureId, ruleSetId, rulebookId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventures/${adventureId}/applied-rule-set`, {
      method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ ruleSetId, edition: 'DND_5E_2014', rulebookIds: [rulebookId] }),
    })
    return { ok: response.ok, status: response.status, body: await response.text() }
  }, { adventureId, ruleSetId, rulebookId: session.rulebookId })
  expect(appliedRuleSet.ok, `apply the fixture rulebook before map movement: ${appliedRuleSet.status} ${appliedRuleSet.body}`).toBe(true)
  const groupsPanel = page.getByRole('region', { name: '탐험 그룹 관리' })
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
  const secondGroupId = startedCycle.cycle!.groupOrder[1]
  const firstGroup = startedCycle.groups.find(group => group.id === firstGroupId)!
  const tacticalMap = page.getByLabel('tactical-map')
  await expect(tacticalMap).toBeVisible()
  const firstGroupToken = tacticalMap.locator(`[data-token-id="${firstGroup.mapTokenId}"]`)
  await expect(firstGroupToken).toHaveAttribute('aria-label', 'PLAYER 3,3')
  await page.screenshot({
    path: `${process.env.HARNESS_SESSION_DIR}/runtime/e2e/367-active-group-before-movement.png`,
    fullPage: true,
  })
  const trapDestination = tacticalMap.getByRole('button', { name: '격자 4,3' })
  await expect(trapDestination).toBeEnabled()
  const movementPreview = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/combat-map/movement-preview`)
    && response.request().method() === 'POST')
  await firstGroupToken.dragTo(trapDestination)
  await expect(page.getByRole('dialog', { name: '맵 행동 확인' })).toBeVisible()
  expect((await movementPreview).ok()).toBeTruthy()
  const moveSubmitted = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/turns`)
    && response.request().method() === 'POST')
  await page.getByRole('dialog', { name: '맵 행동 확인' }).getByRole('button', { name: '확인' }).click()
  const moveResponse = await moveSubmitted
  expect(moveResponse.ok(), `group movement submission failed: ${moveResponse.status()}`).toBeTruthy()
  const savedMovementState = tacticalMap.getByRole('region', { name: '저장된 이동 상태' })
  if (await savedMovementState.isVisible().catch(() => false)) {
    const spatialRoll = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/combat-map/movement-operations/`)
      && response.url().endsWith('/roll') && response.request().method() === 'POST')
    await savedMovementState.getByRole('button', { name: '주사위 굴리기' }).click()
    expect((await spatialRoll).ok()).toBeTruthy()
  }
  const stoppedCycle = await readGroups(page, adventureId)
  const movementEvent = stoppedCycle.cycle?.pendingMovementEvent
  expect(stoppedCycle.cycle?.phase).toBe('GROUP_TURNS')
  expect(movementEvent).toBeTruthy()
  expect(movementEvent?.groupId).toBe(firstGroupId)
  expect(movementEvent?.cause).toBe('MOVEMENT_INTERRUPTED')
  expect(movementEvent?.publicEvents.length).toBeGreaterThan(0)
  const stoppedMap = await readMap(page, adventureId)
  const stoppedToken = stoppedMap.tokens.find(token => token.id === firstGroup.mapTokenId)
  expect(stoppedToken).toBeTruthy()
  expect(movementEvent?.finalPosition).toEqual({ x: stoppedToken!.x, y: stoppedToken!.y })
  expect([[3, 3], [4, 3]]).toContainEqual([stoppedToken!.x, stoppedToken!.y])
  console.info('[367-movement-event]', JSON.stringify({
    groupId: firstGroupId, eventId: movementEvent?.id, cause: movementEvent?.cause,
    finalPosition: movementEvent?.finalPosition, persistedPosition: { x: stoppedToken!.x, y: stoppedToken!.y },
    publicEventCount: movementEvent?.publicEvents.length, result: 'interrupted',
  }))
  const eventResolved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-cycle/events/${movementEvent!.id}/resolve`)
    && response.request().method() === 'POST')
  await explorationCycle.getByRole('button', { name: '사건을 해결하고 다음 그룹으로' }).click()
  expect((await eventResolved).ok()).toBeTruthy()
  const secondGroupTurn = await readGroups(page, adventureId)
  expect(secondGroupTurn.cycle?.pendingMovementEvent).toBeNull()
  expect(secondGroupTurn.cycle?.completedGroups).toContain(firstGroupId)
  expect(secondGroupTurn.cycle?.completedGroups).not.toContain(secondGroupId)
  expect(secondGroupTurn.cycle?.groupOrder[1]).toBe(secondGroupId)
  expect(secondGroupTurn.cycle?.currentGroupId).toBe(secondGroupId)

  const secondGroup = secondGroupTurn.groups.find(group => group.id === secondGroupId)!
  const secondGroupToken = tacticalMap.locator(`[data-token-id="${secondGroup.mapTokenId}"]`)
  await expect(secondGroupToken).toHaveAttribute('aria-label', 'PLAYER 3,3')
  const secondMovementPreview = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/combat-map/movement-preview`)
    && response.request().method() === 'POST')
  await secondGroupToken.dragTo(tacticalMap.getByRole('button', { name: '격자 2,3' }))
  await expect(page.getByRole('dialog', { name: '맵 행동 확인' })).toBeVisible()
  expect((await secondMovementPreview).ok()).toBeTruthy()
  const secondMoveSubmitted = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/turns`)
    && response.request().method() === 'POST')
  await page.getByRole('dialog', { name: '맵 행동 확인' }).getByRole('button', { name: '확인' }).click()
  expect((await secondMoveSubmitted).ok()).toBeTruthy()
  const secondMovedMap = await readMap(page, adventureId)
  expect(secondMovedMap.tokens.find(token => token.id === secondGroup.mapTokenId)).toMatchObject({ x: 2, y: 3 })
  expect((await readGroups(page, adventureId)).cycle?.pendingMovementEvent).toBeNull()
  console.info('[367-group-movement]', JSON.stringify({
    groupId: secondGroupId, tokenId: secondGroup.mapTokenId, savedPosition: { x: 2, y: 3 },
    result: 'committed_without_interruption',
  }))

  const secondGroupTurnFinished = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-cycle/groups/${secondGroupId}/finish`)
    && response.request().method() === 'POST')
  await explorationCycle.getByRole('button', { name: '현재 그룹 차례 완료' }).click()
  expect((await secondGroupTurnFinished).ok()).toBeTruthy()
  const environmentCycle = await readGroups(page, adventureId)
  expect(environmentCycle.cycle?.phase).toBe('ENVIRONMENT')
  expect(environmentCycle.cycle?.environmentPhase).toBe('ENEMIES_PATROLS_AND_DETECTION')
  const mapAtEnvironmentStart = await readMap(page, adventureId)
  const visibilityRuleTurnBefore = await readPersistedMapVisibilityRuleTurn(mapAtEnvironmentStart.mapId)

  for (const [expectedCurrentPhase, expectedNextPhase] of [
    ['ENEMIES_PATROLS_AND_DETECTION', 'HAZARDS_AND_ENVIRONMENT'],
    ['HAZARDS_AND_ENVIRONMENT', 'DURATIONS_AND_DEADLINES'],
  ] as const) {
    expect((await readGroups(page, adventureId)).cycle?.environmentPhase).toBe(expectedCurrentPhase)
    const phaseAdvanced = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-cycle/environment/advance`)
      && response.request().method() === 'POST')
    await explorationCycle.getByRole('button', { name: '환경 단계 진행' }).click()
    expect((await phaseAdvanced).ok()).toBeTruthy()
    const nextEnvironmentCycle = await readGroups(page, adventureId)
    expect(nextEnvironmentCycle.cycle?.phase).toBe('ENVIRONMENT')
    expect(nextEnvironmentCycle.cycle?.environmentPhase).toBe(expectedNextPhase)
    expect(nextEnvironmentCycle.cycle?.elapsedSeconds).toBe(0)
    await expect(explorationCycle.getByRole('status')).toContainText(`환경 진행: ${expectedNextPhase}`)
  }

  const durationsAdvanced = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventureId}/exploration-cycle/environment/advance`)
    && response.request().method() === 'POST')
  await explorationCycle.getByRole('button', { name: '환경 단계 진행' }).click()
  expect((await durationsAdvanced).ok()).toBeTruthy()
  const nextExplorationCycle = await readGroups(page, adventureId)
  expect(nextExplorationCycle.cycle?.phase).toBe('GROUP_TURNS')
  expect(nextExplorationCycle.cycle?.cycleNumber).toBe(2)
  expect(nextExplorationCycle.cycle?.elapsedSeconds).toBe(6)
  await expect(explorationCycle.getByRole('status')).toContainText('지난 시간 6초 · 그룹 이동')
  const mapAfterEnvironment = await readMap(page, adventureId)
  const visibilityRuleTurnAfter = await readPersistedMapVisibilityRuleTurn(mapAfterEnvironment.mapId)
  console.info('[367-game-time]', JSON.stringify({
    mapId: mapAfterEnvironment.mapId,
    visibilityRuleTurnBefore,
    visibilityRuleTurnAfter,
    expectedIncrease: 1,
    persistedElapsedSeconds: nextExplorationCycle.cycle?.elapsedSeconds,
    result: visibilityRuleTurnAfter === visibilityRuleTurnBefore + 1 ? 'advanced' : 'unexpected',
  }))
  expect(visibilityRuleTurnAfter).toBe(visibilityRuleTurnBefore + 1)

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
      cycle: { cycleNumber: number, groupOrder: string[], completedGroups: string[], phase: string, environmentPhase: string,
        elapsedSeconds: number, currentGroupId: string | null, pendingMovementEvent: {
          id: string, groupId: string, cause: string, finalPosition: { x: number, y: number }, publicEvents: string[]
        } | null } | null
    }>
  }, { adventureId })
}

async function readMap(page: import('@playwright/test').Page, adventureId: string) {
  return page.evaluate(async ({ adventureId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventures/${adventureId}/combat-map`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error(`map read failed: ${response.status}`)
    return response.json() as Promise<{ mapId: string, tokens: Array<{ id: string, type: string, x: number, y: number }> }>
  }, { adventureId })
}
