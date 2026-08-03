import type { AbilityScores } from './Dnd5eRules'
import { weaponOptions, type AttackView, type WeaponDefinition } from './Dnd5eWeaponRules'

export type EquippedItemState = {
  armor: string
  shield: boolean
  mainHandWeaponId: string | null
  offHandWeaponId: string | null
  twoHandedWeaponId: string | null
}

export type EquipmentConflict = {
  code: 'SHIELD_WITH_TWO_HANDED' | 'DUPLICATE_HAND_WEAPON' | 'WEAPON_NOT_OWNED'
  message: string
}

export type CombatAttackView = AttackView & {
  mode: 'MELEE' | 'RANGED' | 'THROWN' | 'UNARMED'
  ammunitionRequired: boolean
  versatileDamage?: string
}

export function validateEquipmentState(ownedWeaponIds: string[], state: EquippedItemState): EquipmentConflict[] {
  const conflicts: EquipmentConflict[] = []
  const equippedIds = [state.mainHandWeaponId, state.offHandWeaponId, state.twoHandedWeaponId].filter((value): value is string => Boolean(value))
  if (state.shield && state.twoHandedWeaponId) {
    conflicts.push({ code: 'SHIELD_WITH_TWO_HANDED', message: '방패와 양손 무기는 동시에 장착할 수 없습니다.' })
  }
  if (state.mainHandWeaponId && state.mainHandWeaponId === state.offHandWeaponId) {
    conflicts.push({ code: 'DUPLICATE_HAND_WEAPON', message: '같은 무기 한 개를 양손에 동시에 장착할 수 없습니다.' })
  }
  const owned = new Set(ownedWeaponIds)
  for (const id of equippedIds) {
    if (!owned.has(id)) conflicts.push({ code: 'WEAPON_NOT_OWNED', message: `보유하지 않은 무기 ${id}를 장착할 수 없습니다.` })
  }
  return conflicts
}

export function defaultEquipmentState(ownedWeaponIds: string[], armor: string, shield: boolean): EquippedItemState {
  const first = ownedWeaponIds.map(findWeapon).find(Boolean)
  const twoHanded = first?.twoHanded ? first.id : null
  return {
    armor,
    shield: shield && !twoHanded,
    mainHandWeaponId: twoHanded ? null : first?.id ?? null,
    offHandWeaponId: null,
    twoHandedWeaponId: twoHanded,
  }
}

export function calculateCombatAttacks(
  characterClass: string,
  state: EquippedItemState,
  ownedWeaponIds: string[],
  scores: AbilityScores,
  proficiencyBonus: number,
): CombatAttackView[] {
  const equippedIds = [state.mainHandWeaponId, state.offHandWeaponId, state.twoHandedWeaponId]
    .filter((value): value is string => Boolean(value) && ownedWeaponIds.includes(value as string))
  const attacks = equippedIds.flatMap(id => weaponAttack(findWeapon(id), scores, proficiencyBonus))
  const unarmedModifier = characterClass === '몽크' ? Math.max(scores.strength, scores.dexterity) : scores.strength
  const unarmedDamage = characterClass === '몽크' ? '1d4' : '1'
  attacks.push({
    weaponId: 'unarmed',
    label: characterClass === '몽크' ? '무술 비무장 공격' : '비무장 공격',
    attackBonus: unarmedModifier + proficiencyBonus,
    damage: `${unarmedDamage}${formatDamageModifier(unarmedModifier)}`,
    damageType: '타격',
    mode: 'UNARMED',
    ammunitionRequired: false,
  })
  return attacks
}

function weaponAttack(weapon: WeaponDefinition | undefined, scores: AbilityScores, proficiencyBonus: number): CombatAttackView[] {
  if (!weapon) return []
  const meleeModifier = weapon.finesse ? Math.max(scores.strength, scores.dexterity) : scores.strength
  const rangedModifier = weapon.kind === 'RANGED' ? scores.dexterity : meleeModifier
  const result: CombatAttackView[] = [{
    weaponId: weapon.id,
    label: weapon.label,
    attackBonus: rangedModifier + proficiencyBonus,
    damage: `${weapon.damage}${formatDamageModifier(rangedModifier)}`,
    damageType: weapon.damageType,
    range: weapon.range,
    mode: weapon.kind,
    ammunitionRequired: Boolean(weapon.ammunition),
    versatileDamage: weapon.versatileDamage ? `${weapon.versatileDamage}${formatDamageModifier(meleeModifier)}` : undefined,
  }]
  if (weapon.thrownRange && weapon.kind === 'MELEE') {
    result.push({
      weaponId: `${weapon.id}-thrown`,
      label: `${weapon.label} 투척`,
      attackBonus: meleeModifier + proficiencyBonus,
      damage: `${weapon.damage}${formatDamageModifier(meleeModifier)}`,
      damageType: weapon.damageType,
      range: weapon.thrownRange,
      mode: 'THROWN',
      ammunitionRequired: false,
    })
  }
  return result
}

function findWeapon(id: string): WeaponDefinition | undefined {
  return weaponOptions.find(weapon => weapon.id === id)
}

function formatDamageModifier(value: number): string {
  if (value === 0) return ''
  return value > 0 ? `+${value}` : String(value)
}
