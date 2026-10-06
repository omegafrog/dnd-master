import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { execFile } from 'node:child_process'
import { readFile, writeFile } from 'node:fs/promises'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const fixtureTurnCountFile = process.env.CODEX_E2E_TURN_COUNT_FILE
const fixtureExecutable = new URL('./fixtures/codex-app-server-ai-reauth.sh', import.meta.url).pathname
const repositoryRoot = new URL('../../..', import.meta.url).pathname
const composeFile = `${repositoryRoot}src/infra/compose.yaml`

test('연결된 Codex 실행과 연결 해제 차단, 재인증 뒤 수동 새 요청을 확인한다', async ({ page, request }) => {
  test.skip(!backend || !email || !password || !fixtureTurnCountFile,
    'src/start-dev.sh와 Codex 테스트 fixture 경로를 제공하는 실행 환경이 필요합니다')
  test.setTimeout(90_000)
  expect(process.env.CODEX_EXECUTABLE, 'start-dev.sh must use the test-only Codex app-server fixture')
    .toBe(fixtureExecutable)
  await writeFile(fixtureTurnCountFile!, '0\n')
  let uiTurnPostCount = 0
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/turns')) uiTurnPostCount += 1
  })

  const auth = await login(request)
  const seed = await seedAdventure(auth.playerId)
  try {
    await request.delete(`${backend}/api/v1/profile/codex-connection`, {
      headers: { Authorization: `Bearer ${auth.token}` },
    }).then(async response => expect(response.ok(), await response.text()).toBeTruthy())

    await loginThroughUi(page)
    await connect(page)
    const sessionView = await request.get(`${backend}/api/v1/adventure-sessions/${seed.sessionId}`, {
      headers: { Authorization: `Bearer ${auth.token}` },
    })
    expect(sessionView.ok(), `session read failed: ${sessionView.status()} ${await sessionView.text()}`).toBeTruthy()
    expect(await sessionView.json()).toMatchObject({ adventureId: seed.adventureId, status: 'STARTED' })
    const conversationView = await request.get(`${backend}/api/v1/adventures/${seed.adventureId}/conversation`, {
      headers: { Authorization: `Bearer ${auth.token}` },
    })
    expect(conversationView.ok(), `conversation read failed: ${conversationView.status()} ${await conversationView.text()}`).toBeTruthy()
    await page.goto(`/#/sessions/${seed.sessionId}?mode=play`)
    await expect(page.getByLabel('무엇을 하시겠어요?')).toBeEnabled({ timeout: 15_000 })
    await sendAction(page, '첫 번째 연결된 행동')
    await expect(page.getByText('숲길에서 발자국을 발견했습니다.')).toBeVisible()
    await expect.poll(readTurnCount).toBe(1)
    expect(uiTurnPostCount).toBe(1)

    const disconnected = await request.delete(`${backend}/api/v1/profile/codex-connection`, {
      headers: { Authorization: `Bearer ${auth.token}` },
    })
    expect(disconnected.ok(), await disconnected.text()).toBeTruthy()
    await page.goto('/#/profile')
    await expect(page.getByText('이 설치는 Codex 계정에 연결되지 않았습니다.')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toBeVisible()
    const blocked = await submitTurn(request, auth.token, seed.adventureId, 1, '연결 해제 상태의 행동')
    expect(blocked.status()).toBe(502)
    const blockedBody = await blocked.json() as { message?: string }
    expect(blockedBody.message).toBeTruthy()
    expect(await readTurnCount()).toBe(1)

    await page.goto('/#/profile')
    await connect(page)
    await page.goto(`/#/sessions/${seed.sessionId}?mode=play`)
    const turnRequestsBeforeAuthRejection = uiTurnPostCount
    await sendAction(page, '인증 만료가 발생하는 행동')
    await expect.poll(readTurnCount).toBe(2)
    expect(uiTurnPostCount - turnRequestsBeforeAuthRejection).toBe(1)

    await page.goto('/#/profile')
    await expect(page.getByText('Codex 계정을 다시 인증해야 합니다.')).toBeVisible()
    await page.getByRole('button', { name: 'Codex 계정 다시 인증' }).click()
    await expect(page.getByText('Codex 계정이 연결되었습니다.').first()).toBeVisible({ timeout: 15_000 })

    await page.goto(`/#/sessions/${seed.sessionId}?mode=play`)
    await expect(page.getByLabel('무엇을 하시겠어요?')).toBeEnabled({ timeout: 15_000 })
    const turnRequestsBeforeManualRetry = uiTurnPostCount
    await sendAction(page, '재인증 뒤 새로 보낸 행동')
    await expect.poll(() => uiTurnPostCount).toBe(turnRequestsBeforeManualRetry + 1)
    await expect.poll(readTurnCount).toBe(3)
    await expect(page.getByText('숲길에서 발자국을 발견했습니다.').last()).toBeVisible()
  } finally {
    await cleanupAdventure(seed)
  }
})

