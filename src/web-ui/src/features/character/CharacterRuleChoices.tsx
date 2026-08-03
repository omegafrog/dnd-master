import type { ChoiceRequirement } from './Dnd5eCharacterChoiceRules'

export function CharacterRuleChoices({ requirements, selections, onChange }: {
  requirements: ChoiceRequirement[]
  selections: Record<string, string[]>
  onChange: (requirementId: string, values: string[]) => void
}) {
  if (requirements.length === 0) return null
  return <fieldset><legend>추가 규칙 선택</legend>
    {requirements.map(requirement => {
      const selected = selections[requirement.id] ?? []
      return <fieldset key={requirement.id}><legend>{requirement.label} {requirement.count}개</legend>
        {requirement.options.map(option => <label key={option}>
          <input
            type="checkbox"
            aria-label={`${requirement.label} ${option}`}
            checked={selected.includes(option)}
            disabled={!selected.includes(option) && selected.length >= requirement.count}
            onChange={event => {
              const next = event.currentTarget.checked
                ? [...selected, option].slice(0, requirement.count)
                : selected.filter(value => value !== option)
              onChange(requirement.id, next)
            }}
          />
          {option}
        </label>)}
        <p>{selected.length} / {requirement.count} 선택</p>
      </fieldset>
    })}
  </fieldset>
}
