import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CharacterDerivedPreview } from './CharacterDerivedPreview'

const saves = { strength: 3, dexterity: 2, constitution: 1, intelligence: 0, wisdom: -1, charisma: -2 }

describe('CharacterDerivedPreview', () => {
  it('파생 수치와 공격 속성을 표시한다', () => {
    render(<CharacterDerivedPreview armorClass={16} hitPointMaximum={10} passivePerception={13} savingThrows={saves} skills={[{ id: 'perception', label: '지각', bonus: 3 }]} attacks={[{ weaponId: 'spear', mode: 'THROWN', label: '창 투척', attackBonus: 5, damage: '1d6+3', damageType: '관통', range: '20/60ft' }]} spell={{ attackBonus: 4, saveDc: 12, firstLevelSlots: 2 }} />)
    expect(screen.getByText(/방어도 16/).textContent).toContain('수동 지각 13')
    expect(screen.getByText(/주문 공격 \+4/).textContent).toContain('1레벨 슬롯 2')
    expect(screen.getByText(/창 투척/).textContent).toContain('사거리 20/60ft')
  })
})
