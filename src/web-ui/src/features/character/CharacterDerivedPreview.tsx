import type { Ability } from './Dnd5eRules'

export type PreviewSkill = { id: string; label: string; bonus: number }
export type PreviewAttack = {
  weaponId: string
  mode: string
  label: string
  attackBonus: number
  damage: string
  damageType: string
  versatileDamage?: string
  range?: string
  ammunitionRequired?: boolean
}

export function CharacterDerivedPreview({ armorClass, hitPointMaximum, passivePerception, savingThrows, skills, attacks, spell }: {
  armorClass: number
  hitPointMaximum: number
  passivePerception: number
  savingThrows: Record<Ability, number>
  skills: PreviewSkill[]
  attacks: PreviewAttack[]
  spell?: { attackBonus: number; saveDc: number | null; firstLevelSlots: number }
}) {
  const labels: Record<Ability, string> = { strength: '근력', dexterity: '민첩', constitution: '건강', intelligence: '지능', wisdom: '지혜', charisma: '매력' }
  const abilities: Ability[] = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma']
  return <section aria-label="자동 계산 결과"><h3>자동 계산 결과</h3>
    <p>방어도 {armorClass} · 최대 HP {hitPointMaximum || '?'} · 수동 지각 {passivePerception}</p>
    <p>내성 굴림: {abilities.map(ability => `${labels[ability]} ${formatModifier(savingThrows[ability])}`).join(' · ')}</p>
    {spell && <p>주문 공격 {formatModifier(spell.attackBonus)} · 주문 DC {spell.saveDc} · 1레벨 슬롯 {spell.firstLevelSlots}</p>}
    <ul aria-label="기술 보너스">{skills.map(skill => <li key={skill.id}>{skill.label} {formatModifier(skill.bonus)}</li>)}</ul>
    <ul aria-label="공격 목록">{attacks.map(attack => <li key={`${attack.weaponId}-${attack.mode}`}>{attack.label}: 명중 {formatModifier(attack.attackBonus)}, 피해 {attack.damage} {attack.damageType}{attack.versatileDamage ? ` · 양손 ${attack.versatileDamage}` : ''}{attack.range ? ` · 사거리 ${attack.range}` : ''}{attack.ammunitionRequired ? ' · 탄약 필요' : ''}</li>)}</ul>
  </section>
}

function formatModifier(value: number) { return value >= 0 ? `+${value}` : String(value) }
