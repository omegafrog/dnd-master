import { expect, test, type APIRequestContext } from '@playwright/test'

const backend = process.env.BACKEND_E2E_URL
const sessionId = process.env.BACKEND_E2E_SESSION_ID
const ownerPlayerId = process.env.BACKEND_E2E_PLAYER_ID
const token = process.env.BACKEND_E2E_TOKEN ?? ''

const authHeaders = { Authorization: `Bearer ${token}` }

function requireEnvironment() {
  test.skip(
    !backend || !sessionId || !ownerPlayerId,
    'set BACKEND_E2E_URL, BACKEND_E2E_SESSION_ID, and BACKEND_E2E_PLAYER_ID against a running backend',
  )
}

function fighterDraft(characterName: string) {
  const equippedItems = {
    armor: '',
    shield: false,
    mainHandWeaponId: null,
    offHandWeaponId: null,
    twoHandedWeaponId: null,
  }
  return {
    ownerPlayerId,
    edition: 'DND_5E_2014',
    characterName,
    level: 1,
    inspiration: false,
    race: '인간',
    characterClass: '파이터',
    background: '현자',
    startingAbilities: 'strength=15,dexterity=14,constitution=13,intelligence=12,wisdom=10,charisma=8',
    // The backend must ignore this forged value and persist its own derivation.
    derivedStatistics: JSON.stringify({ armorClass: 999, hitPointMaximum: 999 }),
    characterBuild: JSON.stringify({
      schemaVersion: 2,
      rulesetRevision: 1,
      subrace: '',
      subclass: '',
      skillProficiencies: ['운동', '지각'],
      expertise: [],
      equipmentSelections: { weapon: 'default' },
      ruleChoices: {},
      ownedEquipment: [],
      ownedWeaponIds: [],
      equippedItems,
      cantrips: [],
      learnedOrPreparedSpells: [],
      armorProficiencies: ['모든 갑옷', '방패'],
      weaponProficiencies: ['단순 무기', '군용 무기'],
    }),
    characterState: JSON.stringify({
      currentHitPoints: 1,
      temporaryHitPoints: 0,
      experience: 0,
      equippedItems,
      ammunition: {},
      spellSlots: [],
    }),
    blueprintValues: {},
  }
}

async function createCharacter(request: APIRequestContext, characterName: string) {
  const response = await request.post(
    `${backend}/internal/v1/adventure-sessions/${sessionId}/character-sheets`,
    { headers: authHeaders, data: fighterDraft(characterName) },
  )
  expect(response.ok(), await response.text()).toBeTruthy()
  return response.json() as Promise<{ characterSheetId: string; edition: string; characterName: string; version: number }>
}

