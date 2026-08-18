import { expect, test } from '@playwright/test'

test('solo journey exposes labelled controls, landmarks and keyboard navigation', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html')
  await expect(page.getByRole('main')).toHaveCount(1)
  await expect(page.getByRole('heading', { level: 1, name: 'D&D Master' })).toBeVisible()
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  await expect(page.getByRole('main')).toHaveCount(1)
  await expect(page.getByRole('heading', { level: 1, name: '자료와 모험 설정' })).toBeVisible()
  const missingControls = await page.locator('input, select, button').evaluateAll(elements => elements.filter(element => {
    if (element instanceof HTMLButtonElement) return !element.textContent?.trim() && !element.getAttribute('aria-label')
    const id = element.id
    return !element.closest('label') && !(id && document.querySelector(`label[for="${id}"]`)) && !element.getAttribute('aria-label')
  }).map(element => element.outerHTML))
  expect(missingControls, missingControls.join('\n')).toHaveLength(0)

  await page.keyboard.press('Tab')
  const focusedTag = await page.evaluate(() => document.activeElement?.tagName)
  expect(['INPUT', 'SELECT', 'BUTTON', 'A']).toContain(focusedTag)
  await expect(page.getByRole('status').first()).toBeAttached()
})
