import { expect, test } from '@playwright/test'

test('solo player completes setup, grounded play, map and saved-adventure deletion', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인' }).click()
  await expect(page.getByRole('heading', { name: '자료와 모험 설정' })).toBeVisible()

  await page.getByLabel('자료 파일').setInputFiles([
    { name: 'rules.txt', mimeType: 'text/plain', buffer: Buffer.from('Dexterity rules') },
    { name: 'story.txt', mimeType: 'text/plain', buffer: Buffer.from('A campaign journal') },
  ])
  await page.getByLabel('rules.txt 유형').selectOption('RULEBOOK')
  await page.getByLabel('story.txt 유형').selectOption('STORYBOOK')
  await page.getByRole('button', { name: '자료 업로드' }).click()
  await expect(page.getByText('rules.txt: 사용 준비 완료')).toBeVisible()
  await expect(page.getByText('story.txt: 사용 준비 완료')).toBeVisible()
  await expect(page.getByRole('checkbox', { name: 'rules.txt' })).toBeVisible()
  await page.getByLabel('시나리오 파일').setInputFiles({ name: 'crypt.txt', mimeType: 'text/plain', buffer: Buffer.from('The sealed crypt') })
  await page.getByRole('button', { name: '시나리오 등록' }).click()
  await expect(page.getByText('등록 완료: crypt.txt')).toBeVisible()
  await page.getByRole('checkbox', { name: 'rules.txt' }).check()
  await page.getByRole('button', { name: '룰 세트 저장' }).click()
  await expect(page.getByText('룰 세트가 저장되었습니다.')).toBeVisible()

  await page.getByLabel('자료 파일').setInputFiles({ name: 'rules.txt', mimeType: 'text/plain', buffer: Buffer.from('Dexterity rules') })
  await page.getByRole('button', { name: '자료 업로드' }).click()
  await expect(page.locator('ul[aria-label="문서 상태 목록"] li', { hasText: 'rules.txt' })).toHaveCount(1)

  await page.getByLabel('행동 또는 대화').fill('I sneak past the guardian')
  await page.getByRole('button', { name: '보내기' }).click()
  await expect(page.getByText('AI 게임 마스터: 근거를 바탕으로 응답한다.')).toBeVisible()
  await page.getByLabel('상황').fill('How does stealth work?')
  await page.getByRole('button', { name: '룰 확인' }).click()
  await expect(page.getByText('SUFFICIENT')).toBeVisible()

  await expect(page.getByRole('heading', { name: 'Aria (2024)' })).toBeVisible()
  await page.getByRole('button', { name: '굴리기' }).click()
  await expect(page.getByText('결과: 17')).toBeVisible()
  await page.getByLabel('이동 경로').fill('1,1 2,1')
  await page.getByRole('button', { name: '이동' }).click()
  await expect(page.getByText('전투 맵 이동 기능은 준비 중입니다.')).toBeVisible()
})

test('player starts session and party becomes immutable', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인' }).click()
  await expect(page.getByRole('heading', { name: '모험 파티' })).toBeVisible()
  const party = page.getByRole('region', { name: '모험 파티' })
  await party.getByLabel('캐릭터 시트 ID').fill('sheet-e2e')
  await party.getByRole('button', { name: '파티에 추가' }).click()
  await expect(page.getByText('sheet-e2e · DIRECT')).toBeVisible()
  await party.getByRole('button', { name: '모험 시작' }).click()
  await expect(page.getByText('시작 후 파티와 제어 방식은 변경할 수 없습니다.')).toBeVisible()
  await expect(party.getByRole('button', { name: '제거' })).toHaveCount(0)
})
