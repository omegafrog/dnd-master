import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterEquipmentLoadout } from './CharacterEquipmentLoadout'

const state = {
  armor: '체인 메일',
  shield: true,
  mainHandWeaponId: 'longsword',
  offHandWeaponId: null,
  twoHandedWeaponId: null,
}

describe('CharacterEquipmentLoadout', () => {
  it('양손 무기를 선택하면 한손 무기와 방패를 해제한다', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CharacterEquipmentLoadout
      ownedWeaponIds={['longsword', 'greatsword']}
      availableArmor="체인 메일"
      shieldAvailable
      state={state}
      conflicts={[]}
      armorIssues={[]}
      onChange={onChange}
    />)

    await user.selectOptions(screen.getByLabelText('양손 무기'), 'greatsword')

    expect(onChange).toHaveBeenCalledWith({
      armor: '체인 메일',
      shield: false,
      mainHandWeaponId: null,
      offHandWeaponId: null,
      twoHandedWeaponId: 'greatsword',
    })
  })

  it('장비와 갑옷 검증 오류를 함께 표시한다', () => {
    render(<CharacterEquipmentLoadout
      ownedWeaponIds={['greatsword']}
      availableArmor="체인 메일"
      shieldAvailable
      state={{ ...state, mainHandWeaponId: null, twoHandedWeaponId: 'greatsword' }}
      conflicts={[{ code: 'SHIELD_WITH_TWO_HANDED', message: '방패와 양손 무기는 동시에 장착할 수 없습니다.' }]}
      armorIssues={[{ code: 'ARMOR_NOT_PROFICIENT', message: '체인 메일에 숙련되어 있지 않습니다.' }]}
      onChange={() => undefined}
    />)

    expect(screen.getAllByRole('alert').map(node => node.textContent)).toEqual([
      '방패와 양손 무기는 동시에 장착할 수 없습니다.',
      '체인 메일에 숙련되어 있지 않습니다.',
    ])
  })
})
