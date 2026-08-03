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
import { expertiseChoiceCount, passivePerception, skillBonuses, skillDefinitions, uniqueProficiencies } from './Dnd5eSheetDerivedRules'
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
import { evaluateCharacterBuild, getCharacterRulesCatalog, type CharacterBuildEvaluationView, type CharacterRulesCatalogView } from './CharacterRulesApi'

type SessionApi = Pick<AdventureSessionApi, 'read' | 'addMember'>
type CharacterSetupApi = {
  getPlayPreparation: NonNullable<SetupApi['getPlayPreparation']>
  createCharacterSheet?: SetupApi['createCharacterSheet']
}
const abilities: Ability[] = ['strength', 'dexterity', 'constitution', 'intelligence', 'wisdom', 'charisma']
const emptyScores = Object.fromEntries(abilities.map(ability => [ability, 0])) as AbilityScores
const emptyEquipmentState: EquippedItemState = { armor: '', shield: false, mainHandWeaponId: null, offHandWeaponId: null, twoHandedWeaponId: null }
const emptyRoleplay: RoleplayDetails = { personality: '', ideal: '', bond: '', flaw: '', appearance: '' }

export function CharacterCreationPage({ sessionId, setupApi, sessionApi }: { sessionId: string; setupApi: CharacterSetupApi; sessionApi: SessionApi }) {
  const [session, setSession] = useState<AdventureSessionView | null>(null)
  const [preparation, setPreparation] = useState<PlayPreparationView | null>(null)
  const [catalog, setCatalog] = useState<CharacterRulesCatalogView | null>(null)
  const [evaluation, setEvaluation] = useState<CharacterBuildEvaluationView | null>(null)
  const [evaluating, setEvaluating] = useState(false)
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
    void Promise.all([sessionApi.read(sessionId), getCharacterRulesCatalog()]).then(async ([nextSession, nextCatalog]) => {
      if (!active) return
      setSession(nextSession)
      setCatalog(nextCatalog)
      if (nextSession.scenarioPackageId) {
        const nextPreparation = await setupApi.getPlayPreparation(nextSession.scenarioPackageId)
        if (active) setPreparation(nextPreparation)
      }
    }).catch(error => { if (active) setMessage(error instanceof Error ? error.message : '캐릭터 생성 준비를 불러오지 못했습니다.') })
    return () => { active = false }
  }, [sessionApi, sessionId, setupApi])

  const availableRaceOptions = catalog ? raceOptions.filter(option => catalog.races.includes(option.id)) : []
  const availableClassOptions = catalog ? classOptions.filter(option => catalog.classes.includes(option.id)) : []
  const availableBackgroundOptions = catalog ? backgroundOptions.filter(option => catalog.backgrounds.includes(option.id)) : []
  const selectedRace = availableRaceOptions.find(option => option.id === race)
  const selectedSubrace = selectedRace?.subraces.find(option => option.id === subrace)
  const selectedClass = availableClassOptions.find(option => option.id === characterClass)
  const selectedBackground = availableBackgroundOptions.find(option => option.id === background)
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

  const concreteClassEquipment = classEquipment.filter(item => !/^(군용|단순) (?:근접 )?무기 \d+개$/.test(item))
  const selectedWeapons = weaponIds.map(id => weaponOptions.find(option => option.id === id)?.label).filter((value): value is string => Boolean(value))
  const ownedEquipment = [...concreteClassEquipment, ...selectedWeapons, ...backgroundEquipment]
  const languages = uniqueProficiencies((selectedRace?.languages ?? []).filter(value => !value.startsWith('선택 언어')), selectedChoiceValues(raceRequirements, ruleChoices), selectedChoiceValues(backgroundRequirements.filter(item => item.label.includes('언어')), ruleChoices))
  const tools = uniqueProficiencies(creationRule?.toolProficiencies ?? [], selectedChoiceValues(classRequirements, ruleChoices), selectedChoiceValues(backgroundRequirements.filter(item => !item.label.includes('언어')), ruleChoices))
  const preparedSpells = uniqueProficiencies(firstLevelSpells, automaticDomainSpells)
  const characterBuild = JSON.stringify({
    schemaVersion: 2, rulesetRevision: catalog?.revision ?? null, subrace, subclass,
    subclassFeatures: selectedSubclass?.features ?? [], subclassEffects: activeSubclassEffects,
    backgroundFeature: selectedBackgroundRule?.feature ?? null, ruleChoices, languages, toolProficiencies: tools,
    raceTraits: [...(selectedRace?.traits ?? []), ...(selectedSubrace?.traits ?? [])], classFeatures: [...(selectedClass?.features ?? []), ...(selectedSubclass?.features ?? [])],
    skillProficiencies: allSkillProficiencies, expertise: validExpertise,
    equipmentSelections, weaponSelections, ownedEquipment, ownedWeaponIds: weaponIds, equippedItems: equipmentState,
    cantrips: uniqueProficiencies(cantrips, activeSubclassEffects.bonusCantrips), spellModel: spellRule?.model ?? null,
    learnedOrPreparedSpells: preparedSpells, domainSpells: automaticDomainSpells,
    armorProficiencies: finalArmorProficiencies, weaponProficiencies: finalWeaponProficiencies,
    ...roleplay,
  })
  const characterState = JSON.stringify({ currentHitPoints: statistics.hitPointMaximum, temporaryHitPoints: 0, experience: 0, equippedItems: equipmentState, ammunition: {}, spellSlots: spellRule ? [{ level: 1, maximum: spellRule.firstLevelSlots, remaining: spellRule.firstLevelSlots, recovery: spellRule.recovery }] : [] })
  const startingAbilities = abilities.map(ability => `${ability}=${scores[ability]}`).join(',')
  const localComplete = Boolean(name.trim() && race && characterClass && background && standardArrayValid && selectedClass
    && skills.length === selectedClass.skillChoiceCount && (!selectedRace?.subraces.length || subrace)
    && (!subclassRequired || subclass) && validExpertise.length === requiredExpertise
    && equipmentComplete && weaponsComplete && equipmentStateValid && spellsComplete && choicesValid)

  useEffect(() => {
    if (!catalog || !race || !characterClass) {
      setEvaluation(null)
      return
    }
    let active = true
    const timer = window.setTimeout(() => {
      setEvaluating(true)
      void evaluateCharacterBuild(sessionId, {
        sessionId, edition: 'DND_5E_2014', characterName: name.trim() || '미완성 캐릭터', level: 1, inspiration: false,
        race, characterClass, background, startingAbilities, characterBuild, characterState, blueprintRevision: session?.blueprintRevision, blueprintValues: {},
      }).then(next => { if (active) setEvaluation(next) })
        .catch(error => { if (active) setMessage(error instanceof Error ? error.message : '캐릭터 규칙 평가에 실패했습니다.') })
        .finally(() => { if (active) setEvaluating(false) })
    }, 200)
    return () => { active = false; window.clearTimeout(timer) }
  }, [background, catalog, characterBuild, characterClass, characterState, name, race, session?.blueprintRevision, sessionId, startingAbilities])

  const canCreate = localComplete && evaluation?.valid === true && !evaluating
  const blocked = !preparation || preparation.status !== 'READY' || !preparation.characterCreationBlueprint.available || preparation.characterCreationBlueprint.status !== 'PUBLISHED'
  const serverDerived = evaluation?.derived
  const previewSkills = serverDerived?.skillBonuses
    ? skillDefinitions.map(skill => ({ ...skill, ...(serverDerived.skillBonuses?.[skill.label] ?? { proficient: false, expertise: false, bonus: statistics.abilityModifiers[skill.ability] }) }))
    : calculatedSkills
  const previewAttacks = serverDerived?.attacks?.map(attack => ({ ...attack, range: attack.range ?? undefined, versatileDamage: attack.versatileDamage ?? undefined })) ?? attacks

  function resetDependentChoices() { setRuleChoices({}); setExpertise([]) }
  function chooseClass(next: string) {
    setCharacterClass(next); setSubclass(''); setSkills([]); setEquipmentSelections({}); setWeaponSelections({}); setEquipmentState(emptyEquipmentState); setCantrips([]); setFirstLevelSpells([]); resetDependentChoices()
  }
  function chooseBackground(next: string) { setBackground(next); resetDependentChoices() }

  async function create() {
    if (!session || !preparation || !setupApi.createCharacterSheet || !canCreate || blocked) return
    try {
      const next = await setupApi.createCharacterSheet({
        sessionId, edition: 'DND_5E_2014', characterName: name.trim(), level: 1, inspiration: false,
        race, characterClass, background, startingAbilities, characterBuild, characterState,
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

  if (!session || !preparation || !catalog) return <p role="status">{message || '캐릭터 생성 준비를 불러오는 중…'}</p>
  if (blocked) return <section><h2>캐릭터 생성</h2><p role="alert">캐릭터 생성 설정 검토와 게시가 먼저 필요합니다.</p><a href={`#/sessions/${sessionId}/character-blueprint`}>설정 검토 페이지로 이동</a></section>

  return <section aria-labelledby="character-creation-heading">
    <h2 id="character-creation-heading">캐릭터 생성</h2>{message && <p role="status">{message}</p>}
    <p>규칙 카탈로그: {catalog.baseSchema} revision {catalog.revision}</p>
    <CharacterIdentitySelection name={name} race={race} subrace={subrace} proficiencyBonus={serverDerived?.proficiencyBonus ?? statistics.proficiencyBonus} raceOptions={availableRaceOptions} onNameChange={setName} onRaceChange={next => { setRace(next); setSubrace(''); resetDependentChoices() }} onSubraceChange={next => { setSubrace(next); resetDependentChoices() }} />
    <CharacterClassSelection classOptions={availableClassOptions} characterClass={characterClass} subclass={subclass} subclassOptions={subclassOptions} subclassRequired={subclassRequired} equipmentGroups={creationRule?.equipmentGroups ?? []} equipmentSelections={equipmentSelections} weaponSlots={weaponSlots} weaponSelections={weaponSelections} onClassChange={chooseClass} onSubclassChange={setSubclass} onEquipmentChange={(groupId, value) => { setEquipmentSelections(current => ({ ...current, [groupId]: value })); setWeaponSelections({}) }} onWeaponSelectionsChange={(slotId, values) => setWeaponSelections(current => ({ ...current, [slotId]: values }))}>
      {selectedClass && <CharacterSkillSelection skillOptions={selectedClass.skillChoices} skillChoiceCount={selectedClass.skillChoiceCount} selectedSkills={skills} proficientSkills={allSkillProficiencies} expertiseChoiceCount={requiredExpertise} selectedExpertise={validExpertise} onSkillsChange={setSkills} onExpertiseChange={setExpertise} />}
      <CharacterEquipmentLoadout ownedWeaponIds={weaponIds} availableArmor={inferredArmor.equippedArmor} shieldAvailable={inferredArmor.equippedShield} state={equipmentState} conflicts={equipmentConflicts} armorIssues={armorIssues} onChange={setEquipmentState} />
      <CharacterSpellSelection rule={spellRule} cantripOptions={selectedClass?.cantrips ?? []} firstLevelOptions={selectedClass?.firstLevelSpells ?? []} selectedCantrips={cantrips} selectedFirstLevelSpells={firstLevelSpells} requiredCantrips={requiredCantrips} requiredFirstLevelSpells={requiredFirstLevel} automaticSpells={automaticDomainSpells} onCantripsChange={setCantrips} onFirstLevelSpellsChange={setFirstLevelSpells} />
    </CharacterClassSelection>
    <fieldset><legend>배경</legend><label>배경 <select aria-label="배경" value={background} onChange={event => chooseBackground(event.currentTarget.value)}><option value="">선택하세요</option>{availableBackgroundOptions.map(option => <option key={option.id}>{option.id}</option>)}</select></label>{selectedBackgroundRule && <p><strong>{selectedBackgroundRule.feature.name}</strong>: {selectedBackgroundRule.feature.description}</p>}</fieldset>
    <CharacterRuleChoices requirements={allChoiceRequirements} selections={ruleChoices} onChange={(id, values) => setRuleChoices(current => ({ ...current, [id]: values }))} />
    <CharacterAbilityScores abilities={abilities} standardArray={STANDARD_ARRAY} scores={scores} onChange={setScores} />
    <CharacterRoleplayDetails background={selectedBackground} help={personalityHelp} values={roleplay} onChange={setRoleplay} />
    <CharacterDerivedPreview armorClass={serverDerived?.armorClass ?? statistics.armorClass} hitPointMaximum={serverDerived?.hitPointMaximum ?? statistics.hitPointMaximum} passivePerception={serverDerived?.passivePerception ?? passivePerception(calculatedSkills)} savingThrows={serverDerived?.savingThrowBonuses ?? savingThrows} skills={previewSkills} attacks={previewAttacks} spell={spellRule ? { attackBonus: serverDerived?.spellAttackBonus ?? spellAttack ?? 0, saveDc: serverDerived?.spellSaveDc ?? spellDc, firstLevelSlots: spellRule.firstLevelSlots } : undefined} />
    {evaluation && !evaluation.valid && <ul aria-label="서버 규칙 위반">{evaluation.violations.map(violation => <li key={`${violation.code}-${violation.message}`}>{violation.message}</li>)}</ul>}
    <button type="button" onClick={() => void create()} disabled={!canCreate}>캐릭터 생성</button>{!canCreate && <p>{evaluating ? '서버 규칙을 확인하는 중입니다.' : '필수 선택과 서버 규칙 판정을 모두 확인하세요.'}</p>}
    <CharacterPartyStep partyMemberIds={session.party.map(member => member.characterSheetId)} createdCharacterSheetId={created?.characterSheetId} mode={mode} onModeChange={setMode} onAdd={() => void addToParty()} />
  </section>
}
