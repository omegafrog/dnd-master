import type { CharacterCreationDraft } from '../rulebooks/SetupApi'

export type CharacterRuleViolationView = {
  code: string
  category: string
  severity: string
  message: string
  parameters: Record<string, string>
}

export type CharacterAttackView = {
  weaponId: string
  label: string
  attackBonus: number
  damage: string
  damageType: string
  range?: string | null
  mode: 'MELEE' | 'RANGED' | 'THROWN' | 'UNARMED'
  ammunitionRequired: boolean
  versatileDamage?: string | null
}

export type CharacterSkillEvaluationView = {
  proficient: boolean
  expertise: boolean
  bonus: number
}

export type CharacterBuildEvaluationView = {
  valid: boolean
  derived: {
    abilityScores?: Record<string, number>
    abilityModifiers?: Record<string, number>
    proficiencyBonus?: number
    initiative?: number
    speed?: number
    hitDie?: string
    hitPointMaximum?: number
    armorClass?: number
    savingThrowBonuses?: Record<string, number>
    skillBonuses?: Record<string, CharacterSkillEvaluationView>
    passivePerception?: number
    attacks?: CharacterAttackView[]
    spellAttackBonus?: number | null
    spellSaveDc?: number | null
  }
  violations: CharacterRuleViolationView[]
}

export type CharacterRulesCatalogView = {
  edition: 'DND_5E_2014'
  baseSchema: string
  revision: number
  races: string[]
  classes: string[]
  backgrounds: string[]
}

async function jsonRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })
  if (!response.ok) throw new Error('캐릭터 규칙 엔진 요청을 처리하지 못했습니다.')
  return response.json() as Promise<T>
}

export function getCharacterRulesCatalog(): Promise<CharacterRulesCatalogView> {
  return jsonRequest('/internal/v1/character-rules/catalogs/DND_5E_2014')
}

export function evaluateCharacterBuild(sessionId: string, draft: CharacterCreationDraft): Promise<CharacterBuildEvaluationView> {
  return jsonRequest(`/internal/v1/adventure-sessions/${sessionId}/character-builds/evaluate`, {
    method: 'POST',
    body: JSON.stringify(draft),
  })
}
