import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterClassSelection } from './CharacterClassSelection'

const classes = [{
  id: '클레릭', label: '클레릭', description: '', hitDie: 'd8', savingThrows: ['wisdom', 'charisma'] as const,
  skillChoices: [], skillChoiceCount: 0, features: [], subclassLevel: 1,
  canCastSpells: true, cantrips: [], firstLevelSpells: [],
}]

describe('CharacterClassSelection', () => {
  it('클래스와 하위 클래스 변경을 전달한다', async () => {
    const user = userEvent.setup()
    const onClassChange = vi.fn()
    const onSubclassChange = vi.fn()
    render(<CharacterClassSelection
      classOptions={classes}
      characterClass=""
      subclass=""
      subclassOptions={[{ id: '생명 권역', label: '생명 권역' }]}
      subclassRequired
      equipmentGroups={[]}
      equipmentSelections={{}}
      weaponSlots={[]}
      weaponSelections={{}}
      onClassChange={onClassChange}
      onSubclassChange={onSubclassChange}
      onEquipmentChange={vi.fn()}
      onWeaponSelectionsChange={vi.fn()}
    />)

    await user.selectOptions(screen.getByLabelText('클래스'), '클레릭')
    await user.selectOptions(screen.getByLabelText('하위 클래스'), '생명 권역')
    expect(onClassChange).toHaveBeenCalledWith('클레릭')
    expect(onSubclassChange).toHaveBeenCalledWith('생명 권역')
  })

  it('장비 그룹 선택과 무기 슬롯 개수 제한을 적용한다', async () => {
    const user = userEvent.setup()
    const onEquipmentChange = vi.fn()
    const onWeaponSelectionsChange = vi.fn()
    render(<CharacterClassSelection
      classOptions={classes}
      characterClass="클레릭"
      subclass="생명 권역"
      subclassOptions={[]}
      subclassRequired={false}
      equipmentGroups={[{ id: 'weapon', label: '주 무장', options: [{ id: 'simple', label: '단순 무기', items: ['단순 무기 1개'] }] }]}
      equipmentSelections={{}}
      weaponSlots={[{ id: 'simple-0', label: '단순 무기 1개', category: 'SIMPLE', count: 1 }]}
      weaponSelections={{ 'simple-0': ['dagger'] }}
      onClassChange={vi.fn()}
      onSubclassChange={vi.fn()}
      onEquipmentChange={onEquipmentChange}
      onWeaponSelectionsChange={onWeaponSelectionsChange}
    />)

    await user.selectOptions(screen.getByLabelText('장비 주 무장'), 'simple')
    expect(onEquipmentChange).toHaveBeenCalledWith('weapon', 'simple')
    const unchecked = screen.getAllByRole('checkbox').find(input => !(input as HTMLInputElement).checked) as HTMLInputElement
    expect(unchecked.disabled).toBe(true)
    await user.click(screen.getByLabelText(/대거/))
    expect(onWeaponSelectionsChange).toHaveBeenCalledWith('simple-0', [])
  })
})