test.describe('real backend D&D 5e 2014 character flow', () => {
  test('catalog and evaluation expose authoritative server rules', async ({ request }) => {
    requireEnvironment()

    const catalogResponse = await request.get(
      `${backend}/internal/v1/character-rules/catalogs/DND_5E_2014`,
      { headers: authHeaders },
    )
    expect(catalogResponse.ok(), await catalogResponse.text()).toBeTruthy()
    const catalog = await catalogResponse.json()
    expect(catalog.baseSchema).toBe('DND_5E_2014')
    expect(catalog.classes).toContain('파이터')
    expect(catalog.races).toContain('인간')

    const evaluationResponse = await request.post(
      `${backend}/internal/v1/adventure-sessions/${sessionId}/character-builds/evaluate`,
      { headers: authHeaders, data: fighterDraft(`평가-${Date.now()}`) },
    )
    expect(evaluationResponse.ok(), await evaluationResponse.text()).toBeTruthy()
    const evaluation = await evaluationResponse.json()
    expect(evaluation.valid).toBe(true)
    expect(evaluation.violations).toEqual([])
    expect(evaluation.derived.proficiencyBonus).toBe(2)
    expect(evaluation.derived.armorClass).not.toBe(999)
    expect(evaluation.derived.hitPointMaximum).not.toBe(999)
    expect(evaluation.derived.attacks).toEqual(expect.arrayContaining([
      expect.objectContaining({ weaponId: 'unarmed', mode: 'UNARMED' }),
    ]))
  })

  test('create, read back, and add the character to the party', async ({ request }) => {
    requireEnvironment()
    const characterName = `Playwright 파이터 ${Date.now()}`
    const created = await createCharacter(request, characterName)

    expect(created.edition).toBe('DND_5E_2014')
    expect(created.characterName).toBe(characterName)

    const readResponse = await request.get(
      `${backend}/internal/v1/character-sheets/${created.characterSheetId}?edition=DND_5E_2014`,
      { headers: authHeaders },
    )
    expect(readResponse.ok(), await readResponse.text()).toBeTruthy()
    const stored = await readResponse.json()
    expect(stored.characterSheetId).toBe(created.characterSheetId)
    expect(stored.characterName).toBe(characterName)

    const storedDerived = JSON.parse(stored.derivedStatistics)
    expect(storedDerived.armorClass).not.toBe(999)
    expect(storedDerived.hitPointMaximum).not.toBe(999)

    const sessionResponse = await request.get(
      `${backend}/api/v1/adventure-sessions/${sessionId}`,
      { headers: authHeaders },
    )
    expect(sessionResponse.ok(), await sessionResponse.text()).toBeTruthy()
    const session = await sessionResponse.json()

    const partyResponse = await request.post(
      `${backend}/api/v1/adventure-sessions/${sessionId}/party`,
      {
        headers: { ...authHeaders, 'If-Match-Version': String(session.version) },
        data: {
          characterSheetId: created.characterSheetId,
          controlMode: 'DIRECT',
          nameMutableAfterStart: false,
          raceMutableAfterStart: false,
          characterClassMutableAfterStart: false,
          backgroundMutableAfterStart: false,
          startingAbilitiesMutableAfterStart: false,
          levelMutableAfterStart: false,
        },
      },
    )
    expect(partyResponse.ok(), await partyResponse.text()).toBeTruthy()
    const updatedSession = await partyResponse.json()
    expect(updatedSession.party).toEqual(expect.arrayContaining([
      expect.objectContaining({ characterSheetId: created.characterSheetId, controlMode: 'DIRECT' }),
    ]))
  })

  test('rejects a druid attempting to equip metal armor', async ({ request }) => {
    requireEnvironment()
    const draft = fighterDraft(`드루이드 거부 ${Date.now()}`)
    const equippedItems = {
      armor: '스케일 메일',
      shield: false,
      mainHandWeaponId: null,
      offHandWeaponId: null,
      twoHandedWeaponId: null,
    }
    draft.characterClass = '드루이드'
    draft.characterBuild = JSON.stringify({
      schemaVersion: 2,
      rulesetRevision: 1,
      subrace: '',
      subclass: '',
      skillProficiencies: ['자연', '지각'],
      expertise: [],
      equipmentSelections: { armor: 'scale' },
      ruleChoices: {},
      ownedEquipment: ['스케일 메일'],
      ownedWeaponIds: [],
      equippedItems,
      cantrips: ['가이던스', '셸릴리'],
      learnedOrPreparedSpells: ['얽힘'],
      armorProficiencies: ['비금속 경갑', '비금속 평갑', '비금속 방패'],
      weaponProficiencies: ['드루이드 무기'],
    })
    draft.characterState = JSON.stringify({
      currentHitPoints: 1,
      temporaryHitPoints: 0,
      experience: 0,
      equippedItems,
      ammunition: {},
      spellSlots: [{ level: 1, maximum: 2, remaining: 2, recovery: 'LONG_REST' }],
    })

    const response = await request.post(
      `${backend}/internal/v1/adventure-sessions/${sessionId}/character-builds/evaluate`,
      { headers: authHeaders, data: draft },
    )
    expect(response.ok(), await response.text()).toBeTruthy()
    const evaluation = await response.json()
    expect(evaluation.valid).toBe(false)
    expect(evaluation.violations).toEqual(expect.arrayContaining([
      expect.objectContaining({ code: 'DRUID_METAL_ARMOR_RESTRICTION' }),
    ]))
  })
})
