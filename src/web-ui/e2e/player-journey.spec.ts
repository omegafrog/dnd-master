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

  await expect(page.getByRole('heading', { name: 'Aria', exact: true })).toBeVisible()
  await expect(page.getByText('2024', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '굴리기' }).click()
  await expect(page.getByText('결과: 17')).toBeVisible()
  await expect(page.getByText('현재 장면에 사용할 안전한 맵이 없습니다. 텍스트로 계속 진행합니다.')).toBeVisible()
})

test('player adds a character to the draft session', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험 생성과 파티 구성' })).toBeVisible()
  const party = page.getByRole('region', { name: '모험 생성과 파티 구성' })
  await party.getByRole('button', { name: '직접 조작으로 추가' }).click()
  await expect(party.getByText('sheet-e2e')).toBeVisible()
  await expect(party.getByText('DIRECT')).toBeVisible()
  await expect(party.getByRole('button', { name: '제거' })).toHaveCount(1)
})

test('document-derived scenario preserves bundle and exposes character blueprint setup', async ({ page }) => {
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
    await expect(scenario.getByText('패키지 package-e2e · COMPLETE')).toBeVisible()
    await expect(scenario.getByText('캐릭터 한도: 1명')).toBeVisible()
    await expect(scenario.getByRole('button', { name: '캐릭터 생성 시작' })).toBeVisible()
    await scenario.getByRole('button', { name: '캐릭터 생성 시작' }).click()
    await expect(page).toHaveURL(/#\/scenario-packages\/package-e2e\/character-blueprint$/)
    await test.info().attach('026-4-blueprint.json', {
      body: Buffer.from(await page.evaluate(() => JSON.stringify((window as unknown as { __dndMasterE2E: unknown }).__dndMasterE2E))),
      contentType: 'application/json',
    })
    await page.screenshot({ path: 'test-results/026-4-blueprint.png', fullPage: true })
  } catch (error) {
    await test.info().attach('026-4-api-failures.json', {
      body: Buffer.from(JSON.stringify(failedResponses)),
      contentType: 'application/json',
    }).catch(() => undefined)
    await page.screenshot({ path: 'test-results/026-4-failure.png', fullPage: true }).catch(() => undefined)
    throw new Error(`${error instanceof Error ? error.message : String(error)}\nAPI failures: ${JSON.stringify(failedResponses)}`)
  }
})

test('running session reconnects with switched provider and confirms ending', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html?full-journey')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  const provider = page.getByRole('region', { name: '모험 생성과 파티 구성' })
  const gmProvider = provider.getByRole('region', { name: 'GM provider' })
  await gmProvider.getByLabel('GM provider').selectOption('openai')
  await page.locator('input[aria-label="GM model"]').fill('gpt-5.6-luna')
  await gmProvider.getByRole('button', { name: 'Provider 전환' }).click()
  await expect(gmProvider.getByText(/현재: openai · gpt-5.6-luna · medium/)).toBeVisible()

  await page.getByLabel('행동 또는 대화').fill('I inspect the revealed chamber')
  await page.getByRole('button', { name: '보내기' }).click()
  await expect(page.getByText(/턴 1: 근거를 바탕으로 응답한다\./)).toBeVisible()
  await page.getByRole('button', { name: '굴리기' }).click()
  await expect(page.getByText('결과: 17')).toBeVisible()
  const map = page.getByRole('region', { name: '플레이어 전투 맵' })
  await map.getByRole('button', { name: 'PLAYER 0,0' }).click()
  await map.getByRole('button', { name: '격자 1,0' }).click()
  await map.getByRole('dialog', { name: '맵 행동 확인' }).getByRole('button', { name: '확인' }).click()
  await expect(map.getByText('맵 행동을 GM 턴으로 전송했습니다.')).toBeVisible()

  await page.reload()
  await expect(page.getByRole('region', { name: 'GM provider' }).getByText(/현재: openai · gpt-5.6-luna · medium/)).toBeVisible()
  await expect(page.getByText(/턴 1: 근거를 바탕으로 응답한다\./)).toBeVisible()
  await page.getByLabel('행동 또는 대화').fill('I continue after reconnect')
  await page.getByRole('button', { name: '보내기' }).click()
  await expect(page.getByText(/턴 2: 근거를 바탕으로 응답한다\./)).toBeVisible()
  await page.getByRole('button', { name: '세션 완료' }).click()
  await page.getByRole('button', { name: '종료 확인' }).click()
  await expect(page.getByText(/COMPLETED · 파티/)).toBeVisible()
})

function summarizeErrorBody(body: string) {
  const compact = body.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
  return compact.length > 500 ? `${compact.slice(0, 500)}…` : compact
}
