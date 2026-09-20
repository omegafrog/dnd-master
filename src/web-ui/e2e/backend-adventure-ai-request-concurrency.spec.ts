import { expect, test, type APIRequestContext } from '@playwright/test'
import { execFile, spawn } from 'node:child_process'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const backend = process.env.BACKEND_E2E_URL
const email = process.env.BACKEND_E2E_EMAIL
const password = process.env.BACKEND_E2E_PASSWORD
const repositoryRoot = new URL('../../..', import.meta.url).pathname
const composeFile = `${repositoryRoot}src/infra/compose.yaml`

type Seed = { adventureId: string; sessionId: string; commandIds: [string, string] }
type ConcurrentResponse = { commandId: string; response: Awaited<ReturnType<APIRequestContext['post']>> }

test('로그인한 Solo Player의 동시 모험 행동은 수락된 요청만 저장한다', async ({ request }) => {
  test.skip(!backend || !email || !password,
    'src/start-dev.sh가 제공하는 BACKEND_E2E_URL, BACKEND_E2E_EMAIL, BACKEND_E2E_PASSWORD가 필요합니다')
  test.setTimeout(30_000)

  const auth = await login(request)
  const seed = await seedStartedAdventure(auth.playerId)
  try {
    // 세션이 요청 하나를 수락한 직후 멈춰, 다른 동시 HTTP 행동이
    // 이후 처리 전에 진행 중인 요청을 확실히 보게 한다.
    const turnWriteLock = await holdGmTurnWriteLock()

    const responses: ConcurrentResponse[] = await Promise.all(seed.commandIds.map(async (commandId, index) => ({
      commandId,
      response: await request.post(`${backend}/api/v1/adventures/${seed.adventureId}/turns`, {
        headers: {
          Authorization: `Bearer ${auth.token}`,
          'Content-Type': 'application/json',
          'Idempotency-Key': commandId,
          'If-Match-Version': '0',
        },
        data: {
          turnId: crypto.randomUUID(),
          input: { type: 'TEXT', text: `동시 행동 ${index + 1}` },
        },
      }),
    })))
    await turnWriteLock

    const responseDetails = await Promise.all(responses.map(async ({ commandId, response }) =>
      `${commandId}: ${response.status()} ${await response.text()}`,
    ))
    const rejected = responses.filter(({ response }) => response.status() === 409)
    const accepted = responses.filter(({ response }) => response.status() !== 409)
    expect(rejected, responseDetails.join('\n')).toHaveLength(1)
    expect(accepted, responseDetails.join('\n')).toHaveLength(1)
    // 이 최소 모험에는 실행 결합을 만들지 않는다. 따라서 세션 수락 뒤
    // 실행 결합 부재로 반환되는 502만 수락된 요청의 허용된 최종 응답이다.
    expect(accepted[0].response.status(), responseDetails.join('\n')).toBe(502)

    const persistedCommandIds = await queryRows(
      `SELECT command_id::text FROM adventure_gm_turn
       WHERE adventure_id = '${seed.adventureId}'::uuid
         AND command_id IN ('${seed.commandIds[0]}'::uuid, '${seed.commandIds[1]}'::uuid)
       ORDER BY command_id`,
    )
    expect(persistedCommandIds).toEqual([accepted[0].commandId])

    const rejectedCommandId = rejected[0].commandId
    expect(await queryRows(
      `SELECT command_id::text FROM adventure_runtime_turn WHERE command_id = '${rejectedCommandId}'::uuid
       UNION ALL
       SELECT command_id::text FROM adventure_runtime_command_journal WHERE command_id = '${rejectedCommandId}'::uuid
       UNION ALL
       SELECT command_id::text FROM adventure_runtime_turn_command WHERE command_id = '${rejectedCommandId}'::uuid`,
    )).toEqual([])

    expect(await queryRows(
      `SELECT COALESCE(active_ai_request_id::text, '') || '|' || version::text FROM adventure_session
       WHERE session_id = '${seed.sessionId}'::uuid`,
    )).toEqual(['|2'])
  } finally {
    await executeSql(`
      BEGIN;
      DELETE FROM adventure WHERE adventure_id = '${seed.adventureId}'::uuid;
      DELETE FROM adventure_session WHERE session_id = '${seed.sessionId}'::uuid;
      COMMIT;
    `)
  }
})

