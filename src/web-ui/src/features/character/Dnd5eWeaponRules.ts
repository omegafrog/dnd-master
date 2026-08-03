import type { AbilityScores } from './Dnd5eRules'

export type WeaponDefinition = {
  id: string
  label: string
  damage: string
  damageType: string
  category: 'SIMPLE' | 'MARTIAL'
  kind: 'MELEE' | 'RANGED'
  finesse?: boolean
  range?: string
  thrownRange?: string
  ammunition?: boolean
  twoHanded?: boolean
  versatileDamage?: string
}

export type AttackView = {
  weaponId: string
  label: string
  attackBonus: number
  damage: string
  damageType: string
  range?: string
}

export const weaponOptions: WeaponDefinition[] = [
  { id: 'club', label: '곤봉', damage: '1d4', damageType: '타격', category: 'SIMPLE', kind: 'MELEE' },
  { id: 'dagger', label: '대거', damage: '1d4', damageType: '관통', category: 'SIMPLE', kind: 'MELEE', finesse: true, thrownRange: '20/60ft' },
  { id: 'greatclub', label: '그레이트클럽', damage: '1d8', damageType: '타격', category: 'SIMPLE', kind: 'MELEE', twoHanded: true },
  { id: 'handaxe', label: '핸드액스', damage: '1d6', damageType: '참격', category: 'SIMPLE', kind: 'MELEE', thrownRange: '20/60ft' },
  { id: 'javelin', label: '재블린', damage: '1d6', damageType: '관통', category: 'SIMPLE', kind: 'MELEE', thrownRange: '30/120ft' },
  { id: 'light-hammer', label: '라이트 해머', damage: '1d4', damageType: '타격', category: 'SIMPLE', kind: 'MELEE', thrownRange: '20/60ft' },
  { id: 'mace', label: '메이스', damage: '1d6', damageType: '타격', category: 'SIMPLE', kind: 'MELEE' },
  { id: 'quarterstaff', label: '쿼터스태프', damage: '1d6', damageType: '타격', category: 'SIMPLE', kind: 'MELEE', versatileDamage: '1d8' },
  { id: 'sickle', label: '낫', damage: '1d4', damageType: '참격', category: 'SIMPLE', kind: 'MELEE' },
  { id: 'spear', label: '창', damage: '1d6', damageType: '관통', category: 'SIMPLE', kind: 'MELEE', thrownRange: '20/60ft', versatileDamage: '1d8' },
  { id: 'light-crossbow', label: '라이트 크로스보우', damage: '1d8', damageType: '관통', category: 'SIMPLE', kind: 'RANGED', range: '80/320ft', ammunition: true, twoHanded: true },
  { id: 'dart', label: '다트', damage: '1d4', damageType: '관통', category: 'SIMPLE', kind: 'RANGED', finesse: true, range: '20/60ft' },
  { id: 'shortbow', label: '단궁', damage: '1d6', damageType: '관통', category: 'SIMPLE', kind: 'RANGED', range: '80/320ft', ammunition: true, twoHanded: true },
  { id: 'sling', label: '슬링', damage: '1d4', damageType: '타격', category: 'SIMPLE', kind: 'RANGED', range: '30/120ft', ammunition: true },
  { id: 'battleaxe', label: '배틀액스', damage: '1d8', damageType: '참격', category: 'MARTIAL', kind: 'MELEE', versatileDamage: '1d10' },
  { id: 'greataxe', label: '그레이트액스', damage: '1d12', damageType: '참격', category: 'MARTIAL', kind: 'MELEE', twoHanded: true },
  { id: 'greatsword', label: '그레이트소드', damage: '2d6', damageType: '참격', category: 'MARTIAL', kind: 'MELEE', twoHanded: true },
  { id: 'longsword', label: '롱소드', damage: '1d8', damageType: '참격', category: 'MARTIAL', kind: 'MELEE', versatileDamage: '1d10' },
  { id: 'rapier', label: '레이피어', damage: '1d8', damageType: '관통', category: 'MARTIAL', kind: 'MELEE', finesse: true },
  { id: 'scimitar', label: '시미터', damage: '1d6', damageType: '참격', category: 'MARTIAL', kind: 'MELEE', finesse: true },
  { id: 'shortsword', label: '숏소드', damage: '1d6', damageType: '관통', category: 'MARTIAL', kind: 'MELEE', finesse: true },
  { id: 'warhammer', label: '워해머', damage: '1d8', damageType: '타격', category: 'MARTIAL', kind: 'MELEE', versatileDamage: '1d10' },
  { id: 'longbow', label: '장궁', damage: '1d8', damageType: '관통', category: 'MARTIAL', kind: 'RANGED', range: '150/600ft', ammunition: true, twoHanded: true },
  { id: 'hand-crossbow', label: '핸드 크로스보우', damage: '1d6', damageType: '관통', category: 'MARTIAL', kind: 'RANGED', range: '30/120ft', ammunition: true },
]

