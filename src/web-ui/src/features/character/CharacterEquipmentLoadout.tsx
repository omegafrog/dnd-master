import type { ArmorEquipmentIssue } from './Dnd5eArmorEquipmentRules'
import type { EquippedItemState, EquipmentConflict } from './Dnd5eEquipmentStateRules'
import { weaponOptions } from './Dnd5eWeaponRules'

export function CharacterEquipmentLoadout({
  ownedWeaponIds,
  availableArmor,
  shieldAvailable,
  state,
  conflicts,
  armorIssues,
  onChange,
}: {
  ownedWeaponIds: string[]
  availableArmor?: string
  shieldAvailable: boolean
  state: EquippedItemState
  conflicts: EquipmentConflict[]
  armorIssues: ArmorEquipmentIssue[]
  onChange: (state: EquippedItemState) => void
}) {
  const ownedWeapons = ownedWeaponIds
    .map(id => weaponOptions.find(weapon => weapon.id === id))
    .filter((weapon): weapon is NonNullable<typeof weapon> => Boolean(weapon))
  const oneHandedWeapons = ownedWeapons.filter(weapon => !weapon.twoHanded)
  const twoHandedWeapons = ownedWeapons.filter(weapon => weapon.twoHanded)

  function setHandWeapon(field: 'mainHandWeaponId' | 'offHandWeaponId' | 'twoHandedWeaponId', value: string) {
    onChange({
      ...state,
      mainHandWeaponId: field === 'twoHandedWeaponId' && value ? null : field === 'mainHandWeaponId' ? value || null : state.mainHandWeaponId,
      offHandWeaponId: field === 'twoHandedWeaponId' && value ? null : field === 'offHandWeaponId' ? value || null : state.offHandWeaponId,
      twoHandedWeaponId: field === 'twoHandedWeaponId' ? value || null : value ? null : state.twoHandedWeaponId,
      shield: field === 'twoHandedWeaponId' && value ? false : state.shield,
    })
  }

  if (!ownedWeaponIds.length && !availableArmor && !shieldAvailable) return null

  return <fieldset><legend>장착 상태</legend>
    {availableArmor && <label>장착 갑옷 <select aria-label="장착 갑옷" value={state.armor} onChange={event => onChange({ ...state, armor: event.currentTarget.value })}><option value="">장착하지 않음</option><option value={availableArmor}>{availableArmor}</option></select></label>}
    {shieldAvailable && <label><input aria-label="방패 장착" type="checkbox" checked={state.shield} onChange={event => onChange({ ...state, shield: event.currentTarget.checked })} />방패 장착</label>}
    <label>주손 무기 <select aria-label="주손 무기" value={state.mainHandWeaponId ?? ''} onChange={event => setHandWeapon('mainHandWeaponId', event.currentTarget.value)}><option value="">없음</option>{oneHandedWeapons.map(weapon => <option key={weapon.id} value={weapon.id}>{weapon.label}</option>)}</select></label>
    <label>보조손 무기 <select aria-label="보조손 무기" value={state.offHandWeaponId ?? ''} onChange={event => setHandWeapon('offHandWeaponId', event.currentTarget.value)}><option value="">없음</option>{oneHandedWeapons.map(weapon => <option key={weapon.id} value={weapon.id}>{weapon.label}</option>)}</select></label>
    <label>양손 무기 <select aria-label="양손 무기" value={state.twoHandedWeaponId ?? ''} onChange={event => setHandWeapon('twoHandedWeaponId', event.currentTarget.value)}><option value="">없음</option>{twoHandedWeapons.map(weapon => <option key={weapon.id} value={weapon.id}>{weapon.label}</option>)}</select></label>
    {[...conflicts, ...armorIssues].map(issue => <p role="alert" key={issue.code}>{issue.message}</p>)}
  </fieldset>
}