async function login(request: APIRequestContext) {
  const response = await request.post(`${backend}/api/v1/auth/login`, {
    data: { username: email, password },
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  const session = await response.json() as { token?: string; playerId?: string }
  expect(session.token, '로그인 응답에 토큰이 없습니다').toBeTruthy()
  expect(session.playerId, '로그인 응답에 플레이어 ID가 없습니다').toBeTruthy()
  return { token: session.token!, playerId: session.playerId! }
}

async function seedStartedAdventure(ownerPlayerId: string): Promise<Seed> {
  const adventureId = crypto.randomUUID()
  const sessionId = crypto.randomUUID()
  const scenarioId = crypto.randomUUID()
  const ruleSetId = crypto.randomUUID()
  const scenarioPackageId = crypto.randomUUID()
  const characterSheetId = crypto.randomUUID()
  const party = JSON.stringify([{
    characterSheetId: { value: characterSheetId }, controlMode: 'DIRECT',
    nameMutableAfterStart: true, raceMutableAfterStart: true, characterClassMutableAfterStart: true,
    backgroundMutableAfterStart: true, startingAbilitiesMutableAfterStart: true, levelMutableAfterStart: true,
  }]).replace(/'/g, "''")

  await executeSql(`
    BEGIN;
    INSERT INTO adventure_session (
      session_id, owner_player_id, scenario_package_id, scenario_package_revision,
      blueprint_id, blueprint_revision, character_edition, character_limit,
      status, started_adventure_id, version
    ) VALUES (
      '${sessionId}'::uuid, '${ownerPlayerId}'::uuid, '${scenarioPackageId}'::uuid, 1,
      '${scenarioPackageId}'::uuid, 1, 'DND_5E_2014', 1,
      'STARTED', '${adventureId}'::uuid, 0
    );
    INSERT INTO adventure (
      adventure_id, session_id, owner_player_id, scenario_id, rule_set_id,
      current_scene, status, version, party_json, game_state_jsonb
    ) VALUES (
      '${adventureId}'::uuid, '${sessionId}'::uuid, '${ownerPlayerId}'::uuid, '${scenarioId}'::uuid, '${ruleSetId}'::uuid,
      '동시 요청 검증', 'SAVED', 0, '${party}', '{"values":{},"revision":0}'::jsonb
    );
    COMMIT;
  `)
  return { adventureId, sessionId, commandIds: [crypto.randomUUID(), crypto.randomUUID()] }
}

async function holdGmTurnWriteLock() {
  const child = spawn('docker', [
    'compose', '-f', composeFile, 'exec', '-T', 'postgres',
    'psql', '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align',
    '--set', 'ON_ERROR_STOP=1', '--command', `
      BEGIN;
      LOCK TABLE adventure_gm_turn IN SHARE ROW EXCLUSIVE MODE;
      SELECT 'GM_TURN_WRITE_LOCKED';
      SELECT pg_sleep(2);
      COMMIT;
    `,
  ])
  let output = ''
  const locked = new Promise<void>((resolve, reject) => {
    child.stdout.on('data', chunk => {
      output += chunk.toString()
      if (output.includes('GM_TURN_WRITE_LOCKED')) resolve()
    })
    child.once('error', reject)
    child.once('exit', code => {
      if (!output.includes('GM_TURN_WRITE_LOCKED')) {
        reject(new Error(`GM turn write lock exited before acquisition (code ${code})`))
      }
    })
  })
  const completed = new Promise<void>((resolve, reject) => {
    let stderr = ''
    child.stderr.on('data', chunk => { stderr += chunk.toString() })
    child.once('exit', code => {
      if (code === 0) resolve()
      else reject(new Error(`GM turn write lock failed (code ${code}): ${stderr}`))
    })
  })
  await locked
  return completed
}

async function queryRows(sql: string) {
  const output = await psql(sql)
  return output.split('\n').map(row => row.trim()).filter(Boolean)
}

async function executeSql(sql: string) {
  await psql(sql)
}

async function psql(sql: string) {
  const { stdout } = await execFileAsync('docker', [
    'compose', '-f', composeFile, 'exec', '-T', 'postgres',
    'psql', '--username', 'postgres', '--dbname', 'postgres', '--tuples-only', '--no-align',
    '--set', 'ON_ERROR_STOP=1', '--command', sql,
  ])
  return stdout
}
