export function CharacterSkillSelection({
  skillOptions,
  skillChoiceCount,
  selectedSkills,
  fixedProficientSkills,
  proficientSkills,
  expertiseChoiceCount,
  selectedExpertise,
  onSkillsChange,
  onExpertiseChange,
}: {
  skillOptions: string[]
  skillChoiceCount: number
  selectedSkills: string[]
  fixedProficientSkills: string[]
  proficientSkills: string[]
  expertiseChoiceCount: number
  selectedExpertise: string[]
  onSkillsChange: (skills: string[]) => void
  onExpertiseChange: (expertise: string[]) => void
}) {
  function toggleSkill(skill: string) {
    const next = selectedSkills.includes(skill)
      ? selectedSkills.filter(item => item !== skill)
      : selectedSkills.length < skillChoiceCount
        ? [...selectedSkills, skill]
        : selectedSkills
    onSkillsChange(next)
    onExpertiseChange(selectedExpertise.filter(item => next.includes(item) || fixedProficientSkills.includes(item)))
  }

  function toggleExpertise(skill: string) {
    const next = selectedExpertise.includes(skill)
      ? selectedExpertise.filter(item => item !== skill)
      : selectedExpertise.length < expertiseChoiceCount
        ? [...selectedExpertise, skill]
        : selectedExpertise
    onExpertiseChange(next)
  }

  return <>
    <fieldset>
      <legend>기술 숙련 {skillChoiceCount}개 선택</legend>
      <p>{selectedSkills.length}/{skillChoiceCount}개 선택</p>
      {skillOptions.map(skill => <label key={skill}>
        <input
          type="checkbox"
          checked={selectedSkills.includes(skill)}
          onChange={() => toggleSkill(skill)}
          disabled={!selectedSkills.includes(skill) && selectedSkills.length >= skillChoiceCount}
        />
        {skill}
      </label>)}
    </fieldset>
    {expertiseChoiceCount > 0 && <fieldset>
      <legend>숙달 {expertiseChoiceCount}개 선택</legend>
      <p>{selectedExpertise.length}/{expertiseChoiceCount}개 선택</p>
      {proficientSkills.map(skill => <label key={skill}>
        <input
          type="checkbox"
          checked={selectedExpertise.includes(skill)}
          onChange={() => toggleExpertise(skill)}
          disabled={!selectedExpertise.includes(skill) && selectedExpertise.length >= expertiseChoiceCount}
        />
        {skill}
      </label>)}
    </fieldset>}
  </>
}
