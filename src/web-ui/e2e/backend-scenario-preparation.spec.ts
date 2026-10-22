import { expect, test } from '@playwright/test'
import { bootstrapAdventureSession, createDirectApi, explorationGroupLearnedAbilityIds,
  explorationGroupStorybooks, hasRuntimeCredentials } from './support/runtime-adventure'

test('A Most Potent Brew compiles and reaches adventure preparation', async () => {
  test.skip(!hasRuntimeCredentials(), '실제 백엔드 로그인 정보가 필요합니다')
  test.setTimeout(20 * 60_000)

  const session = await bootstrapAdventureSession(createDirectApi(), 2,
    explorationGroupStorybooks, explorationGroupLearnedAbilityIds)

  expect(session.sessionId).toMatch(/^[0-9a-f-]{36}$/i)
  expect(session.packageId).toMatch(/^[0-9a-f-]{36}$/i)
  expect(session.mapDocumentId).toMatch(/^[0-9a-f-]{36}$/i)
  console.info('[scenario-preparation-e2e]', JSON.stringify({
    result: 'adventure_prepared', sessionId: session.sessionId, packageId: session.packageId,
    sourceDocumentCount: explorationGroupStorybooks.length, partySize: 2,
  }))
})
