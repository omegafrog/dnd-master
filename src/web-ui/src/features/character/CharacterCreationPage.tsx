import { useEffect, useMemo, useState } from 'react'
import type { AdventureSessionView, SessionControlMode } from '../adventure-session/AdventureSessionApi'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CreatedCharacterSheetView, PlayPreparationView, SetupApi } from '../rulebooks/SetupApi'
import { calculateDnd5eCharacter, type Ability, type AbilityScores } from './Dnd5eRules'
import { backgroundOptions, classOptions, personalityHelp, raceOptions, STANDARD_ARRAY } from './Dnd5eCharacterCatalog'

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
  const [background, setBackground] = useState('')
  const [scores, setScores] = useState<AbilityScores>(emptyScores)
  const [skills, setSkills] = useState<string[]>([])
  const [equipment, setEquipment] = useState<string[]>([])
  const [spells, setSpells] = useState<string[]>([])
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
  const selectedClass = classOptions.find(option => option.id === characterClass)
  const selectedBackground = backgroundOptions.find(option => option.id === background)
  const standardArrayValid = useMemo(() => [...Object.values(scores)].sort((a, b) => b - a).join(',') === [...STANDARD_ARRAY].sort((a, b) => b - a).join(','), [scores])
  const statistics = calculateDnd5eCharacter({ race, subrace, characterClass, level: 1, baseAbilities: scores })
  const canCreate = Boolean(name.trim() && race && characterClass && background && standardArrayValid
    && selectedClass && skills.length === selectedClass.skillChoiceCount
    && (!selectedRace?.subraces.length || subrace)
    && (!selectedClass.canCastSpells || spells.length > 0))
  const blocked = !preparation || preparation.status !== 'READY' || !preparation.characterCreationBlueprint.available
    || preparation.characterCreationBlueprint.status !== 'PUBLISHED'

  function chooseRace(nextRace: string) {
    setRace(nextRace)
    setSubrace('')
  }

  function chooseClass(nextClass: string) {
    setCharacterClass(nextClass)
    setSkills([])
    setEquipment([])
    setSpells([])
  }

  function toggleLimited(value: string, current: string[], limit: number, setter: (next: string[]) => void) {
    if (current.includes(value)) setter(current.filter(item => item !== value))
    else if (current.length < limit) setter([...current, value])
  }

  async function create() {
    if (!session || !preparation || !setupApi.createCharacterSheet || !canCreate || blocked) return
    try {
      const derivedStatistics = calculateDnd5eCharacter({ race, subrace, characterClass, level: 1, baseAbilities: scores })
      const next = await setupApi.createCharacterSheet({
        sessionId,
        edition: 'DND_5E_2014',
        characterName: name.trim(),
        level: 1,
        inspiration: false,
        race,
        characterClass,
        background,
        startingAbilities: abilities.map(ability => `${ability}=${scores[ability]}`).join(','),
        derivedStatistics: JSON.stringify({ ...derivedStatistics, experience: 0 }),
        characterBuild: JSON.stringify({ subrace, skills, equipment, spells, personality, ideal, bond, flaw, appearance }),
        characterState: JSON.stringify({ currentHitPoints: derivedStatistics.hitPointMaximum, temporaryHitPoints: 0, experience: 0 }),
        blueprintRevision: session.blueprintRevision,
        blueprintValues: {},
      })
      setCreated(next)
      setMessage(`캐릭터 시트 ${next.characterSheetId} 생성 완료. 아래 버튼으로 파티에 추가하세요.`)
    } catch (error) { setMessage(error instanceof Error ? error.message : '캐릭터를 생성하지 못했습니다.') }
  }

  async function addToParty() {
    if (!session || !created) return
    try {
      setSession(await sessionApi.addMember(sessionId, session.version, {
        characterSheetId: created.characterSheetId,
        controlMode: mode,
        nameMutableAfterStart: false,
        raceMutableAfterStart: false,
        characterClassMutableAfterStart: false,
        backgroundMutableAfterStart: false,
        startingAbilitiesMutableAfterStart: false,
        levelMutableAfterStart: false,
      }))
      setMessage('캐릭터를 파티에 추가했습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '파티 추가에 실패했습니다.') }
  }

  if (!session || !preparation) return <p role="status">{message || '캐릭터 생성 준비를 불러오는 중…'}</p>
  if (blocked) return <section><h2>캐릭터 생성</h2><p role="alert">캐릭터 생성 설정 검토와 게시가 먼저 필요합니다.</p><a href={`#/sessions/${sessionId}/character-blueprint`}>설정 검토 페이지로 이동</a></section>

  return <section aria-labelledby="character-creation-heading">
    <h2 id="character-creation-heading">캐릭터 생성</h2>
    <p>사용자가 선택해야 하는 항목만 입력합니다. 계산되는 시트 값은 자동으로 표시됩니다.</p>
    {message && <p role="status">{message}</p>}

    <fieldset><legend>기본 정보</legend>
      <label>캐릭터 이름 <input aria-label="캐릭터 이름" value={name} onChange={event => setName(event.currentTarget.value)} required /></label>
      <p>레벨: <strong>1</strong> · 경험치: <strong>0</strong> · 숙련 보너스: <strong>+2</strong></p>
      <small>신규 캐릭터는 1레벨, 0 XP로 시작하며 숙련 보너스는 레벨에서 자동 계산됩니다.</small>
    </fieldset>

    <fieldset><legend>종족</legend>
      <p>종족은 능력치 보너스, 이동속도, 언어와 종족 특성을 결정합니다.</p>
      <label>종족 <select aria-label="종족" value={race} onChange={event => chooseRace(event.currentTarget.value)} required><option value="">선택하세요</option>{raceOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>
      {selectedRace && <p>{selectedRace.description}</p>}
      {selectedRace && selectedRace.subraces.length > 0 && <label>하위 종족 <select aria-label="하위 종족" value={subrace} onChange={event => setSubrace(event.currentTarget.value)} required><option value="">선택하세요</option>{selectedRace.subraces.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>}
      {subrace && <p>{selectedRace?.subraces.find(option => option.id === subrace)?.description}</p>}
    </fieldset>

    <fieldset><legend>클래스</legend>
      <p>클래스는 히트 다이스, 내성 숙련, 기술 선택, 시작 장비, 클래스 특성과 주문 사용 여부를 결정합니다.</p>
      <label>클래스 <select aria-label="클래스" value={characterClass} onChange={event => chooseClass(event.currentTarget.value)} required><option value="">선택하세요</option>{classOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>
      {selectedClass && <section aria-label="클래스 설명"><p>{selectedClass.description}</p><p>히트 다이스 {selectedClass.hitDie} · 내성 숙련 {selectedClass.savingThrows.map(ability => abilityLabels[ability]).join(', ')}</p><p>자동 특성: {selectedClass.features.join(', ')}</p>{selectedClass.subclassLevel > 1 && <p>하위 클래스는 {selectedClass.subclassLevel}레벨에 선택하므로 지금 입력하지 않습니다.</p>}</section>}
      {selectedClass && <fieldset><legend>기술 숙련 {selectedClass.skillChoiceCount}개 선택</legend>{selectedClass.skillChoices.map(skill => <label key={skill}><input type="checkbox" checked={skills.includes(skill)} onChange={() => toggleLimited(skill, skills, selectedClass.skillChoiceCount, setSkills)} disabled={!skills.includes(skill) && skills.length >= selectedClass.skillChoiceCount} />{skill}</label>)}<p>{skills.length} / {selectedClass.skillChoiceCount} 선택</p></fieldset>}
      {selectedClass && <fieldset><legend>클래스 시작 장비 선택</legend>{selectedClass.equipmentChoices.map(item => <label key={item}><input type="checkbox" checked={equipment.includes(item)} onChange={() => setEquipment(current => current.includes(item) ? current.filter(value => value !== item) : [...current, item])} />{item}</label>)}</fieldset>}
      {selectedClass?.canCastSpells && <fieldset><legend>1레벨 주문 선택</legend><p>클래스가 사용할 수 있는 목록에서 고릅니다. 주문 공격 보너스와 주문 내성 DC는 자동 계산됩니다.</p>{selectedClass.spellChoices.map(spell => <label key={spell}><input type="checkbox" checked={spells.includes(spell)} onChange={() => setSpells(current => current.includes(spell) ? current.filter(value => value !== spell) : [...current, spell])} />{spell}</label>)}</fieldset>}
    </fieldset>

    <fieldset><legend>배경</legend>
      <p>배경은 과거의 삶, 배경 기술 숙련, 시작 장비와 역할극 예시를 제공합니다.</p>
      <label>배경 <select aria-label="배경" value={background} onChange={event => setBackground(event.currentTarget.value)} required><option value="">선택하세요</option>{backgroundOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}</select></label>
      {selectedBackground && <section aria-label="배경 설명"><p>{selectedBackground.description}</p><p>기술 숙련: {selectedBackground.skills.join(', ')}</p><p>자동 시작 장비: {selectedBackground.equipment.join(', ')}</p></section>}
    </fieldset>

    <fieldset><legend>능력치 — 표준 배열</legend>
      <p>15, 14, 13, 12, 10, 8을 각각 정확히 한 번씩 배정합니다. 이미 사용한 값은 다른 능력치에서 선택할 수 없습니다.</p>
      {abilities.map(ability => { const usedByOther = abilities.filter(item => item !== ability).map(item => scores[item]); return <label key={ability}>{abilityLabels[ability]} <select aria-label={abilityLabels[ability]} value={scores[ability] || ''} onChange={event => setScores(current => ({ ...current, [ability]: Number(event.currentTarget.value) }))} required><option value="">선택</option>{STANDARD_ARRAY.map(value => <option key={value} value={value} disabled={usedByOther.includes(value)}>{value}</option>)}</select></label> })}
      {!standardArrayValid && <p role="alert">표준 배열의 여섯 값을 중복 없이 모두 배정하세요.</p>}
    </fieldset>

    {selectedBackground && <fieldset><legend>성격과 동기 — 선택 사항</legend>
      <LabeledSuggestion label="인격 특성" help={personalityHelp.personality} value={personality} setValue={setPersonality} suggestions={selectedBackground.personality} />
      <LabeledSuggestion label="이상" help={personalityHelp.ideal} value={ideal} setValue={setIdeal} suggestions={selectedBackground.ideals} />
      <LabeledSuggestion label="유대" help={personalityHelp.bond} value={bond} setValue={setBond} suggestions={selectedBackground.bonds} />
      <LabeledSuggestion label="단점" help={personalityHelp.flaw} value={flaw} setValue={setFlaw} suggestions={selectedBackground.flaws} />
    </fieldset>}

    <fieldset><legend>외형 — 선택 사항</legend><p>키·나이·몸무게를 따로 입력하지 않습니다. 외모, 복장과 첫인상을 한 문단으로 묘사하세요.</p><label>외형 묘사 <textarea aria-label="외형 묘사" value={appearance} onChange={event => setAppearance(event.currentTarget.value)} /></label></fieldset>

    <section aria-label="자동 계산 결과"><h3>자동 계산되는 캐릭터 시트 값</h3>
      <p>숙련 보너스 +{statistics.proficiencyBonus} · 이동속도 {statistics.speed || '?'}ft · 최대 HP {statistics.hitPointMaximum || '?'} · 히트 다이스 {statistics.hitDie || '?'}</p>
      <p>방어도 {statistics.armorClass} · 우선권 {formatModifier(statistics.abilityModifiers.dexterity)} · 내성 숙련 {statistics.savingThrowProficiencies.map(ability => abilityLabels[ability]).join(', ') || '?'}</p>
      <p>종족/클래스 특성, 공격 및 주문 수치, 숙련과 언어는 선택한 조합으로 저장 시 자동 산출됩니다.</p>
    </section>

    <button type="button" onClick={() => void create()} disabled={!canCreate}>캐릭터 생성</button>
    {!canCreate && <p>이름, 종족·하위 종족, 클래스 기술, 배경, 표준 배열과 주문 선택을 확인하세요.</p>}

    <section aria-label="파티 구성"><h3>일행 추가</h3><p>텍스트 ID를 직접 쓰지 않습니다. 먼저 생성된 캐릭터를 현재 세션의 파티에 추가합니다.</p>
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