const fixedEquipmentWeapons: Record<string, string> = {
  곤봉: 'club', 대거: 'dagger', 그레이트클럽: 'greatclub', 핸드액스: 'handaxe', 재블린: 'javelin', '라이트 해머': 'light-hammer',
  메이스: 'mace', 쿼터스태프: 'quarterstaff', 낫: 'sickle', 창: 'spear', '라이트 크로스보우': 'light-crossbow', 다트: 'dart',
  단궁: 'shortbow', 슬링: 'sling', 배틀액스: 'battleaxe', 그레이트액스: 'greataxe', 그레이트소드: 'greatsword', 롱소드: 'longsword',
  레이피어: 'rapier', 시미터: 'scimitar', 숏소드: 'shortsword', 워해머: 'warhammer', 장궁: 'longbow', '핸드 크로스보우': 'hand-crossbow',
}

export function unresolvedWeaponSlots(equipment: string[]): Array<{ id: string; label: string; category: 'SIMPLE' | 'MARTIAL'; count: number }> {
  const slots: Array<{ id: string; label: string; category: 'SIMPLE' | 'MARTIAL'; count: number }> = []
  equipment.forEach((item, index) => {
    const martial = item.match(/^군용 (?:근접 )?무기 (\d+)개$/)
    const simple = item.match(/^단순 (?:근접 )?무기 (\d+)개$/)
    if (martial) slots.push({ id: `martial-${index}`, label: item, category: 'MARTIAL', count: Number(martial[1]) })
    if (simple) slots.push({ id: `simple-${index}`, label: item, category: 'SIMPLE', count: Number(simple[1]) })
  })
  return slots
}

export function resolvedWeaponIds(equipment: string[], selectedSlots: Record<string, string[]>): string[] {
  const fixed = equipment.flatMap(item => {
    const normalized = item.replace(/ \d+개$/, '')
    const id = fixedEquipmentWeapons[normalized]
    return id ? [id] : []
  })
  return [...fixed, ...Object.values(selectedSlots).flat()].filter((value, index, all) => all.indexOf(value) === index)
}

export function weaponChoices(category: 'SIMPLE' | 'MARTIAL', meleeOnly = false): WeaponDefinition[] {
  return weaponOptions.filter(weapon => weapon.category === category && (!meleeOnly || weapon.kind === 'MELEE'))
}

export function calculateAttacks(weaponIds: string[], scores: AbilityScores, proficiencyBonus: number): AttackView[] {
  return weaponIds.flatMap(id => {
    const weapon = weaponOptions.find(option => option.id === id)
    if (!weapon) return []
    const abilityModifier = weapon.kind === 'RANGED'
      ? scores.dexterity
      : weapon.finesse ? Math.max(scores.strength, scores.dexterity) : scores.strength
    return [{
      weaponId: weapon.id,
      label: weapon.label,
      attackBonus: abilityModifier + proficiencyBonus,
      damage: `${weapon.damage}${abilityModifier >= 0 ? '+' : ''}${abilityModifier}`,
      damageType: weapon.damageType,
      range: weapon.range ?? weapon.thrownRange,
    }]
  })
}
