import { expect, test } from '@playwright/test'
import { bootstrapStartedAdventure, explorationGroupStorybooks, hasRuntimeCredentials, isStorybookReady,
  explorationGroupLearnedAbilityIds, sessionCreationPayload } from './support/runtime-adventure'

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

  const adventure = await bootstrapStartedAdventure(request, 2, explorationGroupStorybooks, explorationGroupLearnedAbilityIds)
  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.goto(`/#/sessions/${adventure.sessionId}?mode=play`)

  const groupsPanel = page.getByRole('region', { name: '탐험 그룹 관리' })
  await expect(groupsPanel).toBeVisible({ timeout: 60_000 })
  const formation = groupsPanel.getByRole('combobox', { name: /^대열 / }).nth(1)
  const formationSaved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventure.adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await formation.selectOption('MIDDLE')
  const formationResponse = await formationSaved
  expect(formationResponse.ok(), await formationResponse.text()).toBeTruthy()

  const savedBeforeSplit = await readGroups(page, adventure.adventureId)
  const mapBeforeSplit = await readMap(page, adventure.adventureId)
  expect(savedBeforeSplit.groups).toHaveLength(1)
  expect(mapBeforeSplit.tokens).toHaveLength(1)
  expect(mapBeforeSplit.tokens[0].type).toBe('PLAYER')
  expect(savedBeforeSplit.groups[0].mapTokenId).toBe(mapBeforeSplit.tokens[0].id)

  const splitSaved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventure.adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await groupsPanel.getByRole('button', { name: '현재 위치에서 분리' }).click()
  const splitResponse = await splitSaved
  expect(splitResponse.ok(), await splitResponse.text()).toBeTruthy()
  const split = await readGroups(page, adventure.adventureId)
  expect(split.groups).toHaveLength(2)
  expect(split.groups.flatMap(group => group.members)).toHaveLength(2)
  expect(split.groups.map(group => group.members[0].formation).sort()).toEqual(['FRONT', 'MIDDLE'])
  const splitMap = await readMap(page, adventure.adventureId)
  const groupTokens = split.groups.map(group => splitMap.tokens.find(token => token.id === group.mapTokenId))
  expect(groupTokens.every(Boolean)).toBe(true)
  expect(groupTokens.map(token => `${token!.x},${token!.y}`).every(position => position === `${groupTokens[0]!.x},${groupTokens[0]!.y}`)).toBe(true)

  const mergeSaved = page.waitForResponse(response => response.url().includes(`/api/v1/adventures/${adventure.adventureId}/exploration-groups`)
    && response.request().method() === 'PUT')
  await groupsPanel.getByRole('button', { name: '그룹 합류' }).click()
  const mergeResponse = await mergeSaved
  expect(mergeResponse.ok(), await mergeResponse.text()).toBeTruthy()

  await page.reload()
  const restored = await readGroups(page, adventure.adventureId)
  expect(restored.groups).toHaveLength(1)
  expect(restored.groups[0].members).toHaveLength(2)
  expect(restored.groups[0].members.map(member => member.formation).sort()).toEqual(['FRONT', 'MIDDLE'])
  const restoredMap = await readMap(page, adventure.adventureId)
  expect(restoredMap.tokens.filter(token => token.type === 'PLAYER').map(token => token.id)).toEqual([restored.groups[0].mapTokenId])
})

async function readGroups(page: import('@playwright/test').Page, adventureId: string) {
  return page.evaluate(async ({ adventureId }) => {
    const token = JSON.parse(localStorage.getItem('dnd-master.auth-session') ?? '{}').accessToken as string | undefined
    if (!token) throw new Error('authenticated browser session is unavailable')
    const response = await fetch(`/api/v1/adventures/${adventureId}/exploration-groups`, { headers: { Authorization: `Bearer ${token}` } })
    if (!response.ok) throw new Error(`group read failed: ${response.status}`)
    return response.json() as Promise<{ version: number, groups: Array<{ id: string, mapTokenId: string, members: Array<{ characterSheetId: string, formation: string }> }> }>
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
