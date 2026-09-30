import { expect, test, type Page } from '@playwright/test'
import { assertPotentBrewStorybooks, bootstrapStartedAdventure, hasRuntimeEnvironment, readPersistedCompactionJobs, readPersistedConversationSummaries, readPersistedLongTermFacts, readPersistedRuntimeTurns } from './support/runtime-adventure'

const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD

test('새 모험 검증은 Linux 자료 경로와 세 가지 자료 역할을 요구한다', () => {
  const storybooks = [
    { path: '/home/jiwoo/workspace/dnd-master/docs/assets/main.pdf', role: 'MAIN_SCENARIO' },
    { path: '/home/jiwoo/workspace/dnd-master/docs/assets/map.pdf', role: 'MAP' },
    { path: '/home/jiwoo/workspace/dnd-master/docs/assets/handout.pdf', role: 'HANDOUT' },
  ]

  expect(() => assertPotentBrewStorybooks(storybooks)).not.toThrow()
  expect(() => assertPotentBrewStorybooks(storybooks.slice(0, 2))).toThrow(/HANDOUT/)
  expect(() => assertPotentBrewStorybooks(storybooks.map(source => ({ ...source, path: `C:\\assets\\${source.role}.pdf` }))))
    .toThrow(/Linux docs\/assets path/)
})

test('플레이어 행동은 저장되고 비공개 사실 식별자는 플레이어 응답에 포함되지 않는다', async ({ page, request }) => {
  test.skip(!hasRuntimeEnvironment(), 'src/start-dev.sh가 제공하는 실제 백엔드와 Potent Brew 자료가 필요합니다')
  assertPotentBrewStorybooks()
  test.setTimeout(0)

  const adventure = await bootstrapStartedAdventure(request)
  const initialRuntimeTurnCount = (await readPersistedRuntimeTurns(adventure.adventureId)).length
  const turnResponses: Array<{ status: number, body: unknown }> = []

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.goto(`/#/sessions/${adventure.sessionId}?mode=play`)
  await expect(page.getByRole('region', { name: '모험 대화' })).toBeVisible()
  await sendAction(page, adventure.adventureId, '주변을 살피고 다음 행동을 결정한다.', turnResponses)
  await sendAction(page, adventure.adventureId, '동료와 상의한 뒤 안전하게 진행한다.', turnResponses)
  await sendAction(page, adventure.adventureId, '현재 상황을 확인하고 계속 진행한다.', turnResponses)

  let turns: unknown[] = []
  await expect.poll(async () => {
    turns = await readPersistedRuntimeTurns(adventure.adventureId)
    return turns.length >= initialRuntimeTurnCount + 3
  }, { intervals: [1_000, 2_000, 5_000], timeout: 0 }).toBe(true)
  expect(turns.length).toBeGreaterThanOrEqual(initialRuntimeTurnCount + 3)
  for (const turn of turns) assertStructuredRuntimeTurn(turn)

  let jobs: Awaited<ReturnType<typeof readPersistedCompactionJobs>> = []
  await expect.poll(async () => {
    jobs = await readPersistedCompactionJobs(adventure.adventureId)
    const manualReview = jobs.find(job => job.sourceEnd > 0 && job.status === 'MANUAL_REVIEW')
    if (manualReview) throw new Error(`conversation compaction requires manual review for ${manualReview.sourceStart}-${manualReview.sourceEnd}: ${manualReview.error}`)
    return jobs.some(job => job.sourceEnd > 0 && job.status === 'DONE')
  }, { intervals: [1_000, 2_000, 5_000], timeout: 0 }).toBe(true)
  let summaries: Awaited<ReturnType<typeof readPersistedConversationSummaries>> = []
  await expect.poll(async () => {
    summaries = await readPersistedConversationSummaries(adventure.adventureId)
    return summaries.length >= 1
  }, { intervals: [1_000, 2_000, 5_000], timeout: 0 }).toBe(true)

  expect(summaries.every(summary => summary.sourceStart >= 0 && summary.sourceEnd >= summary.sourceStart
    && summary.sourceAdventureVersion >= 0 && Number.isInteger(summary.sourceAdventureVersion) && summary.textLength > 0)).toBe(true)
  expect(summaries.some(summary => jobs.some(job => job.status === 'DONE' && job.sourceEnd > 0
    && job.sourceStart === summary.sourceStart && job.sourceEnd === summary.sourceEnd))).toBe(true)
  expect(turnResponses).toHaveLength(3)
  for (const response of turnResponses) assertAcceptedTurnResponse(response, adventure.adventureId)

  // 기록 후보는 선택 사항이다. 생성 여부 대신 생성된 기록의 계약을 검증한다.
  const facts = await readPersistedLongTermFacts(adventure.adventureId)

  for (const fact of facts) {
    expect(fact.factId).toMatch(/^[0-9a-f-]{36}$/i)
    expect(fact.establishedTurnId).toMatch(/^[0-9a-f-]{36}$/i)
    expect(['EVENT', 'RELATIONSHIP', 'GOAL', 'THREAT']).toContain(fact.kind)
    expect(Number.isInteger(fact.sourceAdventureVersion)).toBe(true)
    expect(fact.sourceAdventureVersion).toBeGreaterThanOrEqual(0)
    expect(Number.isInteger(fact.version)).toBe(true)
    expect(fact.version).toBeGreaterThanOrEqual(1)
  }

  const serializedTurnResponses = JSON.stringify(turnResponses.map(response => response.body))
  for (const hiddenFact of facts.filter(fact => !fact.playerVisible)) expect(serializedTurnResponses).not.toContain(hiddenFact.factId)
})

