import type { Ability, AbilityScores } from './Dnd5eRules'

const abilityLabels: Record<Ability, string> = {
  strength: '근력',
  dexterity: '민첩',
  constitution: '건강',
  intelligence: '지능',
  wisdom: '지혜',
  charisma: '매력',
}

export function CharacterAbilityScores({
  abilities,
  standardArray,
  scores,
  onChange,
}: {
  abilities: Ability[]
  standardArray: number[]
  scores: AbilityScores
  onChange: (scores: AbilityScores) => void
}) {
  const assignedCount = abilities.filter(ability => scores[ability] > 0).length

  return <fieldset>
    <legend>능력치 — 표준 배열</legend>
    <p>{assignedCount}/{abilities.length}개 배정</p>
    {abilities.map(ability => {
      const usedByOthers = abilities
        .filter(item => item !== ability)
        .map(item => scores[item])
        .filter(value => value > 0)
      return <label key={ability}>
        {abilityLabels[ability]}{' '}
        <select
          aria-label={abilityLabels[ability]}
          value={scores[ability] || ''}
          onChange={event => onChange({ ...scores, [ability]: Number(event.currentTarget.value) })}
        >
          <option value="">선택</option>
          {standardArray.map(value => <option key={value} value={value} disabled={usedByOthers.includes(value)}>{value}</option>)}
        </select>
      </label>
    })}
  </fieldset>
}
