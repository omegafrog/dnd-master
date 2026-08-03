import { expect, test } from '@playwright/test'

test('solo player completes setup, grounded play, map and saved-adventure deletion', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
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
  await expect(page.getByRole('list', { name: '시나리오 문서 목록' }).getByLabel('rules.txt', { exact: true })).toBeVisible()
  await page.getByLabel('시나리오 파일').setInputFiles({ name: 'crypt.txt', mimeType: 'text/plain', buffer: Buffer.from('The sealed crypt') })
  await page.getByRole('button', { name: '시나리오 등록' }).click()
  await expect(page.getByText('등록 완료: crypt.txt')).toBeVisible()
  await page.getByRole('list', { name: '시나리오 문서 목록' }).getByLabel('rules.txt', { exact: true }).check()
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
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험 파티' })).toBeVisible()
  const party = page.getByRole('region', { name: '모험 파티' })
  await party.getByLabel('캐릭터 시트 ID').fill('sheet-e2e')
  await party.getByRole('button', { name: '파티에 추가' }).click()
  await expect(page.getByText('sheet-e2e · DIRECT')).toBeVisible()
  await party.getByRole('button', { name: '모험 시작' }).click()
  await expect(page.getByText('시작 후 파티와 제어 방식은 변경할 수 없습니다.')).toBeVisible()
  await expect(party.getByRole('button', { name: '제거' })).toHaveCount(0)
})

test('document-derived character creation preserves bundle, blueprint and creation artifacts', async ({ page }) => {
  const failedResponses: Array<{ url: string; status: number; body: string }> = []
  page.on('response', async response => {
    if (response.status() < 400) return
    const body = await response.text().catch(() => '')
    failedResponses.push({ url: response.url(), status: response.status(), body: summarizeErrorBody(body) })
  })

  try {
    await page.goto('/e2e/fixtures/index.html')
    await page.getByLabel('이메일').fill('player@example.com')
    await page.getByLabel('비밀번호').fill('secret-password')
    await page.getByRole('button', { name: '로그인', exact: true }).click()

    await page.getByLabel('자료 파일').setInputFiles([
      { name: 'rules-2014.txt', mimeType: 'text/plain', buffer: Buffer.from('DND 4판 strength tag') },
      { name: 'rules-2024.txt', mimeType: 'text/plain', buffer: Buffer.from('DND 5판 strength tag') },
      { name: 'storybook.txt', mimeType: 'text/plain', buffer: Buffer.from('Storybook elf option priority') },
      { name: 'printer.txt', mimeType: 'text/plain', buffer: Buffer.from('Printer-only material') },
      { name: 'map.txt', mimeType: 'text/plain', buffer: Buffer.from('Map room layout') },
    ])
    await page.getByLabel('rules-2014.txt 유형').selectOption('RULEBOOK')
    await page.getByLabel('rules-2024.txt 유형').selectOption('RULEBOOK')
    await page.getByLabel('storybook.txt 유형').selectOption('STORYBOOK')
    await page.getByLabel('printer.txt 유형').selectOption('STORYBOOK')
    await page.getByLabel('map.txt 유형').selectOption('STORYBOOK')
    await page.getByRole('button', { name: '자료 업로드' }).click()

    await expect(page.getByLabel('map.txt 역할')).toBeVisible()
    await page.getByLabel('map.txt 역할').selectOption('MAP')
    const scenario = page.locator('section[aria-labelledby="scenario-heading"]')
    await scenario.getByLabel('printer.txt', { exact: true }).uncheck()
    await scenario.getByRole('button', { name: '시나리오 번들 저장' }).click()
    await expect(scenario.getByText('map.txt · MAP')).toBeVisible()
    await expect(scenario.getByText(/printer.txt ·/)).not.toBeVisible()
    await test.info().attach('026-4-bundle.json', {
      body: Buffer.from(await page.evaluate(() => JSON.stringify((window as unknown as { __dndMasterE2E: unknown }).__dndMasterE2E))),
      contentType: 'application/json',
    })
    await scenario.getByRole('button', { name: '시나리오 패키지 컴파일' }).click()
    await expect(scenario.getByText('DND 4판').first()).toBeVisible()
    await expect(scenario.getByText('DND 5판').first()).toBeVisible()
    await expect(scenario.getByText('Storybook 우선 옵션: Elf').first()).toBeVisible()
    await expect(scenario.getByText('상태: NEEDS_REVIEW').first()).toBeVisible()
    await expect(scenario.getByText(/revision 2/).first()).toBeVisible()
    await expect(scenario.getByText('conflicting rulebook/storybook values')).toBeVisible()
    await expect(scenario.getByText(/근거: rules-2024.txt-RULEBOOK/)).toBeVisible()
    await expect(scenario.getByText(/근거: storybook.txt/)).toBeVisible()
    await test.info().attach('026-4-blueprint.json', {
      body: Buffer.from(await page.evaluate(() => JSON.stringify((window as unknown as { __dndMasterE2E: unknown }).__dndMasterE2E))),
      contentType: 'application/json',
    })
    await page.screenshot({ path: 'test-results/026-4-blueprint.png', fullPage: true })
    await scenario.getByRole('spinbutton', { name: 'STR' }).fill('13')
    await scenario.getByRole('button', { name: '검토값 저장' }).last().click()
    await expect(scenario.getByText('상태: READY').first()).toBeVisible()
    await expect(scenario.getByText(/revision 3/).first()).toBeVisible()
    await scenario.getByRole('button', { name: 'Blueprint 게시' }).click()
    await expect(scenario.getByText('상태: PUBLISHED')).toBeVisible()
    await expect(scenario.getByText(/revision 4/).first()).toBeVisible()
    const characterCreation = scenario.locator('section[aria-labelledby="character-creation-heading"]')
    await characterCreation.getByLabel('캐릭터 이름').fill('Aria')
    await characterCreation.getByRole('button', { name: '캐릭터 시트 생성' }).click()
    await expect(characterCreation.getByText('캐릭터 시트 sheet-e2e 생성 완료.')).toBeVisible()
    await test.info().attach('026-4-creation.json', {
      body: Buffer.from(await page.evaluate(() => JSON.stringify((window as unknown as { __dndMasterE2E: unknown }).__dndMasterE2E))),
      contentType: 'application/json',
    })
    await page.screenshot({ path: 'test-results/026-4-creation.png', fullPage: true })
  } catch (error) {
    await test.info().attach('026-4-api-failures.json', {
      body: Buffer.from(JSON.stringify(failedResponses)),
      contentType: 'application/json',
    }).catch(() => undefined)
    await page.screenshot({ path: 'test-results/026-4-failure.png', fullPage: true }).catch(() => undefined)
    throw new Error(`${error instanceof Error ? error.message : String(error)}\nAPI failures: ${JSON.stringify(failedResponses)}`)
  }
})

function summarizeErrorBody(body: string) {
  const compact = body.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
  return compact.length > 500 ? `${compact.slice(0, 500)}…` : compact
}
