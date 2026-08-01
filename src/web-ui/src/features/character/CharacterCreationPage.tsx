import { useEffect, useMemo, useState } from 'react'
import type { AdventureSessionView, SessionControlMode } from '../adventure-session/AdventureSessionApi'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CreatedCharacterSheetView, PlayPreparationView, SetupApi } from '../rulebooks/SetupApi'
import { calculateDnd5eCharacter, type Ability, type AbilityScores } from './Dnd5eRules'
import { backgroundOptions, classOptions, personalityHelp, raceOptions, STANDARD_ARRAY } from './Dnd5eCharacterCatalog'
import { classCreationRule, resolveEquipment, savingThrowBonuses, spellAttackBonus, spellSaveDc } from './Dnd5eCharacterDerivedRules'
import { subclassesFor } from './Dnd5eSubclassCatalog'
import { calculateAttacks, resolvedWeaponIds, unresolvedWeaponSlots, weaponChoices } from './Dnd5eWeaponRules'

type SessionApi = Pick<AdventureSessionApi, 'read' | 'addMember'>
type CharacterSetupApi = Pick<SetupApi, 'getPlayPreparation' | 'createCharacterSheet'>
const abilities: Ability[] = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma']
const abilityLabels: Record<Ability, string> = { strength: '근력', dexterity: '민첩', constitution: '건강', intelligence: '지능', wisdom: '지혜', charisma: '매력' }
const emptyScores = Object.fromEntries(abilities.map(ability => [ability, 0])) as AbilityScores

