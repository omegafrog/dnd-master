import { expect, test } from '@playwright/test'

test('published package review creates a character session from the API fixture', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html?package-review-published')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  await expect(page.getByRole('heading', { name: '룰북 기본 스키마' })).toBeVisible()
  await expect(page.getByText('읽기 전용')).toBeVisible()
  await expect(page.getByText('사용 예정', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '캐릭터 생성 시작' })).toBeVisible()
  await page.getByRole('button', { name: '캐릭터 생성 시작' }).click()

  await expect.poll(() => page.evaluate(() => (window as unknown as { __dndMasterE2E: { sessionCreationRequest: unknown } }).__dndMasterE2E.sessionCreationRequest)).toEqual({
    scenarioPackageId: 'package-e2e',
    blueprintId: 'package-e2e',
    blueprintRevision: 2,
  })
  await expect.poll(() => page.evaluate(() => window.location.hash)).toBe('#/sessions/session-created-e2e/character-blueprint')
})
