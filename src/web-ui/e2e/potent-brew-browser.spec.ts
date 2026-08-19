import { expect, test } from '@playwright/test'
import { basename } from 'node:path'

const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const rulebookPath = process.env.BACKEND_E2E_RULEBOOK_FILE
const assetRoot = '/home/jiwoo/workspace/dnd-master/docs/assets/'

type StorybookInput = { path: string; role: 'MAIN_SCENARIO' | 'MAP' | 'HANDOUT' }

function readStorybooks(): StorybookInput[] {
  const raw = process.env.BACKEND_E2E_STORYBOOKS_JSON ?? ''
  if (!raw.trim()) return []
  let parsed: unknown
  try { parsed = JSON.parse(raw) } catch (error) { throw new Error(`BACKEND_E2E_STORYBOOKS_JSON must be valid JSON: ${String(error)}`) }
  if (!Array.isArray(parsed)) throw new Error('BACKEND_E2E_STORYBOOKS_JSON must be an array')
  return parsed.map((entry, index) => {
    if (!entry || typeof entry !== 'object') throw new Error(`storybook entry ${index} must be an object`)
    const path = 'path' in entry ? entry.path : undefined
    const role = 'role' in entry ? entry.role : undefined
    if (typeof path !== 'string' || !path.startsWith(assetRoot)) throw new Error(`storybook entry ${index} must use a Linux docs/assets path`)
    if (role !== 'MAIN_SCENARIO' && role !== 'MAP' && role !== 'HANDOUT') throw new Error(`storybook entry ${index} has an unsupported Potent Brew role`)
    return { path, role }
  })
}

const storybooks = readStorybooks()
if (rulebookPath && !rulebookPath.startsWith(assetRoot)) throw new Error('BACKEND_E2E_RULEBOOK_FILE must use a Linux docs/assets path')
if (storybooks.length === 3 && new Set(storybooks.map(asset => asset.role)).size !== 3) {
  throw new Error('BACKEND_E2E_STORYBOOKS_JSON must contain exactly MAIN_SCENARIO, MAP, and HANDOUT roles')
}
const hasEnvironment = Boolean(backend && email && password && rulebookPath && storybooks.length === 3)

test('fresh Potent Brew browser journey selects three assets and saves their roles', async ({ page }) => {
  test.skip(!hasEnvironment, 'set backend credentials, rulebook file, and three Linux Potent Brew storybooks')
  test.setTimeout(180_000)

  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await page.goto('/#/setup')
  await expect(page.getByRole('heading', { name: '자료와 모험 설정' })).toBeVisible()

  const fileInput = page.getByLabel('자료 파일')
  await fileInput.setInputFiles(storybooks.map(asset => asset.path))
  await page.getByRole('button', { name: '자료 업로드', exact: true }).click()

  const documentList = page.getByRole('list', { name: '문서 상태 목록' })
  for (const asset of storybooks) {
    await expect(documentList.getByText(basename(asset.path), { exact: true })).toBeVisible({ timeout: 120_000 })
  }

  for (const asset of storybooks) {
    await expect.poll(async () => documentList.getByText(basename(asset.path), { exact: true }).locator('..').innerText(), {
      timeout: 120_000,
      intervals: [1000, 2000, 5000],
    }).toMatch(/사용 준비 완료/)
    await documentList.getByLabel(`${basename(asset.path)} 모험 자료 선택`).check()
  }

  const scenario = page.getByRole('region', { name: '모험 자료 구성' })
  await expect(scenario).toBeVisible()
  for (const asset of storybooks) {
    await scenario.getByLabel(`${basename(asset.path)} 역할`).selectOption(asset.role)
  }
  await scenario.getByRole('button', { name: '모험 자료 저장', exact: true }).click()
  await expect(scenario.getByText(/저장 완료/)).toBeVisible({ timeout: 30_000 })
  for (const asset of storybooks) {
    await expect(scenario.getByText(`${basename(asset.path)} · ${asset.role}`, { exact: true })).toBeVisible()
  }
})
