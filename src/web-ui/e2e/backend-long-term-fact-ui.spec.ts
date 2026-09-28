import { expect, test, type Page } from '@playwright/test'
import { bootstrapStartedAdventure, hasRuntimeEnvironment, readPersistedAdventureState, readPersistedCompactionJobs, readPersistedLongTermFacts } from './support/runtime-adventure'

const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD

test('플레이어 화면에서 확정한 목표는 장면 이동 뒤에도 모험별 기록으로 이어지고 비공개 사실을 노출하지 않는다', async ({ page, request }) => {
  test.skip(!hasRuntimeEnvironment(), 'src/start-dev.sh가 제공하는 실제 백엔드와 Potent Brew 자료가 필요합니다')
  test.setTimeout(0)

  const adventure = await bootstrapStartedAdventure(request)

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.goto(`/#/sessions/${adventure.sessionId}?mode=play`)
  await expect(page.getByRole('region', { name: '모험 대화' })).toBeVisible()
  const opening = await readPersistedAdventureState(adventure.adventureId)

  await sendAction(page, '우리 목표는 맥주 저장고의 거대 쥐를 찾아 퇴치하는 것이다.')
  await sendAction(page, '하늘 모자이크 패널만 밟아 왼쪽 문으로 들어간다.')
  const afterCrossing = await readPersistedAdventureState(adventure.adventureId)
  expect(afterCrossing.currentScene, '왼쪽 문을 선택한 행동 뒤에도 장면 이동이 저장되지 않음')
    .not.toBe(opening.currentScene)
  await sendAction(page, '맥주 저장고에서 거대 쥐를 찾아 퇴치하는 목표를 계속 추진하며 주변을 조사한다.')

  let jobs: Awaited<ReturnType<typeof readPersistedCompactionJobs>> = []
  await expect.poll(async () => {
    jobs = await readPersistedCompactionJobs(adventure.adventureId)
    return jobs.length > 0 && jobs.every(job => ['DONE', 'MANUAL_REVIEW'].includes(job.status))
  }, { intervals: [1_000, 2_000, 5_000], timeout: 0 }).toBe(true)
  // A candidate that fails provenance/shape validation is intentionally sent to
  // manual review. The live contract requires at least one valid publication
  // and a durable goal row; it does not treat a separate rejected range as loss.
  expect(jobs.some(job => job.status === 'DONE')).toBe(true)

  const facts = await readPersistedLongTermFacts(adventure.adventureId)
  expect(facts.some(fact => fact.playerVisible && Number.isFinite(fact.sourceAdventureVersion)),
    '요약 작업은 완료됐지만 확인된 목표·관계 기록이 저장되지 않음').toBe(true)

  expect(facts.some(fact => /목표|맥주 저장고|거대 쥐/i.test(fact.relevance))).toBe(true)
  await expect(page.getByRole('list', { name: '대화 기록' })).toContainText(/맥주 저장고|거대 쥐/)

  // 이 실제 Potent Brew 경로가 비공개 장기 기록을 만들지 않으면, UI 응답의
  // 미공개 사실 누설 여부만 여기서 확인한다. 비공개 행의 선택 제외는
  // RuntimeGmPromptComposer 정책 테스트가 별도로 고정한다.
  const conversation = await page.getByRole('list', { name: '대화 기록' }).innerText()
  const hiddenFacts = facts.filter(fact => !fact.playerVisible)
  for (const hidden of hiddenFacts) expect(conversation).not.toContain(hidden.relevance)
  if (hiddenFacts.length === 0) expect(conversation).not.toMatch(/비밀 통로|숨겨진 단서|범인|정답/)
})

async function sendAction(page: Page, text: string) {
  const input = page.getByRole('textbox', { name: /무엇을 하시겠/ })
  const history = page.getByRole('list', { name: '대화 기록' })
  const before = await history.getByRole('listitem').count()
  await input.fill(text)
  await page.getByRole('button', { name: /행동 보내기/ }).click()
  await expect.poll(() => history.getByRole('listitem').count(), { timeout: 0 }).toBeGreaterThan(before + 1)
  await expect.poll(() => input.isEnabled(), { timeout: 0 }).toBe(true)
}
