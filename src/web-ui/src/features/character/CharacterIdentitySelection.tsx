type RaceOption = {
  id: string
  subraces: { id: string }[]
}

export function CharacterIdentitySelection({
  name,
  race,
  subrace,
  proficiencyBonus,
  raceOptions,
  onNameChange,
  onRaceChange,
  onSubraceChange,
}: {
  name: string
  race: string
  subrace: string
  proficiencyBonus: number
  raceOptions: RaceOption[]
  onNameChange: (name: string) => void
  onRaceChange: (race: string) => void
  onSubraceChange: (subrace: string) => void
}) {
  const selectedRace = raceOptions.find(option => option.id === race)

  function changeRace(nextRace: string) {
    onRaceChange(nextRace)
    onSubraceChange('')
  }

  return <>
    <fieldset>
      <legend>기본 정보</legend>
      <label>
        캐릭터 이름
        <input aria-label="캐릭터 이름" value={name} onChange={event => onNameChange(event.currentTarget.value)} />
      </label>
      <p>레벨: <strong>1</strong> · 경험치: <strong>0</strong> · 숙련 보너스: <strong>+{proficiencyBonus}</strong></p>
    </fieldset>
    <fieldset>
      <legend>종족</legend>
      <label>
        종족
        <select aria-label="종족" value={race} onChange={event => changeRace(event.currentTarget.value)}>
          <option value="">선택하세요</option>
          {raceOptions.map(option => <option key={option.id} value={option.id}>{option.id}</option>)}
        </select>
      </label>
      {selectedRace?.subraces.length ? <label>
        하위 종족
        <select aria-label="하위 종족" value={subrace} onChange={event => onSubraceChange(event.currentTarget.value)}>
          <option value="">선택하세요</option>
          {selectedRace.subraces.map(option => <option key={option.id} value={option.id}>{option.id}</option>)}
        </select>
      </label> : null}
    </fieldset>
  </>
}
