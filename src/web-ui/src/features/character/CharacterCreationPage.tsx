import { useEffect, useMemo, useState } from 'react'
import type { AdventureSessionView, SessionControlMode } from '../adventure-session/AdventureSessionApi'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CreatedCharacterSheetView, PlayPreparationView, SetupApi } from '../rulebooks/SetupApi'
import { calculateDnd5eCharacter, type Ability, type AbilityScores } from './Dnd5eRules'
import { backgroundOptions, classOptions, personalityHelp, raceOptions, STANDARD_ARRAY } from './Dnd5eCharacterCatalog'
import { backgroundRule, choicesComplete, classChoiceRequirements, raceChoiceRequirements, selectedChoiceValues } from './Dnd5eCharacterChoiceRules'
import { classCreationRule, inferArmorLoadout, resolveEquipment, savingThrowBonuses, spellAttackBonus, spellSaveDc } from './Dnd5eCharacterDerivedRules'
import { validateArmorEquipment } from './Dnd5eArmorEquipmentRules'
import { calculateCombatAttacks, defaultEquipmentState, validateEquipmentState, type EquippedItemState } from './Dnd5eEquipmentStateRules'
import { expertiseChoiceCount, passivePerception, skillBonuses, uniqueProficiencies } from './Dnd5eSheetDerivedRules'
import { domainSpells, selectionCount, spellSelectionRule } from './Dnd5eSpellPreparationRules'
import { applySubclassArmorClass, subclassEffects } from './Dnd5eSubclassEffects'
import { subclassesFor } from './Dnd5eSubclassCatalog'
import { resolvedWeaponIds, unresolvedWeaponSlots, weaponChoices, weaponOptions } from './Dnd5eWeaponRules'
import { CharacterRuleChoices } from './CharacterRuleChoices'
import { CharacterEquipmentLoadout } from './CharacterEquipmentLoadout'
import { CharacterSpellSelection } from './CharacterSpellSelection'
import { CharacterSkillSelection } from './CharacterSkillSelection'
import { CharacterAbilityScores } from './CharacterAbilityScores'
import { CharacterIdentitySelection } from './CharacterIdentitySelection'

