import type { ClassOption } from './Dnd5eCharacterCatalog'
import type { EquipmentGroup } from './Dnd5eCharacterDerivedRules'
import type { WeaponSlot } from './Dnd5eWeaponRules'
import { weaponChoices } from './Dnd5eWeaponRules'

export function CharacterClassSelection({
  classOptions, characterClass, subclass, subclassOptions, subclassRequired,
  equipmentGroups, equipmentSelections, weaponSlots, weaponSelections,
  onClassChange, onSubclassChange, onEquipmentChange, onWeaponSelectionsChange, children,
}: {
  classOptions: ClassOption[]
  characterClass: string
  subclass: string
  subclassOptions: Array<{ id: string; label: string }>
  subclassRequired: boolean
  equipmentGroups: EquipmentGroup[]
  equipmentSelections: Record<string, string>
  weaponSlots: WeaponSlot[]
  weaponSelections: Record<string, string[]>
  onClassChange: (value: string) => void
  onSubclassChange: (value: string) => void
  onEquipmentChange: (groupId: string, value: string) => void
  onWeaponSelectionsChange: (slotId: string, values: string[]) => void
  children?: React.ReactNode
}) {
  function toggleWeapon(slot: WeaponSlot, weaponId: string, checked: boolean) {
    const selected = weaponSelections[slot.id] ?? []
    onWeaponSelectionsChange(slot.id, checked ? [...selected, weaponId].slice(0, slot.count) : selected.filter(value => value !== weaponId))
  }

  return <fieldset><legend>클래스</legend>
    <label>클래스 <select aria-label="클래스" value={characterClass} onChange={event => onClassChange(event.currentTarget.value)}>
      <option value="">선택하세요</option>{classOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
    </select></label>
    {subclassRequired && <label>하위 클래스 <select aria-label="하위 클래스" value={subclass} onChange={event => onSubclassChange(event.currentTarget.value)}>
      <option value="">선택하세요</option>{subclassOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
    </select></label>}
    {children}
    {equipmentGroups.length > 0 && <fieldset><legend>클래스 시작 장비</legend>{equipmentGroups.map(group => <label key={group.id}>{group.label} <select aria-label={`장비 ${group.label}`} value={equipmentSelections[group.id] ?? ''} onChange={event => onEquipmentChange(group.id, event.currentTarget.value)}>
      <option value="">선택하세요</option>{group.options.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
    </select></label>)}</fieldset>}
    {weaponSlots.map(slot => <fieldset key={slot.id}><legend>{slot.label}</legend>{weaponChoices(slot.category, slot.label.includes('근접')).map(weapon => {
      const selected = weaponSelections[slot.id] ?? []
      return <label key={weapon.id}><input type="checkbox" checked={selected.includes(weapon.id)} disabled={!selected.includes(weapon.id) && selected.length >= slot.count} onChange={event => toggleWeapon(slot, weapon.id, event.currentTarget.checked)} />{weapon.label} — {weapon.damage} {weapon.damageType}</label>
    })}</fieldset>)}
  </fieldset>
}
