import { expect, test } from '@playwright/test'

test('published package review creates a character session from the API fixture', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html?package-review-published')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  await expect(page.getByRole('heading', { name: '룰북 기본 스키마' })).toBeVisible()
  await expect(page.getByText('읽기 전용')).toBeVisible()
  await expect(page.getByText('사용 예정', { exact: true })).toBeVisible()

  const proposal = page.getByRole('article', { name: '스토리 속 성향' })
  await expect(proposal.getByText('질서 선 성향으로 묘사됩니다.')).toBeVisible()
  await expect(proposal.getByText('storybook.txt', { exact: true })).toBeVisible()
  await expect(proposal.getByText('현재 상태: 사용 예정', { exact: true })).toBeVisible()
  await proposal.getByText('원문 근거 보기', { exact: true }).click()
  const evidence = proposal.locator('details')
  await expect(evidence).toHaveAttribute('open', '')
  await expect(evidence.locator('blockquote')).toHaveText('질서 선의 수호자였다.')
  await expect(evidence.getByRole('list', { name: '스토리 속 성향 근거 목록' })).toContainText('질서 선의 수호자였다.')

  await expect(page.getByRole('button', { name: '캐릭터 생성 시작' })).toBeVisible()
  await page.getByRole('button', { name: '캐릭터 생성 시작' }).click()

  await expect.poll(() => page.evaluate(() => (window as unknown as { __dndMasterE2E: { sessionCreationRequest: unknown } }).__dndMasterE2E.sessionCreationRequest)).toEqual({
    scenarioPackageId: 'package-e2e',
    blueprintId: 'package-e2e',
    blueprintRevision: 2,
  })
  await expect.poll(() => page.evaluate(() => window.location.hash)).toBe('#/sessions/session-created-e2e/character-blueprint')
})

test('proposal decisions persist and confirmation publishes only the applied projection', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html?package-review-decisions')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  const appliedProposal = page.getByRole('article', { name: '스토리 속 성향' })
  const excludedProposal = page.getByRole('article', { name: '스토리 속 소속' })
  await expect(page.getByRole('button', { name: '캐릭터 생성에 사용할 설정 확정' })).toBeDisabled()

  await appliedProposal.getByRole('button', { name: '사용하기' }).click()
  await expect(appliedProposal.getByText('사용 예정', { exact: true })).toBeVisible()
  await excludedProposal.getByRole('button', { name: '제외하기' }).click()
  await expect(excludedProposal.getByText('제외 예정', { exact: true })).toBeVisible()

  const confirm = page.getByRole('button', { name: '캐릭터 생성에 사용할 설정 확정' })
  await expect(confirm).toBeEnabled()
  await confirm.click()
  await expect(page.getByRole('heading', { name: '설정이 확정되었습니다' })).toBeVisible()
  await expect(page.getByText('사용 예정 제안: 1개')).toBeVisible()
  await expect(page.getByText('제외 예정 제안: 1개')).toBeVisible()
  await expect(page.getByRole('button', { name: '캐릭터 생성 시작' })).toBeEnabled()
})