export function CharacterCreationPage({ sessionId, setupApi, sessionApi }: { sessionId: string; setupApi: CharacterSetupApi; sessionApi: SessionApi }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [preparation, setPreparation] = useState<PlayPreparationView | null>(null)
  const [name, setName] = useState('')
  const [race, setRace] = useState('')
  const [subrace, setSubrace] = useState('')
  const [characterClass, setCharacterClass] = useState('')
  const [subclass, setSubclass] = useState('')
  const [background, setBackground] = useState('')
  const [scores, setScores] = useState<AbilityScores>(emptyScores)
  const [skills, setSkills] = useState<string[]>([])
  const [equipmentSelections, setEquipmentSelections] = useState<Record<string, string>>({})
  const [weaponSelections, setWeaponSelections] = useState<Record<string, string[]>>({})
  const [cantrips, setCantrips] = useState<string[]>([])
  const [firstLevelSpells, setFirstLevelSpells] = useState<string[]>([])
  const [personality, setPersonality] = useState('')
  const [ideal, setIdeal] = useState('')
  const [bond, setBond] = useState('')
  const [flaw, setFlaw] = useState('')
  const [appearance, setAppearance] = useState('')
  const [mode, setMode] = useState<SessionControlMode>('DIRECT')
  const [created, setCreated] = useState<CreatedCharacterSheetView | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    let active = true
    void sessionApi.read(sessionId).then(next => {
      if (!active) return undefined
      setSession(next)
      return next.scenarioPackageId ? setupApi.getPlayPreparation(next.scenarioPackageId) : undefined
    }).then(next => { if (active && next) setPreparation(next) })
      .catch(error => { if (active) setMessage(error instanceof Error ? error.message : '캐릭터 생성 준비를 불러오지 못했습니다.') })
    return () => { active = false }
  }, [sessionApi, sessionId, setupApi])

  const selectedRace = raceOptions.find(option => option.id === race)
  const selectedSubrace = selectedRace?.subraces.find(option => option.id === subrace)
  const selectedClass = classOptions.find(option => option.id === characterClass)
  const selectedBackground = backgroundOptions.find(option => option.id === background)
  const subclassOptions = subclassesFor(characterClass)
  const selectedSubclass = subclassOptions.find(option => option.id === subclass)
  const subclassRequired = selectedClass?.subclassLevel === 1 && subclassOptions.length > 0
  const creationRule = classCreationRule(characterClass)
  const standardArrayValid = useMemo(() => [...Object.values(scores)].sort((a, b) => b - a).join(',') === [...STANDARD_ARRAY].sort((a, b) => b - a).join(','), [scores])
  const statistics = calculateDnd5eCharacter({ race, subrace, characterClass, level: 1, baseAbilities: scores })
  const classEquipment = resolveEquipment(characterClass, equipmentSelections)
  const backgroundEquipment = selectedBackground?.equipment ?? []
  const equipmentComplete = Boolean(creationRule && creationRule.equipmentGroups.every(group => equipmentSelections[group.id]))
  const weaponSlots = unresolvedWeaponSlots(classEquipment)
  const weaponsComplete = weaponSlots.every(slot => (weaponSelections[slot.id] ?? []).length === slot.count)
  const weaponIds = resolvedWeaponIds(classEquipment, weaponSelections)
  const attacks = calculateAttacks(weaponIds, statistics.abilityModifiers, statistics.proficiencyBonus)
  const requiredCantrips = Math.min(creationRule?.cantripCount ?? 0, selectedClass?.cantrips.length ?? 0)
  const requiredFirstLevel = Math.min(creationRule?.firstLevelSpellCount ?? 0, selectedClass?.firstLevelSpells.length ?? 0)
  const spellsComplete = !selectedClass?.canCastSpells || (cantrips.length === requiredCantrips && firstLevelSpells.length === requiredFirstLevel)
  const spellAttack = spellAttackBonus(characterClass, statistics.abilityModifiers, statistics.proficiencyBonus)
  const spellDc = spellSaveDc(characterClass, statistics.abilityModifiers, statistics.proficiencyBonus)
  const savingThrows = savingThrowBonuses(statistics.abilityModifiers, statistics.savingThrowProficiencies, statistics.proficiencyBonus)
  const canCreate = Boolean(name.trim() && race && characterClass && background && standardArrayValid && selectedClass
    && skills.length === selectedClass.skillChoiceCount && (!selectedRace?.subraces.length || subrace)
    && (!subclassRequired || subclass) && equipmentComplete && weaponsComplete && spellsComplete)
  const blocked = !preparation || preparation.status !== 'READY' || !preparation.characterCreationBlueprint.available
    || preparation.characterCreationBlueprint.status !== 'PUBLISHED'

  function chooseRace(nextRace: string) { setRace(nextRace); setSubrace('') }
  function chooseClass(nextClass: string) {
    setCharacterClass(nextClass)
    setSubclass('')
    setSkills([])
    setEquipmentSelections({})
    setWeaponSelections({})
    setCantrips([])
    setFirstLevelSpells([])
  }
  function toggleLimited(value: string, current: string[], limit: number, setter: (next: string[]) => void) {
    if (current.includes(value)) setter(current.filter(item => item !== value))
    else if (current.length < limit) setter([...current, value])
  }
  function chooseWeapon(slotId: string, weaponId: string, checked: boolean, count: number) {
    setWeaponSelections(current => {
      const selected = current[slotId] ?? []
      const next = checked ? [...selected, weaponId].slice(0, count) : selected.filter(value => value !== weaponId)
      return { ...current, [slotId]: next }
    })
  }

  async function create() {
    if (!session || !preparation || !setupApi.createCharacterSheet || !canCreate || blocked) return
    try {
      const finalStatistics = calculateDnd5eCharacter({ race, subrace, characterClass, level: 1, baseAbilities: scores })
      const finalSavingThrows = savingThrowBonuses(finalStatistics.abilityModifiers, finalStatistics.savingThrowProficiencies, finalStatistics.proficiencyBonus)
      const finalWeaponIds = resolvedWeaponIds(classEquipment, weaponSelections)
      const finalAttacks = calculateAttacks(finalWeaponIds, finalStatistics.abilityModifiers, finalStatistics.proficiencyBonus)
      const concreteClassEquipment = classEquipment.filter(item => !/^(군용|단순) (?:근접 )?무기 \d+개$/.test(item))
      const selectedWeapons = finalWeaponIds.map(id => weaponChoices('SIMPLE').concat(weaponChoices('MARTIAL')).find(option => option.id === id)?.label).filter((value): value is string => Boolean(value))
      const allEquipment = [...concreteClassEquipment, ...selectedWeapons, ...backgroundEquipment]
      const next = await setupApi.createCharacterSheet({
        sessionId, edition: 'DND_5E_2014', characterName: name.trim(), level: 1, inspiration: false,
        race, characterClass, background,
        startingAbilities: abilities.map(ability => `${ability}=${scores[ability]}`).join(','),
        derivedStatistics: JSON.stringify({
          ...finalStatistics, experience: 0, savingThrowBonuses: finalSavingThrows, attacks: finalAttacks,
          spellAttackBonus: spellAttackBonus(characterClass, finalStatistics.abilityModifiers, finalStatistics.proficiencyBonus),
          spellSaveDc: spellSaveDc(characterClass, finalStatistics.abilityModifiers, finalStatistics.proficiencyBonus),
        }),
        characterBuild: JSON.stringify({
          subrace, subclass, subclassFeatures: selectedSubclass?.features ?? [],
          raceLanguages: selectedRace?.languages ?? [], raceTraits: [...(selectedRace?.traits ?? []), ...(selectedSubrace?.traits ?? [])],
          classFeatures: [...(selectedClass?.features ?? []), ...(selectedSubclass?.features ?? [])], classSkills: skills, backgroundSkills: selectedBackground?.skills ?? [],
          equipmentSelections, weaponSelections, equipment: allEquipment, cantrips, firstLevelSpells,
          personality, ideal, bond, flaw, appearance,
          armorProficiencies: creationRule?.armorProficiencies ?? [], weaponProficiencies: creationRule?.weaponProficiencies ?? [],
          toolProficiencies: creationRule?.toolProficiencies ?? [],
        }),
        characterState: JSON.stringify({ currentHitPoints: finalStatistics.hitPointMaximum, temporaryHitPoints: 0, experience: 0 }),
        blueprintRevision: session.blueprintRevision, blueprintValues: {},
      })
      setCreated(next)
      setMessage(`캐릭터 시트 ${next.characterSheetId} 생성 완료. 아래 버튼으로 파티에 추가하세요.`)
    } catch (error) { setMessage(error instanceof Error ? error.message : '캐릭터를 생성하지 못했습니다.') }
  }

  async function addToParty() {
    if (!session || !created) return
    try {
      setSession(await sessionApi.addMember(sessionId, session.version, {
        characterSheetId: created.characterSheetId, controlMode: mode,
        nameMutableAfterStart: false, raceMutableAfterStart: false, characterClassMutableAfterStart: false,
        backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false,
      }))
      setMessage('캐릭터를 파티에 추가했습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '파티 추가에 실패했습니다.') }
  }

  if (!session || !preparation) return <p role="status">{message || '캐릭터 생성 준비를 불러오는 중…'}</p>
  if (blocked) return <section><h2>캐릭터 생성</h2><p role="alert">캐릭터 생성 설정 검토와 게시가 먼저 필요합니다.</p><a href={`#/sessions/${sessionId}/character-blueprint`}>설정 검토 페이지로 이동</a></section>

  return <section aria-labelledby="character-creation-heading">
    <h2 id="character-creation-heading">캐릭터 생성</h2>
    <p>선택해야 하는 항목만 입력합니다. 숙련 보너스, 내성, 공격·주문 수치와 시트 결과는 자동 계산됩니다.</p>
    {message && <p role="status">{message}</p>}

    <fieldset><legend>기본 정보</legend>
      <label>캐릭터 이름 <input aria-label="캐릭터 이름" value={name} onChange={event => setName(event.currentTarget.value)} required /></label>
      <p>레벨: <strong>1</strong> · 경험치: <strong>0</strong> · 숙련 보너스: <strong>+{statistics.proficiencyBonus}</strong></p>
      <small>신규 캐릭터는 1레벨, 0 XP로 시작합니다. 이 값들은 직접 수정하지 않습니다.</small>
    </fieldset>

    <fieldset><legend>종족</legend>
      <p>종족은 능력치 보너스, 이동속도, 언어와 종족 특성을 결정합니다.</p>
      <label>종족 <select aria-label="종족" value={race} onChange={event => chooseRace(event.currentTarget.value)} required><option value="">선택하세요</option>{raceOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>
      {selectedRace && <section aria-label="종족 설명"><p>{selectedRace.description}</p><p>언어: {selectedRace.languages.join(', ')}</p><p>특성: {selectedRace.traits.join(', ') || '없음'}</p></section>}
      {selectedRace && selectedRace.subraces.length > 0 && <label>하위 종족 <select aria-label="하위 종족" value={subrace} onChange={event => setSubrace(event.currentTarget.value)} required><option value="">선택하세요</option>{selectedRace.subraces.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>}
      {selectedSubrace && <p>{selectedSubrace.description} · 특성: {selectedSubrace.traits.join(', ')}</p>}
    </fieldset>

    <fieldset><legend>클래스</legend>
      <p>클래스는 히트 다이스, 내성 숙련, 기술, 시작 장비, 특성과 주문 사용 여부를 결정합니다.</p>
      <label>클래스 <select aria-label="클래스" value={characterClass} onChange={event => chooseClass(event.currentTarget.value)} required><option value="">선택하세요</option>{classOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>
      {selectedClass && <section aria-label="클래스 설명"><p>{selectedClass.description}</p><p>히트 다이스 {selectedClass.hitDie} · 내성 숙련 {selectedClass.savingThrows.map(ability => abilityLabels[ability]).join(', ')}</p><p>자동 특성: {selectedClass.features.join(', ')}</p>{selectedClass.subclassLevel > 1 && <p>하위 클래스는 {selectedClass.subclassLevel}레벨에 선택하므로 지금 입력하지 않습니다.</p>}</section>}
      {subclassRequired && <fieldset><legend>1레벨 하위 클래스</legend><label>하위 클래스 <select aria-label="하위 클래스" value={subclass} onChange={event => setSubclass(event.currentTarget.value)} required><option value="">선택하세요</option>{subclassOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>{selectedSubclass && <section aria-label="하위 클래스 설명"><p>{selectedSubclass.description}</p><p>자동 특성: {selectedSubclass.features.join(', ')}</p></section>}</fieldset>}
      {creationRule && <section aria-label="클래스 숙련"><p>방어구 숙련: {creationRule.armorProficiencies.join(', ') || '없음'}</p><p>무기 숙련: {creationRule.weaponProficiencies.join(', ') || '없음'}</p><p>도구 숙련: {creationRule.toolProficiencies.join(', ') || '없음'}</p></section>}
      {selectedClass && <fieldset><legend>기술 숙련 {selectedClass.skillChoiceCount}개 선택</legend>{selectedClass.skillChoices.map(skill => <label key={skill}><input type="checkbox" checked={skills.includes(skill)} onChange={() => toggleLimited(skill, skills, selectedClass.skillChoiceCount, setSkills)} disabled={!skills.includes(skill) && skills.length >= selectedClass.skillChoiceCount} />{skill}</label>)}<p>{skills.length} / {selectedClass.skillChoiceCount} 선택</p></fieldset>}
      {creationRule && <fieldset><legend>클래스 시작 장비</legend><p>각 묶음에서 하나씩 고릅니다.</p>{creationRule.equipmentGroups.map(group => <label key={group.id}>{group.label} <select aria-label={`장비 ${group.label}`} value={equipmentSelections[group.id] ?? ''} onChange={event => { const value = event.currentTarget.value; setEquipmentSelections(current => ({ ...current, [group.id]: value })); setWeaponSelections({}) }} required><option value="">선택하세요</option>{group.options.map(choice => <option key={choice.id} value={choice.id}>{choice.label}</option>)}</select></label>)}</fieldset>}
      {weaponSlots.length > 0 && <fieldset><legend>실제 무기 선택</legend><p>일반 무기 슬롯을 실제 무기로 확정합니다. 같은 슬롯에서는 요구 개수까지만 선택할 수 있습니다.</p>{weaponSlots.map(slot => <fieldset key={slot.id}><legend>{slot.label}</legend>{weaponChoices(slot.category, slot.label.includes('근접')).map(weapon => { const selected = weaponSelections[slot.id] ?? []; return <label key={weapon.id}><input type="checkbox" checked={selected.includes(weapon.id)} disabled={!selected.includes(weapon.id) && selected.length >= slot.count} onChange={event => chooseWeapon(slot.id, weapon.id, event.currentTarget.checked, slot.count)} />{weapon.label} — {weapon.damage} {weapon.damageType}</label> })}<p>{(weaponSelections[slot.id] ?? []).length} / {slot.count} 선택</p></fieldset>)}</fieldset>}
      {selectedClass?.canCastSpells && creationRule && <fieldset><legend>주문 선택</legend>
        <p>소마법과 1레벨 주문은 각각 정해진 개수만 선택할 수 있습니다.</p>
        <fieldset><legend>소마법 {requiredCantrips}개</legend>{selectedClass.cantrips.map(spell => <label key={spell}><input type="checkbox" checked={cantrips.includes(spell)} onChange={() => toggleLimited(spell, cantrips, requiredCantrips, setCantrips)} disabled={!cantrips.includes(spell) && cantrips.length >= requiredCantrips} />{spell}</label>)}<p>{cantrips.length} / {requiredCantrips}</p></fieldset>
        <fieldset><legend>1레벨 주문 {requiredFirstLevel}개</legend>{selectedClass.firstLevelSpells.map(spell => <label key={spell}><input type="checkbox" checked={firstLevelSpells.includes(spell)} onChange={() => toggleLimited(spell, firstLevelSpells, requiredFirstLevel, setFirstLevelSpells)} disabled={!firstLevelSpells.includes(spell) && firstLevelSpells.length >= requiredFirstLevel} />{spell}</label>)}<p>{firstLevelSpells.length} / {requiredFirstLevel}</p></fieldset>
      </fieldset>}
    </fieldset>

    <fieldset><legend>배경</legend>
      <p>배경은 과거의 삶, 기술 숙련, 시작 장비와 역할극 예시를 제공합니다.</p>
      <label>배경 <select aria-label="배경" value={background} onChange={event => setBackground(event.currentTarget.value)} required><option value="">선택하세요</option>{backgroundOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>
      {selectedBackground && <section aria-label="배경 설명"><p>{selectedBackground.description}</p><p>자동 기술 숙련: {selectedBackground.skills.join(', ')}</p><p>자동 시작 장비: {selectedBackground.equipment.join(', ')}</p></section>}
    </fieldset>

    <fieldset><legend>능력치 — 표준 배열</legend>
      <p>15, 14, 13, 12, 10, 8을 각각 정확히 한 번씩 배정합니다.</p>
      {abilities.map(ability => { const usedByOther = abilities.filter(item => item !== ability).map(item => scores[item]); return <label key={ability}>{abilityLabels[ability]} <select aria-label={abilityLabels[ability]} value={scores[ability] || ''} onChange={event => { const value = Number(event.currentTarget.value); setScores(current => ({ ...current, [ability]: value })) }} required><option value="">선택</option>{STANDARD_ARRAY.map(value => <option key={value} value={value} disabled={usedByOther.includes(value)}>{value}</option>)}</select></label> })}
      {!standardArrayValid && <p role="alert">표준 배열의 여섯 값을 중복 없이 모두 배정하세요.</p>}
    </fieldset>

    {selectedBackground && <fieldset><legend>성격과 동기 — 선택 사항</legend>
      <LabeledSuggestion label="인격 특성" help={personalityHelp.personality} value={personality} setValue={setPersonality} suggestions={selectedBackground.personality} />
      <LabeledSuggestion label="이상" help={personalityHelp.ideal} value={ideal} setValue={setIdeal} suggestions={selectedBackground.ideals} />
      <LabeledSuggestion label="유대" help={personalityHelp.bond} value={bond} setValue={setBond} suggestions={selectedBackground.bonds} />
      <LabeledSuggestion label="단점" help={personalityHelp.flaw} value={flaw} setValue={setFlaw} suggestions={selectedBackground.flaws} />
    </fieldset>}

    <fieldset><legend>외형 — 선택 사항</legend><p>키·나이·몸무게를 나누지 않고 한 문단으로 묘사합니다.</p><label>외형 묘사 <textarea aria-label="외형 묘사" value={appearance} onChange={event => setAppearance(event.currentTarget.value)} /></label></fieldset>

    <section aria-label="자동 계산 결과"><h3>자동 계산되는 캐릭터 시트 값</h3>
      <p>숙련 보너스 +{statistics.proficiencyBonus} · 이동속도 {statistics.speed || '?'}ft · 최대 HP {statistics.hitPointMaximum || '?'} · 히트 다이스 {statistics.hitDie || '?'}</p>
      <p>방어도 {statistics.armorClass} · 우선권 {formatModifier(statistics.abilityModifiers.dexterity)}</p>
      <p>내성 굴림: {abilities.map(ability => `${abilityLabels[ability]} ${formatModifier(savingThrows[ability])}`).join(' · ')}</p>
      {spellAttack != null && <p>주문 공격 보너스 {formatModifier(spellAttack)} · 주문 내성 DC {spellDc}</p>}
      <p>클래스 장비: {classEquipment.join(', ') || '선택 중'}{backgroundEquipment.length > 0 ? ` · 배경 장비: ${backgroundEquipment.join(', ')}` : ''}</p>
      <p>기술 숙련: {[...skills, ...(selectedBackground?.skills ?? [])].filter((value, index, all) => all.indexOf(value) === index).join(', ') || '선택 중'}</p>
      <h4>공격 및 무기</h4>{attacks.length > 0 ? <ul aria-label="공격 목록">{attacks.map(attack => <li key={attack.weaponId}>{attack.label}: 명중 {formatModifier(attack.attackBonus)}, 피해 {attack.damage} {attack.damageType}{attack.range ? `, 사거리 ${attack.range}` : ''}</li>)}</ul> : <p>무기를 선택하면 공격 수치가 자동 계산됩니다.</p>}
    </section>

    <button type="button" onClick={() => void create()} disabled={!canCreate}>캐릭터 생성</button>
    {!canCreate && <p>이름, 종족·하위 종족, 필요한 하위 클래스, 클래스 기술, 장비·무기, 배경, 표준 배열과 주문 선택을 확인하세요.</p>}

    <section aria-label="파티 구성"><h3>일행 추가</h3><p>텍스트 ID를 직접 쓰지 않고 생성된 캐릭터를 현재 세션 파티에 추가합니다.</p>
      {session.party.length > 0 ? <ul>{session.party.map(member => <li key={member.characterSheetId}>{member.characterSheetId} · {member.controlMode}</li>)}</ul> : <p>아직 파티원이 없습니다.</p>}
      {created && <><label>조작 방식 <select aria-label="조작 방식" value={mode} onChange={event => setMode(event.currentTarget.value as SessionControlMode)}><option value="DIRECT">직접 조작</option><option value="AGENT">에이전트 조작</option></select></label><button type="button" onClick={() => void addToParty()}>생성한 캐릭터를 파티에 추가</button></>}
      <small>일행과의 관계는 캐릭터 생성 필수값이 아니며 별도 세션 메모에서 관리합니다.</small>
    </section>
  </section>
}

function LabeledSuggestion({ label, help, value, setValue, suggestions }: { label: string; help: string; value: string; setValue: (value: string) => void; suggestions: string[] }) {
  return <section><h4>{label}</h4><p>{help}</p><label>{label} <select aria-label={`${label} 예시`} value={suggestions.includes(value) ? value : ''} onChange={event => setValue(event.currentTarget.value)}><option value="">예시에서 선택하지 않음</option>{suggestions.map(item => <option key={item} value={item}>{item}</option>)}</select></label><label>직접 작성 <textarea aria-label={`${label} 직접 작성`} value={value} onChange={event => setValue(event.currentTarget.value)} /></label></section>
}
function formatModifier(value: number) { return value >= 0 ? `+${value}` : String(value) }
