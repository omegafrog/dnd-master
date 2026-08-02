import type { AbilityScores } from './Dnd5eRules'

export type SpellSelectionModel = 'KNOWN' | 'PREPARED' | 'SPELLBOOK' | 'PACT'
export type SpellSelectionRule = {
  model: SpellSelectionModel
  cantripCount: number
  learnedSpellCount: number
  preparedSpellCount: number
  firstLevelSlots: number
  recovery: 'LONG_REST' | 'SHORT_REST'
}

export function spellSelectionRule(characterClass: string, modifiers: AbilityScores, level = 1): SpellSelectionRule | null {
  switch (characterClass) {
    case '바드': return { model: 'KNOWN', cantripCount: 2, learnedSpellCount: 4, preparedSpellCount: 4, firstLevelSlots: 2, recovery: 'LONG_REST' }
    case '클레릭': return { model: 'PREPARED', cantripCount: 3, learnedSpellCount: 0, preparedSpellCount: Math.max(1, level + modifiers.wisdom), firstLevelSlots: 2, recovery: 'LONG_REST' }
    case '드루이드': return { model: 'PREPARED', cantripCount: 2, learnedSpellCount: 0, preparedSpellCount: Math.max(1, level + modifiers.wisdom), firstLevelSlots: 2, recovery: 'LONG_REST' }
    case '소서러': return { model: 'KNOWN', cantripCount: 4, learnedSpellCount: 2, preparedSpellCount: 2, firstLevelSlots: 2, recovery: 'LONG_REST' }
    case '워락': return { model: 'PACT', cantripCount: 2, learnedSpellCount: 2, preparedSpellCount: 2, firstLevelSlots: 1, recovery: 'SHORT_REST' }
    case '위저드': return { model: 'SPELLBOOK', cantripCount: 3, learnedSpellCount: 6, preparedSpellCount: Math.max(1, level + modifiers.intelligence), firstLevelSlots: 2, recovery: 'LONG_REST' }
    default: return null
  }
}

export function domainSpells(subclass: string): string[] {
  switch (subclass) {
    case '생명 권역': return ['축복', '상처 치료']
    case '빛 권역': return ['불타는 손', '요정 불꽃']
    case '지식 권역': return ['명령', '식별']
    case '자연 권역': return ['동물과의 대화', '동물 친구']
    case '폭풍 권역': return ['안개 구름', '천둥파도']
    case '속임수 권역': return ['매혹', '변장']
    case '전쟁 권역': return ['신의 은총', '신앙의 방패']
    default: return []
  }
}

export function selectionCount(rule: SpellSelectionRule | null): number {
  if (!rule) return 0
  return rule.model === 'PREPARED' || rule.model === 'SPELLBOOK' ? rule.preparedSpellCount : rule.learnedSpellCount
}
