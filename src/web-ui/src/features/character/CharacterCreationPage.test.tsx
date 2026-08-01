import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterCreationPage } from './CharacterCreationPage'

const preparation = { scenarioPackageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, status: 'READY', blockers: [], characterLimit: { maximumCharacters: 2, source: null, sourceQuote: '' }, characterCreationBlueprint: { available: true, summary: 'published', rulebookDocumentCount: 1, storybookDocumentCount: 0, diagnostics: [], revision: 4, status: 'PUBLISHED', fields: [] } }
const session = { sessionId: 'session-1', scenarioPackageId: 'package-1', blueprintRevision: 4, characterLimit: 2, version: 0, status: 'DRAFT', party: [], adventureId: null, runtimeConfiguration: null }

function fixture() {
  const createCharacterSheet = vi.fn().mockResolvedValue({ characterSheetId: 'sheet-1', adventureId: 'adventure-1', edition: 'DND_5E_2014', characterName: '아리아', level: 1, inspiration: false, version: 0 })
  return {
    setupApi: { getPlayPreparation: vi.fn().mockResolvedValue(preparation), createCharacterSheet },
    sessionApi: { read: vi.fn().mockResolvedValue(session), addMember: vi.fn() },
    createCharacterSheet,
  }
}

describe('CharacterCreationPage', () => {
  it('레벨, 경험치와 숙련 보너스를 자동값으로 보여준다', async () => {
    const { setupApi, sessionApi } = fixture()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    const automaticValues = await screen.findByText(/레벨:/)
    expect(automaticValues.textContent).toContain('1')
    expect(automaticValues.textContent).toContain('경험치: 0')
    expect(automaticValues.textContent).toContain('숙련 보너스: +2')
    expect(screen.queryByLabelText('캐릭터 레벨')).toBeNull()
  })

  it('선택한 종족에 속한 하위 종족만 보여주고 인간은 하위 종족을 숨긴다', async () => {
    const { setupApi, sessionApi } = fixture()
    const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('종족'), '엘프')
    const subrace = screen.getByLabelText('하위 종족') as HTMLSelectElement
    expect(Array.from(subrace.options).map(option => option.textContent)).toContain('하이 엘프')
    expect(Array.from(subrace.options).map(option => option.textContent)).not.toContain('언덕 드워프')
    await user.selectOptions(screen.getByLabelText('종족'), '인간')
    expect(screen.queryByLabelText('하위 종족')).toBeNull()
  })

  it('표준 배열의 이미 사용한 값을 다른 능력치에서 비활성화한다', async () => {
    const { setupApi, sessionApi } = fixture()
    const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('근력'), '15')
    const dexterity = screen.getByLabelText('민첩') as HTMLSelectElement
    const value15 = Array.from(dexterity.options).find(option => option.value === '15')
    expect(value15?.disabled).toBe(true)
  })

  it('1레벨에 하위 클래스를 정하는 클래스만 하위 클래스 선택을 표시한다', async () => {
    const { setupApi, sessionApi } = fixture()
    const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('클래스'), '클레릭')
    const subclass = screen.getByLabelText('하위 클래스') as HTMLSelectElement
    expect(Array.from(subclass.options).map(option => option.textContent)).toContain('생명 권역')
    await user.selectOptions(screen.getByLabelText('클래스'), '파이터')
    expect(screen.queryByLabelText('하위 클래스')).toBeNull()
  })

  it('일반 군용 무기 슬롯을 실제 무기로 선택하면 공격 목록을 계산한다', async () => {
    const { setupApi, sessionApi } = fixture()
    const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByLabelText('클래스'), '파이터')
    await user.selectOptions(screen.getByLabelText('장비 방어구'), 'chain')
    await user.selectOptions(screen.getByLabelText('장비 주 무장'), 'weapon-shield')
    await user.selectOptions(screen.getByLabelText('장비 보조 무장'), 'crossbow')
    await user.selectOptions(screen.getByLabelText('장비 꾸러미'), 'explorer')
    const rapier = await screen.findByLabelText(/레이피어 — 1d8 관통/)
    await user.click(rapier)
    const attackList = screen.getByLabelText('공격 목록')
    expect(attackList.textContent).toContain('레이피어')
    expect(attackList.textContent).toContain('명중')
  })
})