async function login(request: APIRequestContext) {
  const response = await request.post(`${backend}/api/v1/auth/login`, { data: { username: email, password } })
  expect(response.ok(), await response.text()).toBeTruthy()
  const session = await response.json() as { token?: string; playerId?: string }
  expect(session.token).toBeTruthy()
  expect(session.playerId).toBeTruthy()
  return { token: session.token!, playerId: session.playerId! }
}

async function loginThroughUi(page: Page) {
  await page.goto('/#/login')
  await page.getByLabel('이메일').fill(email!)
  await page.getByLabel('비밀번호').fill(password!)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toBeVisible()
}

async function connect(page: Page) {
  const connected = page.waitForResponse(response =>
    response.url().endsWith('/api/v1/profile/codex-connection/operations') && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Codex 계정 연결', exact: true }).click()
  expect(await (await connected).json()).toMatchObject({ status: 'CONNECTED', pending: false })
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toBeVisible()
}

async function sendAction(page: Page, action: string) {
  await page.getByLabel('무엇을 하시겠어요?').fill(action)
  await page.getByRole('button', { name: '행동 보내기' }).click()
}

async function submitTurn(request: APIRequestContext, token: string, adventureId: string, version: number, text: string) {
  return request.post(`${backend}/api/v1/adventures/${adventureId}/turns`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Idempotency-Key': crypto.randomUUID(),
      'If-Match-Version': String(version),
    },
    data: { turnId: crypto.randomUUID(), input: { type: 'TEXT', text } },
  })
}

async function readTurnCount() {
  return Number((await readFile(fixtureTurnCountFile!, 'utf8')).trim() || '0')
}

