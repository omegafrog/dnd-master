import { expect, test } from '@playwright/test'

test('solo player can upload and inspect storybook materials', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '자료와 모험 설정' })).toBeVisible()

  await page.getByLabel('자료 파일').setInputFiles([
    { name: 'rules.txt', mimeType: 'text/plain', buffer: Buffer.from('Dexterity rules') },
    { name: 'story.txt', mimeType: 'text/plain', buffer: Buffer.from('A campaign journal') },
  ])
  await page.getByRole('button', { name: '자료 업로드' }).click()
  await expect(page.getByRole('list', { name: '문서 상태 목록' }).getByText('rules.txt', { exact: true })).toBeVisible()
  await expect(page.getByRole('list', { name: '문서 상태 목록' }).getByText('story.txt', { exact: true })).toBeVisible()
})

test('player adds a character to the draft session', async ({ page }) => {
  await page.goto('/e2e/fixtures/index.html')
  await page.getByLabel('이메일').fill('player@example.com')
  await page.getByLabel('비밀번호').fill('secret-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모험을 함께할 파티' })).toBeVisible()
  const party = page.getByRole('region', { name: '모험을 함께할 파티' })
  await party.getByRole('button', { name: '내 캐릭터로' }).click()
  await expect(party.getByText('Aria', { exact: true })).toBeVisible()
  await expect(party.getByText('직접 조작', { exact: true })).toBeVisible()
  await expect(party.getByRole('button', { name: '제거' })).toHaveCount(1)
})

test.skip('document-derived scenario preserves bundle and exposes character blueprint setup', async ({ page }) => {
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

  const provider = page.getByRole('region', { name: '모험을 함께할 파티' })
  const gmProvider = provider.locator('details.party-provider-settings')
  await gmProvider.locator('summary').click()
  await gmProvider.getByLabel('GM provider').selectOption('openai')
  await page.locator('input[aria-label="GM model"]').fill('gpt-5.6-luna')
  await gmProvider.getByRole('button', { name: '연결 변경' }).click()
  await expect(gmProvider.getByText(/openai · gpt-5.6-luna/)).toBeVisible()

  await page.getByLabel('무엇을 하시겠어요?').fill('I inspect the revealed chamber')
  await page.getByRole('button', { name: '행동 보내기' }).click()
  await expect(page.getByText(/턴 1: 근거를 바탕으로 응답한다\./)).toBeVisible()
  await page.getByRole('button', { name: '굴리기' }).click()
  await expect(page.getByText('결과: 17')).toBeVisible()
  const map = page.getByRole('region', { name: '플레이어 전투 맵' })
  await map.getByRole('button', { name: 'PLAYER 0,0' }).click()
  await map.getByRole('button', { name: '격자 1,0' }).click()
  await map.getByRole('dialog', { name: '맵 행동 확인' }).getByRole('button', { name: '확인' }).click()
  await expect(map.getByText('맵 행동을 GM 턴으로 전송했습니다.')).toBeVisible()

  await page.reload()
  await expect(provider.locator('details.party-provider-settings').getByText(/openai · gpt-5.6-luna/)).toBeVisible()
  await expect(page.getByText(/턴 1: 근거를 바탕으로 응답한다\./)).toBeVisible()
  await page.getByLabel('무엇을 하시겠어요?').fill('I continue after reconnect')
  await page.getByRole('button', { name: '행동 보내기' }).click()
  await expect(page.getByText(/턴 2: 근거를 바탕으로 응답한다\./)).toBeVisible()
  await page.getByRole('button', { name: '세션 완료' }).click()
  await page.getByRole('button', { name: '종료 확인' }).click()
  await expect(page.getByText(/완료 · 1\/1명/)).toBeVisible()
})

function summarizeErrorBody(body: string) {
  const compact = body.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
  return compact.length > 500 ? `${compact.slice(0, 500)}…` : compact
}
