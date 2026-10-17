import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CharacterSheetView } from './CharacterSheetView'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'

describe('캐릭터 시트의 능력 자원 표시', () => {
  it('시트에 저장된 능력 식별자와 자원 수량, 회복 조건을 보여준다', async () => {
    const api = { getCharacter: vi.fn().mockResolvedValue({ characterSheetId: 'sheet-1', name: '마루', edition: 'DND_5E_2014',
      armorClass: 12, strength: 8, dexterity: 14, constitution: 12, intelligence: 16, wisdom: 10, charisma: 10,
      learnedAbilityIds: ['ability:blink'], resources: { 'arcane-charge': { current: 1, maximum: 3,
        recoveryCondition: 'LONG_REST', abilityIds: ['ability:blink'] } } }) } as unknown as AdventurePlayApi
    render(<CharacterSheetView sheetId="sheet-1" api={api} />)
    expect(await screen.findByText('능력의 고유 식별자: ability:blink')).toBeTruthy()
    expect(screen.getByText(/긴 휴식.*저장된 회복 조건 식별자: LONG_REST/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: /회복|소모/ })).toBeNull()
  })
})
