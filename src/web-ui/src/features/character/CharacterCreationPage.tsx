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
import { resolvedWeaponIds, unresolvedWeaponSlots, weaponOptions } from './Dnd5eWeaponRules'
import { CharacterRuleChoices } from './CharacterRuleChoices'
import { CharacterEquipmentLoadout } from './CharacterEquipmentLoadout'
import { CharacterSpellSelection } from './CharacterSpellSelection'
import { CharacterSkillSelection } from './CharacterSkillSelection'
import { CharacterAbilityScores } from './CharacterAbilityScores'
import { CharacterIdentitySelection } from './CharacterIdentitySelection'
import { CharacterClassSelection } from './CharacterClassSelection'
import { CharacterRoleplayDetails, type RoleplayDetails } from './CharacterRoleplayDetails'
import { CharacterDerivedPreview } from './CharacterDerivedPreview'
import { CharacterPartyStep } from './CharacterPartyStep'

type SessionApi = Pick<AdventureSessionApi, 'read' | 'addMember'>
type CharacterSetupApi = Pick<SetupApi, 'getPlayPreparation' | 'createCharacterSheet'>
const abilities: Ability[] = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma']
const emptyScores = Object.fromEntries(abilities.map(ability => [ability, 0])) as AbilityScores
const emptyEquipmentState: EquippedItemState = { armor: '', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: null }
const emptyRoleplay: RoleplayDetails = { personality: '', ideal: '', bond: '', flaw: '', appearance: '' }

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
  const [roleplay, setRoleplay] = useState<RoleplayDetails>(emptyRoleplay)
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
          ...roleplay,
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
    <CharacterIdentitySelection name={name} race={race} subrace={subrace} proficiencyBonus={statistics.proficiencyBonus} raceOptions={raceOptions} onNameChange={setName} onRaceChange={next => { setRace(next); resetDependentChoices() }} onSubraceChange={next => { setSubrace(next); resetDependentChoices() }} />
    <CharacterClassSelection classOptions={classOptions} characterClass={characterClass} subclass={subclass} subclassOptions={subclassOptions} subclassRequired={subclassRequired} equipmentGroups={creationRule?.equipmentGroups ?? []} equipmentSelections={equipmentSelections} weaponSlots={weaponSlots} weaponSelections={weaponSelections} onClassChange={chooseClass} onSubclassChange={setSubclass} onEquipmentChange={(groupId, value) => { setEquipmentSelections(current => ({ ...current, [groupId]: value })); setWeaponSelections({}) }} onWeaponSelectionsChange={(slotId, values) => setWeaponSelections(current => ({ ...current, [slotId]: values }))}>
      {selectedClass && <CharacterSkillSelection skillOptions={selectedClass.skillChoices} skillChoiceCount={selectedClass.skillChoiceCount} selectedSkills={skills} proficientSkills={allSkillProficiencies} expertiseChoiceCount={requiredExpertise} selectedExpertise={validExpertise} onSkillsChange={setSkills} onExpertiseChange={setExpertise} />}
      <CharacterEquipmentLoadout ownedWeaponIds={weaponIds} availableArmor={inferredArmor.equippedArmor} shieldAvailable={inferredArmor.equippedShield} state={equipmentState} conflicts={equipmentConflicts} armorIssues={armorIssues} onChange={setEquipmentState} />
      <CharacterSpellSelection rule={spellRule} cantripOptions={selectedClass?.cantrips ?? []} firstLevelOptions={selectedClass?.firstLevelSpells ?? []} selectedCantrips={cantrips} selectedFirstLevelSpells={firstLevelSpells} requiredCantrips={requiredCantrips} requiredFirstLevelSpells={requiredFirstLevel} automaticSpells={automaticDomainSpells} onCantripsChange={setCantrips} onFirstLevelSpellsChange={setFirstLevelSpells} />
    </CharacterClassSelection>
    <fieldset><legend>배경</legend><label>배경 <select aria-label="배경" value={background} onChange={event => chooseBackground(event.currentTarget.value)}><option value="">선택하세요</option>{backgroundOptions.map(option => <option key={option.id}>{option.id}</option>)}</select></label>{selectedBackgroundRule && <p><strong>{selectedBackgroundRule.feature.name}</strong>: {selectedBackgroundRule.feature.description}</p>}</fieldset>
    <CharacterRuleChoices requirements={allChoiceRequirements} selections={ruleChoices} onChange={(id, values) => setRuleChoices(current => ({ ...current, [id]: values }))} />
    <CharacterAbilityScores abilities={abilities} standardArray={STANDARD_ARRAY} scores={scores} onChange={setScores} />
    <CharacterRoleplayDetails background={selectedBackground} help={personalityHelp} values={roleplay} onChange={setRoleplay} />
    <CharacterDerivedPreview armorClass={statistics.armorClass} hitPointMaximum={statistics.hitPointMaximum} passivePerception={passivePerception(calculatedSkills)} savingThrows={savingThrows} skills={calculatedSkills} attacks={attacks} spell={spellRule ? { attackBonus: spellAttack ?? 0, saveDc: spellDc, firstLevelSlots: spellRule.firstLevelSlots } : undefined} />
    <button type="button" onClick={() => void create()} disabled={!canCreate}>캐릭터 생성</button>{!canCreate && <p>필수 선택과 장착 상태를 모두 확인하세요.</p>}
    <CharacterPartyStep partyMemberIds={session.party.map(member => member.characterSheetId)} createdCharacterSheetId={created?.characterSheetId} mode={mode} onModeChange={setMode} onAdd={() => void addToParty()} />
  </section>
}