async function seedAdventure(ownerPlayerId: string) {
  const adventureId = crypto.randomUUID()
  const sessionId = crypto.randomUUID()
  const characterSheetId = crypto.randomUUID()
  const bundleId = crypto.randomUUID()
  const packageId = crypto.randomUUID()
  const documentId = crypto.randomUUID()
  const scenarioId = crypto.randomUUID()
  const ruleSetId = crypto.randomUUID()
  const party = JSON.stringify([{
    characterSheetId: { value: characterSheetId }, controlMode: 'DIRECT',
    nameMutableAfterStart: false, raceMutableAfterStart: false, characterClassMutableAfterStart: false,
    backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false,
  }]).replace(/'/g, "''")
  await executeSql(`
    BEGIN;
    INSERT INTO rulebook_registration(rulebook_id, owner_player_id, operation_key, content_hash, format,
      file_size, storage_key, processing_status, extraction_status, extracted_content, document_type, original_filename, version)
      VALUES ('${documentId}'::uuid, '${ownerPlayerId}'::uuid, 'codex-e2e-${documentId}', 'codex-e2e-${documentId}',
      'TXT', 1, 'codex-e2e/${documentId}', 'INDEXED', 'SUCCESS',
      'A quiet path through the woods. Footprints cross the trail.', 'STORYBOOK', 'codex-fixture.txt', 1);
    INSERT INTO scenario_source_bundle(bundle_id, owner_player_id, current_revision, name, rulebook_edition)
      VALUES ('${bundleId}'::uuid, '${ownerPlayerId}'::uuid, 1, 'Codex E2E fixture', 'DND_5E_2014');
    INSERT INTO scenario_source_bundle_revision(bundle_id, revision_number)
      VALUES ('${bundleId}'::uuid, 1);
    INSERT INTO scenario_source_bundle_revision_document(bundle_id, revision_number, selection_order,
      knowledge_document_id, document_type, original_filename, document_role, knowledge_document_status, extraction_version)
      VALUES ('${bundleId}'::uuid, 1, 0, '${documentId}'::uuid, 'STORYBOOK', 'codex-fixture.txt',
      'MAIN_SCENARIO', 'EXTRACTED', 1);
    INSERT INTO scenario_package(package_id, bundle_id, bundle_revision, input_fingerprint,
      report_status, scenario_model_json)
      VALUES ('${packageId}'::uuid, '${bundleId}'::uuid, 1, 'codex-e2e-${packageId}', 'COMPLETE',
        '{"schemaVersion":1,"actors":[],"locations":[],"objectives":[],"revelations":[],"encounters":[],"relationships":[],"resolutionCriteria":[],"startingSituation":"A quiet path through the woods."}'::jsonb);
    INSERT INTO scenario_package_document(package_id, selection_order, knowledge_document_id, document_type,
      original_filename, document_role, knowledge_document_status, extraction_version)
      VALUES ('${packageId}'::uuid, 0, '${documentId}'::uuid, 'STORYBOOK', 'codex-fixture.txt',
      'MAIN_SCENARIO', 'EXTRACTED', 1);
    INSERT INTO adventure_session(session_id, owner_player_id, scenario_package_id, scenario_package_revision,
      blueprint_id, blueprint_revision, character_edition, character_limit, status, started_adventure_id, version)
      VALUES ('${sessionId}'::uuid, '${ownerPlayerId}'::uuid, '${packageId}'::uuid, 1,
      '${packageId}'::uuid, 1, 'DND_5E_2014', 1, 'STARTED', '${adventureId}'::uuid, 0);
    INSERT INTO adventure_session_party_member(session_id, character_sheet_id, control_mode,
      name_mutable_after_start, race_mutable_after_start, class_mutable_after_start,
      background_mutable_after_start, abilities_mutable_after_start, level_mutable_after_start)
      VALUES ('${sessionId}'::uuid, '${characterSheetId}'::uuid, 'DIRECT', false, false, false, false, false, false);
    INSERT INTO character_management.character_sheet(character_sheet_id, adventure_id, session_id, owner_player_id,
      edition, character_name, character_level, inspiration, version)
      VALUES ('${characterSheetId}'::uuid, '${adventureId}'::uuid, '${sessionId}'::uuid,
      '${ownerPlayerId}'::uuid, 'DND_5E_2014', 'Codex 테스트 캐릭터', 1, false, 0);
    INSERT INTO adventure(adventure_id, session_id, owner_player_id, scenario_id, rule_set_id, current_scene,
      status, version, party_json, locked_scenario_package_id, locked_scenario_package_revision,
      game_state_jsonb, current_situation_id, situation_revision, current_situation_jsonb)
      VALUES ('${adventureId}'::uuid, '${sessionId}'::uuid, '${ownerPlayerId}'::uuid, '${scenarioId}'::uuid,
      '${ruleSetId}'::uuid, 'A quiet path through the woods.', 'ACTIVE', 0, '${party}', '${packageId}'::uuid, 1,
      '{"values":{},"revision":0}'::jsonb, '${scenarioId}'::uuid, 1,
      '{"situationId":"${scenarioId}","revision":1,"location":"숲길","problem":"흔적을 찾습니다.","threat":"아직 확인되지 않았습니다.","goal":"발자국을 찾습니다."}'::jsonb);
    INSERT INTO adventure_runtime_binding(adventure_id, binding_version, owner_player_id, scenario_package_id,
      scenario_package_revision, rulebook_ids_json, party_json, engine_id, tool_ids_json, playability_status,
      playability_warnings_json, playability_blockers_json, playability_limits_json, source_context_candidates_json)
      VALUES ('${adventureId}'::uuid, 1, '${ownerPlayerId}'::uuid, '${packageId}'::uuid, 1,
      '[]', '${party}', 'codex-cli', '[]', 'PLAYABLE', '[]', '[]', '[]', '[]');
    COMMIT;
  `)
  return { adventureId, sessionId, bundleId, packageId, documentId }
}

async function cleanupAdventure(seed: Awaited<ReturnType<typeof seedAdventure>>) {
  await executeSql(`
    BEGIN;
    DELETE FROM adventure WHERE adventure_id = '${seed.adventureId}'::uuid;
    DELETE FROM character_management.character_sheet WHERE adventure_id = '${seed.adventureId}'::uuid;
    DELETE FROM adventure_session WHERE session_id = '${seed.sessionId}'::uuid;
    DELETE FROM scenario_package WHERE package_id = '${seed.packageId}'::uuid;
    DELETE FROM scenario_source_bundle WHERE bundle_id = '${seed.bundleId}'::uuid;
    COMMIT;
  `)
}

async function executeSql(sql: string) {
  await psql(sql)
}

async function psql(sql: string) {
  return execFileAsync('docker', [
    'compose', '-f', composeFile, 'exec', '-T', 'postgres',
    'psql', '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align',
    '--set', 'ON_ERROR_STOP=1', '--command', sql,
  ])
}