function assertStructuredRuntimeTurn(value: unknown) {
  expect(value).toEqual(expect.any(Object))
  const turn = value as { lifecycle?: unknown, plan?: unknown, narration?: unknown }
  expect(turn.lifecycle).toBe('COMMITTED')
  expect(typeof turn.narration).toBe('string')
  expect(turn.plan).toEqual(expect.any(Object))
  const plan = turn.plan as Record<string, unknown>
  for (const field of ['scene', 'judgment', 'narration']) expect(typeof plan[field]).toBe('string')
  for (const field of ['combatStartRequested', 'mapEntryRequested', 'stateTransitionRequested']) expect(typeof plan[field]).toBe('boolean')
  expect(Array.isArray(plan.combatEnemies)).toBe(true)
}

function assertAcceptedTurnResponse(response: { status: number, body: unknown }, adventureId: string) {
  expect(response.status).toBe(202)
  expect(response.body).toEqual(expect.any(Object))
  const body = response.body as Record<string, unknown>
  expect(body.adventureId).toBe(adventureId)
  expect(body.turnId).toEqual(expect.any(String))
  expect(typeof body.version).toBe('number')
  expect(typeof body.narration).toBe('string')
  expect(typeof body.currentScene).toBe('string')
  expect(Array.isArray(body.visibleFacts)).toBe(true)
  expect((body.visibleFacts as unknown[]).every(fact => typeof fact === 'string')).toBe(true)
}

async function sendAction(page: Page, adventureId: string, text: string, responses: Array<{ status: number, body: unknown }>) {
  const input = page.getByRole('textbox', { name: /무엇을 하시겠/ })
  const history = page.getByRole('list', { name: '대화 기록' })
  const before = await history.getByRole('listitem').count()
  await input.fill(text)
  const sent = page.waitForResponse(response => new URL(response.url()).pathname === `/api/v1/adventures/${adventureId}/turns`
    && response.request().method() === 'POST')
  await page.getByRole('button', { name: /행동 보내기/ }).click()
  const response = await sent
  const result = { status: response.status(), body: await response.json() }
  assertAcceptedTurnResponse(result, adventureId)
  responses.push(result)
  await expect.poll(() => history.getByRole('listitem').count(), { timeout: 0 }).toBeGreaterThan(before + 1)
  await expect.poll(() => input.isEnabled(), { timeout: 0 }).toBe(true)
  await expect(page.getByRole('status').first()).toHaveAttribute('aria-busy', 'false')
}
