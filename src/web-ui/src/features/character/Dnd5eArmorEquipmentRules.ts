export type ArmorCategory = 'LIGHT' | 'MEDIUM' | 'HEAVY'

export type ArmorEquipmentIssue = {
  code: 'ARMOR_NOT_PROFICIENT' | 'DRUID_METAL_ARMOR'
  message: string
}

const armorCategories: Record<string, ArmorCategory> = {
  '패디드 아머': 'LIGHT',
  '가죽 갑옷': 'LIGHT',
  '스터디드 레더': 'LIGHT',
  '하이드': 'MEDIUM',
  '체인 셔츠': 'MEDIUM',
  '스케일 메일': 'MEDIUM',
  '브레스트플레이트': 'MEDIUM',
  '하프 플레이트': 'MEDIUM',
  '링 메일': 'HEAVY',
  '체인 메일': 'HEAVY',
  '스플린트': 'HEAVY',
  '플레이트': 'HEAVY',
}

const metalArmor = new Set([
  '체인 셔츠', '스케일 메일', '브레스트플레이트', '하프 플레이트',
  '링 메일', '체인 메일', '스플린트', '플레이트',
])

export function armorCategory(armor: string): ArmorCategory | null {
  return armorCategories[armor] ?? null
}

export function validateArmorEquipment(characterClass: string, armor: string, armorProficiencies: string[]): ArmorEquipmentIssue[] {
  if (!armor) return []
  const issues: ArmorEquipmentIssue[] = []
  const category = armorCategory(armor)
  if (category && !isProficient(category, armorProficiencies)) {
    issues.push({ code: 'ARMOR_NOT_PROFICIENT', message: `${armor}에 숙련되어 있지 않습니다.` })
  }
  if (characterClass === '드루이드' && metalArmor.has(armor)) {
    issues.push({ code: 'DRUID_METAL_ARMOR', message: '드루이드는 금속 갑옷을 착용하지 않습니다.' })
  }
  return issues
}

function isProficient(category: ArmorCategory, proficiencies: string[]): boolean {
  if (proficiencies.includes('모든 갑옷')) return true
  if (category === 'LIGHT') return proficiencies.some(value => value.includes('경갑'))
  if (category === 'MEDIUM') return proficiencies.some(value => value.includes('평갑'))
  return proficiencies.some(value => value.includes('중갑'))
}
