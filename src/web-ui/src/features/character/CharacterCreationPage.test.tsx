import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CharacterCreationPage } from './CharacterCreationPage'

const preparation = { scenarioPackageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, status: 'READY', blockers: [], characterLimit: { maximumCharacters: 2, source: null, sourceQuote: '' }, characterCreationBlueprint: { available: true, summary: 'published', rulebookDocumentCount: 1, storybookDocumentCount: 0, diagnostics: [], revision: 4, status: 'PUBLISHED', fields: [] } }
const session = { sessionId: 'session-1', scenarioPackageId: 'package-1', blueprintRevision: 4, characterLimit: 2, version: 0, status: 'DRAFT', party: [], adventureId: null, runtimeConfiguration: null }

const catalog = {
  edition: 'DND_5E_2014', baseSchema: 'DND_5E_2014', revision: 1,
  races: ['드워프', '엘프', '하플링', '인간'],
  classes: ['바바리안', '바드', '클레릭', '드루이드', '파이터', '몽크', '팔라딘', '레인저', '로그', '소서러', '워락', '위저드'],
  backgrounds: ['수행사제', '사기꾼', '범죄자', '연예인', '민중 영웅', '길드 장인', '은둔자', '귀족', '이방인', '현자', '선원', '군인', '부랑아'],
}

const evaluation = {
  valid: true,
  derived: {
    proficiencyBonus: 2, armorClass: 17, hitPointMaximum: 10, passivePerception: 12,
    savingThrowBonuses: { strength: 4, dexterity: 2, constitution: 3, intelligence: 0, wisdom: 1, charisma: 0 },
    skillBonuses: { 지각: { proficient: true, expertise: false, bonus: 2 } },
    attacks: [
      { weaponId: 'rapier', label: '레이피어', attackBonus: 4, damage: '1d8+2', damageType: '관통', mode: 'MELEE', ammunitionRequired: false },
      { weaponId: 'unarmed', label: '비무장 공격', attackBonus: 3, damage: '1+1', damageType: '타격', mode: 'UNARMED', ammunitionRequired: false },
    ],
    spellAttackBonus: 4, spellSaveDc: 12,
  },
  violations: [],
}

function fixture() {
  const createCharacterSheet = vi.fn().mockResolvedValue({ characterSheetId: 'sheet-1', adventureId: 'adventure-1', edition: 'DND_5E_2014', characterName: '아리아', level: 1, inspiration: false, version: 0 })
  return {
    setupApi: { getPlayPreparation: vi.fn().mockResolvedValue(preparation), createCharacterSheet },
    sessionApi: { read: vi.fn().mockResolvedValue(session), addMember: vi.fn().mockResolvedValue({ ...session, version: 1, party: [{ characterSheetId: 'sheet-1' }] }) },
    createCharacterSheet,
  }
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    const body = url.includes('/character-rules/catalogs/') ? catalog : evaluation
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }))
})

afterEach(() => vi.unstubAllGlobals())

describe('CharacterCreationPage', () => {
  it('서버 카탈로그 revision과 자동값을 보여준다', async () => {
    const { setupApi, sessionApi } = fixture()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    expect(await screen.findByText(/규칙 카탈로그:/)).toHaveTextContent('revision 1')
    const automaticValues = await screen.findByText(/레벨:/)
    expect(automaticValues.textContent).toContain('1')
    expect(automaticValues.textContent).toContain('경험치: 0')
    expect(automaticValues.textContent).toContain('숙련 보너스: +2')
    expect(screen.queryByLabelText('캐릭터 레벨')).toBeNull()
  })

  it('선택한 종족에 속한 하위 종족만 보여주고 인간은 하위 종족을 숨긴다', async () => {
    const { setupApi, sessionApi } = fixture(); const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('종족'), '엘프')
    const subrace = screen.getByLabelText('하위 종족') as HTMLSelectElement
    expect(Array.from(subrace.options).map(option => option.textContent)).toContain('하이 엘프')
    expect(Array.from(subrace.options).map(option => option.textContent)).not.toContain('언덕 드워프')
    await user.selectOptions(screen.getByLabelText('종족'), '인간')
    expect(screen.queryByLabelText('하위 종족')).toBeNull()
  })

  it('표준 배열의 이미 사용한 값을 다른 능력치에서 비활성화한다', async () => {
    const { setupApi, sessionApi } = fixture(); const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('근력'), '15')
    const dexterity = screen.getByLabelText('민첩') as HTMLSelectElement
    expect(Array.from(dexterity.options).find(option => option.value === '15')?.disabled).toBe(true)
  })

  it('1레벨에 하위 클래스를 정하는 클래스만 하위 클래스 선택을 표시한다', async () => {
    const { setupApi, sessionApi } = fixture(); const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('클래스'), '클레릭')
    expect(Array.from((screen.getByLabelText('하위 클래스') as HTMLSelectElement).options).map(option => option.textContent)).toContain('생명 권역')
    await user.selectOptions(screen.getByLabelText('클래스'), '파이터')
    expect(screen.queryByLabelText('하위 클래스')).toBeNull()
  })

  it('서버 평가 결과를 공격 및 파생 미리보기에 우선 적용한다', async () => {
    const { setupApi, sessionApi } = fixture(); const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('종족'), '인간')
    await user.selectOptions(screen.getByLabelText('클래스'), '파이터')
    expect((await screen.findByLabelText('공격 목록')).textContent).toContain('레이피어')
    expect(screen.getByText(/방어도/).textContent).toContain('17')
  })

  it('로그는 숙련 기술 중 두 개의 숙달을 선택한다', async () => {
    const { setupApi, sessionApi } = fixture(); const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('클래스'), '로그')
    await user.click(screen.getByLabelText('곡예')); await user.click(screen.getByLabelText('은신')); await user.click(screen.getByLabelText('지각')); await user.click(screen.getByLabelText('수사'))
    const expertiseGroup = screen.getByText('숙달 2개 선택').closest('fieldset')
    expect(expertiseGroup?.textContent).toContain('곡예')
    expect(expertiseGroup?.textContent).not.toContain('종교')
  })

  it('기술 보너스와 수동 지각 및 주문 슬롯을 표시한다', async () => {
    const { setupApi, sessionApi } = fixture(); const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('종족'), '인간')
    await user.selectOptions(screen.getByLabelText('클래스'), '위저드')
    expect(screen.getByLabelText('기술 보너스').textContent).toContain('지각')
    expect(screen.getByText(/수동 지각/).textContent).toContain('12')
    expect(screen.getByText(/1레벨 슬롯/).textContent).toContain('2')
  })
})
