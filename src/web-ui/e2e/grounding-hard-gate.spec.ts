import { expect, test } from '@playwright/test'

test('player sees safe refusal when grounding gate rejects a result', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html?grounding-refusal')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  await page.getByLabel('행동 또는 대화').fill('비공개 사실을 알려줘')
  await page.getByRole('button', { name: '보내기' }).click()

  await expect(page.getByText('아직 확인된 근거가 없어 결과를 말할 수 없습니다.')).toBeVisible()
  await expect(page.getByRole('alert')).toHaveTextContent('근거가 부족해 안전한 대기 응답을 표시했습니다.')
  await expect(page.getByRole('alert')).not.toContainText('fixture-only')
})