type SessionApi = Pick<AdventureSessionApi, 'read' | 'addMember'>
type CharacterSetupApi = Pick<SetupApi, 'getPlayPreparation' | 'createCharacterSheet'>
const abilities: Ability[] = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma']
const abilityLabels: Record<Ability, string> = { strength: '근력', dexterity: '민첩', constitution: '건강', intelligence: '지능', wisdom: '지혜', charisma: '매력' }
const emptyScores = Object.fromEntries(abilities.map(ability => [ability, 0])) as AbilityScores
const emptyEquipmentState: EquippedItemState = { armor: '', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: null }

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
  const [expertise, setExpertise] = useState<string[]>([])
  const [ruleChoices, setRuleChoices] = useState<Record<string, string[]>>({})
  const [equipmentSelections, setEquipmentSelections] = useState<Record<string, string>>({})
  const [weaponSelections, setWeaponSelections] = useState<Record<string, string[]>>({})
  const [equipmentState, setEquipmentState] = useState<EquippedItemState>(emptyEquipmentState)
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
  const selectedBackgroundRule = backgroundRule(background)
  const subclassOptions = subclassesFor(characterClass)
  const selectedSubclass = subclassOptions.find(option => option.id === subclass)
  const subclassRequired = selectedClass?.subclassLevel === 1 && subclassOptions.length > 0
  const creationRule = classCreationRule(characterClass)
  const classEquipment = resolveEquipment(characterClass, equipmentSelections)
  const inferredArmor = inferArmorLoadout(classEquipment)
  const equipmentComplete = Boolean(creationRule && creationRule.equipmentGroups.every(group => equipmentSelections[group.id]))
  const weaponSlots = unresolvedWeaponSlots(classEquipment)
  const weaponsComplete = weaponSlots.every(slot => (weaponSelections[slot.id] ?? []).length === slot.count)
  const weaponIds = resolvedWeaponIds(classEquipment, weaponSelections)
  const equipmentSignature = `${weaponIds.join('|')}::${inferredArmor.equippedArmor}::${inferredArmor.equippedShield}`

  useEffect(() => {
    setEquipmentState(defaultEquipmentState(weaponIds, inferredArmor.equippedArmor, inferredArmor.equippedShield))
  }, [equipmentSignature])

  const standardArrayValid = useMemo(() => [...Object.values(scores)].sort((a, b) => b - a).join(',') === [...STANDARD_ARRAY].sort((a, b) => b - a).join(','), [scores])
  const baseStatistics = calculateDnd5eCharacter({ race, subrace, characterClass, level: 1, baseAbilities: scores, equippedArmor: equipmentState.armor, equippedShield: equipmentState.shield })
  const activeSubclassEffects = subclassEffects(subclass, baseStatistics.abilityModifiers)
  const statistics = { ...baseStatistics, armorClass: applySubclassArmorClass(baseStatistics.armorClass, equipmentState.armor, activeSubclassEffects) }
  const finalArmorProficiencies = uniqueProficiencies(creationRule?.armorProficiencies ?? [], activeSubclassEffects.armorProficiencies)
  const finalWeaponProficiencies = uniqueProficiencies(creationRule?.weaponProficiencies ?? [], activeSubclassEffects.weaponProficiencies)
  const equipmentConflicts = validateEquipmentState(weaponIds, equipmentState)
  const armorIssues = validateArmorEquipment(characterClass, equipmentState.armor, finalArmorProficiencies)
  const attacks = calculateCombatAttacks(characterClass, equipmentState, weaponIds, statistics.abilityModifiers, statistics.proficiencyBonus)
  const backgroundEquipment = selectedBackground?.equipment ?? []
  const spellRule = spellSelectionRule(characterClass, statistics.abilityModifiers, 1)
  const requiredCantrips = Math.min(spellRule?.cantripCount ?? 0, selectedClass?.cantrips.length ?? 0)
  const requiredFirstLevel = Math.min(selectionCount(spellRule), selectedClass?.firstLevelSpells.length ?? 0)
  const automaticDomainSpells = domainSpells(subclass)
  const spellsComplete = !spellRule || (cantrips.length === requiredCantrips && firstLevelSpells.length === requiredFirstLevel)
  const spellAttack = spellAttackBonus(characterClass, statistics.abilityModifiers, statistics.proficiencyBonus)
  const spellDc = spellSaveDc(characterClass, statistics.abilityModifiers, statistics.proficiencyBonus)
  const savingThrows = savingThrowBonuses(statistics.abilityModifiers, statistics.savingThrowProficiencies, statistics.proficiencyBonus)
  const allSkillProficiencies = uniqueProficiencies(skills, selectedBackground?.skills ?? [])
  const requiredExpertise = expertiseChoiceCount(characterClass, 1)
  const validExpertise = expertise.filter(skill => allSkillProficiencies.includes(skill))
  const calculatedSkills = skillBonuses(statistics.abilityModifiers, statistics.proficiencyBonus, allSkillProficiencies, validExpertise)
  const raceRequirements = raceChoiceRequirements(race, subrace)
  const classRequirements = classChoiceRequirements(characterClass)
  const backgroundRequirements = selectedBackgroundRule?.choiceRequirements ?? []
  const allChoiceRequirements = [...raceRequirements, ...classRequirements, ...backgroundRequirements]
  const choicesValid = choicesComplete(allChoiceRequirements, ruleChoices)
  const equipmentStateValid = equipmentConflicts.length === 0 && armorIssues.length === 0
  const canCreate = Boolean(name.trim() && race && characterClass && background && standardArrayValid && selectedClass
    && skills.length === selectedClass.skillChoiceCount && (!selectedRace?.subraces.length || subrace)
    && (!subclassRequired || subclass) && validExpertise.length === requiredExpertise
    && equipmentComplete && weaponsComplete && equipmentStateValid && spellsComplete && choicesValid)
  const blocked = !preparation || preparation.status !== 'READY' || !preparation.characterCreationBlueprint.available || preparation.characterCreationBlueprint.status !== 'PUBLISHED'

  function resetDependentChoices() { setRuleChoices({}); setExpertise([]) }
  function chooseClass(next: string) {
    setCharacterClass(next); setSubclass(''); setSkills([]); setEquipmentSelections({}); setWeaponSelections({}); setEquipmentState(emptyEquipmentState); setCantrips([]); setFirstLevelSpells([]); resetDependentChoices()
  }
  function chooseBackground(next: string) { setBackground(next); resetDependentChoices() }
  function chooseWeapon(slotId: string, weaponId: string, checked: boolean, count: number) {
    setWeaponSelections(current => { const selected = current[slotId] ?? []; return { ...current, [slotId]: checked ? [...selected, weaponId].slice(0, count) : selected.filter(value => value !== weaponId) } })
  }

  async function create() {
    if (!session || !preparation || !setupApi.createCharacterSheet || !canCreate || blocked) return
    try {
      const finalSkills = skillBonuses(statistics.abilityModifiers, statistics.proficiencyBonus, allSkillProficiencies, validExpertise)
      const concreteClassEquipment = classEquipment.filter(item => !/^(군용|단순) (?:근접 )?무기 \d+개$/.test(item))
      const selectedWeapons = weaponIds.map(id => weaponOptions.find(option => option.id === id)?.label).filter((value): value is string => Boolean(value))
      const ownedEquipment = [...concreteClassEquipment, ...selectedWeapons, ...backgroundEquipment]
      const languages = uniqueProficiencies((selectedRace?.languages ?? []).filter(value => !value.startsWith('선택 언어')), selectedChoiceValues(raceRequirements, ruleChoices), selectedChoiceValues(backgroundRequirements.filter(item => item.label.includes('언어')), ruleChoices))
      const tools = uniqueProficiencies(creationRule?.toolProficiencies ?? [], selectedChoiceValues(classRequirements, ruleChoices), selectedChoiceValues(backgroundRequirements.filter(item => !item.label.includes('언어')), ruleChoices))
      const preparedSpells = uniqueProficiencies(firstLevelSpells, automaticDomainSpells)
      const next = await setupApi.createCharacterSheet({
        sessionId, edition: 'DND_5E_2014', characterName: name.trim(), level: 1, inspiration: false,
        race, characterClass, background,
        startingAbilities: abilities.map(ability => `${ability}=${scores[ability]}`).join(','),
        derivedStatistics: JSON.stringify({ ...statistics, experience: 0, savingThrowBonuses: savingThrows, attacks, skillBonuses: finalSkills, passivePerception: passivePerception(finalSkills), spellAttackBonus: spellAttack, spellSaveDc: spellDc, spellSlots: spellRule ? [{ level: 1, slots: spellRule.firstLevelSlots, recovery: spellRule.recovery }] : [] }),
        characterBuild: JSON.stringify({
          schemaVersion: 2, subrace, subclass, subclassFeatures: selectedSubclass?.features ?? [], subclassEffects: activeSubclassEffects,
          backgroundFeature: selectedBackgroundRule?.feature ?? null, ruleChoices, languages, toolProficiencies: tools,
          raceTraits: [...(selectedRace?.traits ?? []), ...(selectedSubrace?.traits ?? [])], classFeatures: [...(selectedClass?.features ?? []), ...(selectedSubclass?.features ?? [])],
          skillProficiencies: allSkillProficiencies, expertise: validExpertise,
          equipmentSelections, weaponSelections, ownedEquipment, ownedWeaponIds: weaponIds, equippedItems: equipmentState,
          cantrips: uniqueProficiencies(cantrips, activeSubclassEffects.bonusCantrips), spellModel: spellRule?.model ?? null,
          learnedOrPreparedSpells: preparedSpells, domainSpells: automaticDomainSpells,
          armorProficiencies: finalArmorProficiencies, weaponProficiencies: finalWeaponProficiencies,
          personality, ideal, bond, flaw, appearance,
        }),
        characterState: JSON.stringify({ currentHitPoints: statistics.hitPointMaximum, temporaryHitPoints: 0, experience: 0, equippedItems: equipmentState, ammunition: {}, spellSlots: spellRule ? [{ level: 1, maximum: spellRule.firstLevelSlots, remaining: spellRule.firstLevelSlots, recovery: spellRule.recovery }] : [] }),
        blueprintRevision: session.blueprintRevision, blueprintValues: {},
      })
      setCreated(next); setMessage(`캐릭터 시트 ${next.characterSheetId} 생성 완료. 아래 버튼으로 파티에 추가하세요.`)
    } catch (error) { setMessage(error instanceof Error ? error.message : '캐릭터를 생성하지 못했습니다.') }
  }

  async function addToParty() {
    if (!session || !created) return
    try {
      setSession(await sessionApi.addMember(sessionId, session.version, { characterSheetId: created.characterSheetId, controlMode: mode, nameMutableAfterStart: false, raceMutableAfterStart: false, characterClassMutableAfterStart: false, backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false }))
      setMessage('캐릭터를 파티에 추가했습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '파티 추가에 실패했습니다.') }
  }

  if (!session || !preparation) return <p role="status">{message || '캐릭터 생성 준비를 불러오는 중…'}</p>
  if (blocked) return <section><h2>캐릭터 생성</h2><p role="alert">캐릭터 생성 설정 검토와 게시가 먼저 필요합니다.</p><a href={`#/sessions/${sessionId}/character-blueprint`}>설정 검토 페이지로 이동</a></section>

  return <section aria-labelledby="character-creation-heading">
    <h2 id="character-creation-heading">캐릭터 생성</h2>{message && <p role="status">{message}</p>}
    <CharacterIdentitySelection
      name={name}
      race={race}
      subrace={subrace}
      proficiencyBonus={statistics.proficiencyBonus}
      raceOptions={raceOptions}
      onNameChange={setName}
      onRaceChange={next => { setRace(next); resetDependentChoices() }}
      onSubraceChange={next => { setSubrace(next); resetDependentChoices() }}
    />
    <fieldset><legend>클래스</legend><label>클래스 <select aria-label="클래스" value={characterClass} onChange={event => chooseClass(event.currentTarget.value)}><option value="">선택하세요</option>{classOptions.map(option => <option key={option.id}>{option.id}</option>)}</select></label>
      {subclassRequired && <label>하위 클래스 <select aria-label="하위 클래스" value={subclass} onChange={event => setSubclass(event.currentTarget.value)}><option value="">선택하세요</option>{subclassOptions.map(option => <option key={option.id}>{option.id}</option>)}</select></label>}
      {selectedClass && <CharacterSkillSelection skillOptions={selectedClass.skillChoices} skillChoiceCount={selectedClass.skillChoiceCount} selectedSkills={skills} proficientSkills={allSkillProficiencies} expertiseChoiceCount={requiredExpertise} selectedExpertise={validExpertise} onSkillsChange={setSkills} onExpertiseChange={setExpertise} />}
      {creationRule && <fieldset><legend>클래스 시작 장비</legend>{creationRule.equipmentGroups.map(group => <label key={group.id}>{group.label} <select aria-label={`장비 ${group.label}`} value={equipmentSelections[group.id] ?? ''} onChange={event => { const value = event.currentTarget.value; setEquipmentSelections(current => ({ ...current, [group.id]: value })); setWeaponSelections({}) }}><option value="">선택하세요</option>{group.options.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>)}</fieldset>}
      {weaponSlots.map(slot => <fieldset key={slot.id}><legend>{slot.label}</legend>{weaponChoices(slot.category, slot.label.includes('근접')).map(weapon => { const selected = weaponSelections[slot.id] ?? []; return <label key={weapon.id}><input type="checkbox" checked={selected.includes(weapon.id)} disabled={!selected.includes(weapon.id) && selected.length >= slot.count} onChange={event => chooseWeapon(slot.id, weapon.id, event.currentTarget.checked, slot.count)} />{weapon.label} — {weapon.damage} {weapon.damageType}</label> })}</fieldset>)}
      <CharacterEquipmentLoadout ownedWeaponIds={weaponIds} availableArmor={inferredArmor.equippedArmor} shieldAvailable={inferredArmor.equippedShield} state={equipmentState} conflicts={equipmentConflicts} armorIssues={armorIssues} onChange={setEquipmentState} />
      <CharacterSpellSelection rule={spellRule} cantripOptions={selectedClass?.cantrips ?? []} firstLevelOptions={selectedClass?.firstLevelSpells ?? []} selectedCantrips={cantrips} selectedFirstLevelSpells={firstLevelSpells} requiredCantrips={requiredCantrips} requiredFirstLevelSpells={requiredFirstLevel} automaticSpells={automaticDomainSpells} onCantripsChange={setCantrips} onFirstLevelSpellsChange={setFirstLevelSpells} />
    </fieldset>
    <fieldset><legend>배경</legend><label>배경 <select aria-label="배경" value={background} onChange={event => chooseBackground(event.currentTarget.value)}><option value="">선택하세요</option>{backgroundOptions.map(option => <option key={option.id}>{option.id}</option>)}</select></label>{selectedBackgroundRule && <p><strong>{selectedBackgroundRule.feature.name}</strong>: {selectedBackgroundRule.feature.description}</p>}</fieldset>
    <CharacterRuleChoices requirements={allChoiceRequirements} selections={ruleChoices} onChange={(id, values) => setRuleChoices(current => ({ ...current, [id]: values }))} />
    <CharacterAbilityScores abilities={abilities} standardArray={STANDARD_ARRAY} scores={scores} onChange={setScores} />
    {selectedBackground && <fieldset><legend>성격과 동기 — 선택 사항</legend><LabeledSuggestion label="인격 특성" help={personalityHelp.personality} value={personality} setValue={setPersonality} suggestions={selectedBackground.personality} /><LabeledSuggestion label="이상" help={personalityHelp.ideal} value={ideal} setValue={setIdeal} suggestions={selectedBackground.ideals} /><LabeledSuggestion label="유대" help={personalityHelp.bond} value={bond} setValue={setBond} suggestions={selectedBackground.bonds} /><LabeledSuggestion label="단점" help={personalityHelp.flaw} value={flaw} setValue={setFlaw} suggestions={selectedBackground.flaws} /></fieldset>}
    <fieldset><legend>외형 — 선택 사항</legend><textarea aria-label="외형 묘사" value={appearance} onChange={event => setAppearance(event.currentTarget.value)} /></fieldset>
    <section aria-label="자동 계산 결과"><h3>자동 계산 결과</h3><p>방어도 {statistics.armorClass} · 최대 HP {statistics.hitPointMaximum || '?'} · 수동 지각 {passivePerception(calculatedSkills)}</p><p>내성 굴림: {abilities.map(ability => `${abilityLabels[ability]} ${formatModifier(savingThrows[ability])}`).join(' · ')}</p>{spellRule && <p>주문 공격 {formatModifier(spellAttack ?? 0)} · 주문 DC {spellDc} · 1레벨 슬롯 {spellRule.firstLevelSlots}</p>}<ul aria-label="기술 보너스">{calculatedSkills.map(skill => <li key={skill.id}>{skill.label} {formatModifier(skill.bonus)}</li>)}</ul><ul aria-label="공격 목록">{attacks.map(attack => <li key={`${attack.weaponId}-${attack.mode}`}>{attack.label}: 명중 {formatModifier(attack.attackBonus)}, 피해 {attack.damage} {attack.damageType}{attack.versatileDamage ? ` · 양손 ${attack.versatileDamage}` : ''}{attack.range ? ` · 사거리 ${attack.range}` : ''}{attack.ammunitionRequired ? ' · 탄약 필요' : ''}</li>)}</ul></section>
    <button type="button" onClick={() => void create()} disabled={!canCreate}>캐릭터 생성</button>{!canCreate && <p>필수 선택과 장착 상태를 모두 확인하세요.</p>}
    <section aria-label="파티 구성"><h3>일행 추가</h3>{session.party.map(member => <p key={member.characterSheetId}>{member.characterSheetId}</p>)}{created && <><select aria-label="조작 방식" value={mode} onChange={event => setMode(event.currentTarget.value as SessionControlMode)}><option value="DIRECT">직접 조작</option><option value="AGENT">에이전트 조작</option></select><button type="button" onClick={() => void addToParty()}>생성한 캐릭터를 파티에 추가</button></>}</section>
  </section>
}

function LabeledSuggestion({ label, help, value, setValue, suggestions }: { label: string; help: string; value: string; setValue: (value: string) => void; suggestions: string[] }) {
  return <section><h4>{label}</h4><p>{help}</p><select aria-label={`${label} 예시`} value={suggestions.includes(value) ? value : ''} onChange={event => setValue(event.currentTarget.value)}><option value="">예시에서 선택하지 않음</option>{suggestions.map(item => <option key={item}>{item}</option>)}</select><textarea aria-label={`${label} 직접 작성`} value={value} onChange={event => setValue(event.currentTarget.value)} /></section>
}
function formatModifier(value: number) { return value >= 0 ? `+${value}` : String(value) }
