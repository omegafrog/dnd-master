import type { BackgroundOption } from './Dnd5eCharacterCatalog'

export type RoleplayDetails = {
  personality: string
  ideal: string
  bond: string
  flaw: string
}

export function CharacterRoleplayDetails({ background, help, values, onChange }: {
  background?: BackgroundOption
  help: { personality: string; ideal: string; bond: string; flaw: string }
  values: RoleplayDetails
  onChange: (next: RoleplayDetails) => void
}) {
  function set(key: keyof RoleplayDetails, value: string) { onChange({ ...values, [key]: value }) }
  return <>
    {background && <fieldset><legend>성격과 동기 — 배경 선택지</legend>
      <Suggestion label="인격 특성" help={help.personality} value={values.personality} suggestions={background.personality} onChange={value => set('personality', value)} />
      <Suggestion label="이상" help={help.ideal} value={values.ideal} suggestions={background.ideals} onChange={value => set('ideal', value)} />
      <Suggestion label="유대" help={help.bond} value={values.bond} suggestions={background.bonds} onChange={value => set('bond', value)} />
      <Suggestion label="단점" help={help.flaw} value={values.flaw} suggestions={background.flaws} onChange={value => set('flaw', value)} />
    </fieldset>}
  </>
}

function Suggestion({ label, help, value, suggestions, onChange }: { label: string; help: string; value: string; suggestions: string[]; onChange: (value: string) => void }) {
  return <section><h4>{label}</h4><p>{help}</p><select aria-label={`${label} 선택`} value={suggestions.includes(value) ? value : ''} onChange={event => onChange(event.currentTarget.value)}><option value="">선택하지 않음</option>{suggestions.map(item => <option key={item}>{item}</option>)}</select></section>
}
