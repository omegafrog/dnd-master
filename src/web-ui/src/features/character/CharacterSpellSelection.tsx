import type { SpellSelectionRule } from './Dnd5eSpellPreparationRules'

export function CharacterSpellSelection({
  rule,
  cantripOptions,
  firstLevelOptions,
  selectedCantrips,
  selectedFirstLevelSpells,
  requiredCantrips,
  requiredFirstLevelSpells,
  automaticSpells,
  onCantripsChange,
  onFirstLevelSpellsChange,
}: {
  rule: SpellSelectionRule | null
  cantripOptions: string[]
  firstLevelOptions: string[]
  selectedCantrips: string[]
  selectedFirstLevelSpells: string[]
  requiredCantrips: number
  requiredFirstLevelSpells: number
  automaticSpells: string[]
  onCantripsChange: (spells: string[]) => void
  onFirstLevelSpellsChange: (spells: string[]) => void
}) {
  if (!rule) return null

  function toggle(value: string, selected: string[], limit: number, onChange: (values: string[]) => void) {
    if (selected.includes(value)) onChange(selected.filter(item => item !== value))
    else if (selected.length < limit) onChange([...selected, value])
  }

  return <fieldset><legend>주문 선택</legend>
    <p>방식: {modelLabel(rule.model)} · 회복: {rule.recovery === 'SHORT_REST' ? '짧은 휴식' : '긴 휴식'}</p>
    <fieldset><legend>소마법 {requiredCantrips}개</legend>
      {cantripOptions.map(spell => <label key={spell}><input
        type="checkbox"
        checked={selectedCantrips.includes(spell)}
        disabled={!selectedCantrips.includes(spell) && selectedCantrips.length >= requiredCantrips}
        onChange={() => toggle(spell, selectedCantrips, requiredCantrips, onCantripsChange)}
      />{spell}</label>)}
      <p>{selectedCantrips.length} / {requiredCantrips}</p>
    </fieldset>
    <fieldset><legend>1레벨 주문 {requiredFirstLevelSpells}개</legend>
      {firstLevelOptions.map(spell => <label key={spell}><input
        type="checkbox"
        checked={selectedFirstLevelSpells.includes(spell)}
        disabled={!selectedFirstLevelSpells.includes(spell) && selectedFirstLevelSpells.length >= requiredFirstLevelSpells}
        onChange={() => toggle(spell, selectedFirstLevelSpells, requiredFirstLevelSpells, onFirstLevelSpellsChange)}
      />{spell}</label>)}
      <p>{selectedFirstLevelSpells.length} / {requiredFirstLevelSpells}</p>
    </fieldset>
    {automaticSpells.length > 0 && <p>자동 권역 주문: {automaticSpells.join(', ')}</p>}
  </fieldset>
}

function modelLabel(model: SpellSelectionRule['model']) {
  switch (model) {
    case 'KNOWN': return '습득 주문'
    case 'PREPARED': return '준비 주문'
    case 'SPELLBOOK': return '주문책과 준비 주문'
    case 'PACT': return '계약 마법'
  }
}
