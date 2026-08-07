import { type FormEvent, StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { AdventureStream } from '../../src/features/adventure/AdventureStream'
import type { AdventureApi } from '../../src/features/adventure/AdventureApi'
import { HttpAdventureApi } from '../../src/features/adventure/AdventureApi'
import { AuthProvider, useAuth } from '../../src/features/auth/AuthContext'
import { LoginForm } from '../../src/features/auth/LoginForm'
import type { IdentityApi } from '../../src/features/auth/IdentityApi'
import { CharacterSheetView } from '../../src/features/character/CharacterSheetView'
import { CharacterCreationPage } from '../../src/features/character/CharacterCreationPage'
import { backgroundOptions, classOptions, raceOptions } from '../../src/features/character/Dnd5eCharacterCatalog'
import { CombatMapView } from '../../src/features/combat-map/CombatMapView'
import { RoleDiceRoller } from '../../src/features/dice/RoleDiceRoller'
import { RuleEvidence } from '../../src/features/rule-guidance/RuleEvidence'
import type { RuleGuidanceApi } from '../../src/features/rule-guidance/RuleGuidanceApi'
import { RulebookSetup } from '../../src/features/rulebooks/RulebookSetup'
import type { SetupApi } from '../../src/features/rulebooks/SetupApi'
import type { AdventurePlayApi, SavedAdventure } from '../../src/features/saved-adventures/AdventurePlayApi'
import { SavedAdventurePanel } from '../../src/features/saved-adventures/SavedAdventurePanel'
import { AdventureSessionPanel } from '../../src/features/adventure-session/AdventureSessionPanel'
import { AdventureStoryPlanPage } from '../../src/features/adventure-session/AdventureStoryPlanPage'
import type { AdventureSessionView } from '../../src/features/adventure-session/AdventureSessionApi'

const backendUrl = import.meta.env.VITE_BACKEND_E2E_URL as string | undefined
const backendAdventureId = import.meta.env.VITE_BACKEND_E2E_ADVENTURE_ID as string | undefined
const backendPlayerId = import.meta.env.VITE_BACKEND_E2E_PLAYER_ID as string | undefined
const adventureId = backendAdventureId ?? 'adventure-e2e'
const acceptanceJourneyMode = window.location.search.includes('acceptance-journey')

const e2eState = {
  bundle: null as unknown,
  blueprint: null as unknown,
  creationRequest: null as unknown,
  blueprintStatus: 'NEEDS_REVIEW' as 'NEEDS_REVIEW' | 'READY' | 'PUBLISHED',
  blueprintRevision: 2,
  blueprintValues: { 'node-str': '12' } as Record<string, string>,
  creation: null as unknown,
  turnEvidence: JSON.parse(sessionStorage.getItem('dnd-master-e2e-turn-evidence') ?? '[]') as Array<{ version: number; sourceRefs: string[]; rolls: string[]; initiative: number; attackTotal?: number; damage?: number; targetHp: number }>,
}

const identityApi: IdentityApi = {
  async login() {
    return {
      accessToken: 'owner-token', playerName: '테스터', playerId: backendPlayerId ?? 'player-e2e',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    }
  },
  async logout() {},
}

const privateDocuments: Array<{ ownerId: string; knowledgeDocumentId: string; originalFilename: string; documentType: 'RULEBOOK' | 'STORYBOOK' }> = []

const setupApi: SetupApi = {
  async uploadRulebooks(documents, _ownerId) {
    return documents.map(document => {
      const knowledgeDocumentId = `${document.file.name}-${document.documentType}`
      if (!privateDocuments.some(existing => existing.knowledgeDocumentId === knowledgeDocumentId)) {
        privateDocuments.push({
          ownerId: _ownerId,
          knowledgeDocumentId,
          documentType: document.documentType,
          originalFilename: document.file.name,
        })
      }
      sessionStorage.setItem('dnd-master-e2e-documents', JSON.stringify(privateDocuments))
      return {
        knowledgeDocumentId,
        documentType: document.documentType,
        originalFilename: document.file.name,
        status: 'ACCEPTED' as const,
      }
    })
  },
  async getRulebookStatus(rulebookId) { return { rulebookId, status: 'INDEXED' } },
  async uploadScenario(file) { return { id: 'scenario-e2e', name: file.name } },
  async createCharacterSheet(draft) {
    if (e2eState.blueprintStatus !== 'PUBLISHED') throw new Error('Blueprint must be published before character creation')
    if (draft.blueprintRevision !== 4 || draft.blueprintValues?.['node-str'] !== '13') {
      throw new Error('Character creation must use the published blueprint values')
    }
    e2eState.creationRequest = draft
    const result = {
      characterSheetId: 'sheet-e2e',
      adventureId: adventureId,
      edition: 'DND_5E_2024',
      characterName: draft.characterName,
      level: 1,
      inspiration: false,
      version: 0,
    }
    e2eState.creation = { draft, result }
    return result
  },
  async createScenarioBundle(ownerId, documents) {
    e2eState.bundle = {
      bundleId: 'bundle-e2e',
      ownerPlayerId: ownerId,
      currentRevision: 1,
      documents: documents.map(document => ({
        ...document,
        originalFilename: privateDocuments.find(item => item.knowledgeDocumentId === document.knowledgeDocumentId)?.originalFilename ?? document.knowledgeDocumentId,
        documentType: privateDocuments.find(item => item.knowledgeDocumentId === document.knowledgeDocumentId)?.documentType ?? 'STORYBOOK',
        status: 'EXTRACTED' as const,
        extractionVersion: 1,
      })),
    }
    return e2eState.bundle as Awaited<ReturnType<SetupApi['createScenarioBundle']>>
  },
  async reviseScenarioBundle(ownerId, documents) {
    return this.createScenarioBundle(ownerId, documents)
  },
  async getScenarioBundle() {
    return e2eState.bundle as Awaited<ReturnType<SetupApi['getScenarioBundle']>>
  },
  async startScenarioCompilation() {
    return { compilationId: 'compilation-e2e', bundleId: 'bundle-e2e', bundleRevision: 1, status: 'REQUESTED' as const, attempt: 0, packageId: null, failureReason: null }
  },
  async getScenarioCompilation() {
    return { compilationId: 'compilation-e2e', bundleId: 'bundle-e2e', bundleRevision: 1, status: 'PUBLISHED' as const, attempt: 1, packageId: 'package-e2e', failureReason: null }
  },
  async getScenarioPackage() {
    return {
      packageId: 'package-e2e', bundleId: 'bundle-e2e', bundleRevision: 1, inputFingerprint: 'document-derived', reportStatus: 'COMPLETE' as const, warnings: [],
      characterLimit: { maximumCharacters: 1, source: null, sourceQuote: '' }, units: [],
    }
  },
  async getPlayPreparation() {
    const preparation = {
      scenarioPackageId: 'package-e2e', bundleId: 'bundle-e2e', bundleRevision: 1, status: 'READY' as const, blockers: [],
      characterLimit: { maximumCharacters: 1, source: null, sourceQuote: '' },
      characterCreationBlueprint: {
        available: true, summary: 'DND 4판 · DND 5판 · Storybook 우선 옵션: Elf', rulebookDocumentCount: 2, storybookDocumentCount: 1,
        diagnostics: [], revision: e2eState.blueprintRevision, status: e2eState.blueprintStatus, fields: [], roots: [{
          id: 'node-scores', parentId: null, key: 'starting_ability_scores', label: 'Scores', inputMode: 'FREE_TEXT' as const,
          value: null, options: [], suggestions: [], status: 'EXTRACTED' as const, allowUserAddChild: false, confidence: 'HIGH',
          sourceQuote: '', diagnostics: [], sourceEvidence: [], children: [{
            id: 'node-str', parentId: 'node-scores', key: 'str', label: 'STR', inputMode: 'FREE_TEXT' as const,
            value: e2eState.blueprintValues['node-str'], options: [], suggestions: [], status: e2eState.blueprintStatus === 'NEEDS_REVIEW' ? 'CONFLICT_REVIEW' as const : 'REVIEWED' as const, allowUserAddChild: false, confidence: 'HIGH',
            sourceQuote: 'STR from DND 5판', diagnostics: e2eState.blueprintStatus === 'NEEDS_REVIEW' ? ['conflicting rulebook/storybook values'] : [], sourceEvidence: [
              { knowledgeDocumentId: 'rules-2024.txt-RULEBOOK', extractionVersion: 1, locator: 'page:2' },
              { knowledgeDocumentId: 'storybook.txt-STORYBOOK', extractionVersion: 1, locator: 'page:2' },
            ], children: [],
          }],
        }],
      },
    }
    e2eState.blueprint = preparation.characterCreationBlueprint
    return preparation
  },
  async publishBlueprint() {
    if (e2eState.blueprintStatus !== 'READY') throw new Error('Blueprint must be ready before publishing')
    e2eState.blueprintStatus = 'PUBLISHED'
    e2eState.blueprintRevision += 1
  },
  async resolveBlueprint(_scenarioPackageId, nodeId, value) {
    e2eState.blueprintValues[nodeId] = value
    e2eState.blueprintStatus = 'READY'
    e2eState.blueprintRevision += 1
  },
  async saveRuleSet() {},
  async listKnowledgeDocuments(ownerId) {
    return privateDocuments
      .filter(document => document.ownerId === ownerId)
      .map(document => ({
        knowledgeDocumentId: document.knowledgeDocumentId,
        documentType: document.documentType,
        originalFilename: document.originalFilename,
        status: 'EXTRACTED' as const,
        format: 'TXT' as const,
        extractionVersion: 1,
        warnings: [],
      }))
  },
}

Object.assign(window, { __dndMasterE2E: e2eState })

const fixtureAdventureApi: AdventureApi = {
  async readConversation() {
    const entries = JSON.parse(sessionStorage.getItem('dnd-master-e2e-conversation') ?? '[]') as Array<{ sequence: number; speaker: string; content: string }>
    return { adventureId, version: Number(sessionStorage.getItem('dnd-master-e2e-turn-version') ?? '0'), entries }
  },
  async sendMessage(_adventureId, message, command, expectedVersion) {
    const commandKey = command?.commandId
    const commandResults = JSON.parse(sessionStorage.getItem('dnd-master-e2e-command-results') ?? '{}') as Record<string, { message: string; response: Awaited<ReturnType<NonNullable<AdventureApi['sendMessage']>>> }>
    if (commandKey && commandResults[commandKey]) {
      if (commandResults[commandKey].message !== message) throw new Error('같은 commandId에 다른 입력을 재사용할 수 없습니다.')
      return commandResults[commandKey].response
    }
    const currentVersion = Number(sessionStorage.getItem('dnd-master-e2e-turn-version') ?? '0')
    if (expectedVersion !== undefined && expectedVersion !== currentVersion) throw new Error('턴 cursor가 최신 상태가 아닙니다.')
    const version = currentVersion + 1
    const entries = JSON.parse(sessionStorage.getItem('dnd-master-e2e-conversation') ?? '[]') as Array<{ sequence: number; speaker: string; content: string }>
    const refused = window.location.search.includes('grounding-refusal')
    const fullJourneyNarrations = [
      '지각 판정에 따라 양조장 안쪽에서 움직임과 희미한 발자국을 알아챘다.',
      '내성 굴림 결과, 코를 찌르는 증기를 버티고 의식을 유지했다.',
      '다중 주사위 결과로 흔적을 따라가자 저장고로 이어지는 길을 찾았다.',
      '전투 시작: 적이 모습을 드러냈고, 먼저 행동할 차례를 확인한다.',
      '명중 굴림과 피해를 적용했다. 위협이 사라져 전투 종료, 다음 단서로 이어진다.',
    ]
    const uploadedDocuments = JSON.parse(sessionStorage.getItem('dnd-master-e2e-documents') ?? '[]') as Array<{ documentType: string; originalFilename: string }>
    const bundledDocuments = (e2eState.bundle as { documents?: Array<{ documentType: string; originalFilename: string }> } | null)?.documents ?? []
    const groundingDocuments = [...privateDocuments, ...uploadedDocuments, ...bundledDocuments]
    const story = groundingDocuments.find(document => document.documentType === 'STORYBOOK')
    const rulebook = groundingDocuments.find(document => document.documentType === 'RULEBOOK')
    const sourceRefs = [story && `storybook:${story.originalFilename}:brewery`, rulebook && `rulebook:${rulebook.originalFilename}:dnd5e`].filter((value): value is string => Boolean(value))
    const rolls = version === 1 ? ['perception:1d20+3=16'] : version === 2 ? ['constitution-save:1d20+2=14'] : version === 3 ? ['trail:2d6=8'] : version === 5 ? ['attack:1d20+5=20', 'damage:1d8+3=9'] : []
    const targetHp = version >= 5 ? 0 : 9
    const narration = refused
      ? '아직 확인된 근거가 없어 결과를 말할 수 없습니다.'
      : (window.location.search.includes('full-journey') || acceptanceJourneyMode)
        ? `턴 ${version}: 근거를 바탕으로 응답한다. ${fullJourneyNarrations[version - 1] ?? '현재 장면을 안전하게 이어간다.'}`
        : '근거를 바탕으로 응답한다.'
    entries.push({ sequence: entries.length + 1, speaker: 'PLAYER', content: message }, { sequence: entries.length + 2, speaker: 'AI_GAME_MASTER', content: narration })
    sessionStorage.setItem('dnd-master-e2e-turn-version', String(version))
    sessionStorage.setItem('dnd-master-e2e-conversation', JSON.stringify(entries))
    const response = {
      narration,
      judgment: refused ? 'pending judgment' : rolls.length > 0 ? rolls.join(', ') : '판정 완료',
      currentScene: '새 장면',
      sourceRefs,
      warnings: refused ? ['degraded-mode:RULE;repair-attempted=true;refusal-reason=fixture-only'] : [],
      version,
    }
    e2eState.turnEvidence.push({ version, sourceRefs, rolls, initiative: 12, ...(version === 5 ? { attackTotal: 20, damage: 9 } : {}), targetHp })
    sessionStorage.setItem('dnd-master-e2e-turn-evidence', JSON.stringify(e2eState.turnEvidence))
    if (commandKey) {
      commandResults[commandKey] = { message, response }
      sessionStorage.setItem('dnd-master-e2e-command-results', JSON.stringify(commandResults))
    }
    sessionStorage.setItem('dnd-master-e2e-target-hp', String(targetHp))
    return response
  },
}

const adventureApi: AdventureApi = backendUrl && backendAdventureId && backendPlayerId
  ? new HttpAdventureApi(() => 'e2e-proxy-token', () => backendPlayerId)
  : fixtureAdventureApi

let saved: SavedAdventure[] = []
const playApi: AdventurePlayApi = {
  async getCharacter() {
    return { characterSheetId: 'sheet-e2e', name: 'Aria', edition: '2024', armorClass: 16, strength: 14, dexterity: 18, constitution: 12, intelligence: 10, wisdom: 13, charisma: 15 }
  },
  async getCombatMap() {
    if (window.location.search.includes('full-journey') || acceptanceJourneyMode) {
      const position = JSON.parse(sessionStorage.getItem('dnd-master-e2e-map-position') ?? '{"x":0,"y":0}') as { x: number; y: number }
      const turnVersion = Number(sessionStorage.getItem('dnd-master-e2e-turn-version') ?? '0')
      const combatResolved = Number(sessionStorage.getItem('dnd-master-e2e-target-hp') ?? '9') <= 0
      const inCombat = turnVersion >= 4 && !combatResolved
      return {
        adventureId,
        status: inCombat ? 'COMBAT' : turnVersion >= 5 || combatResolved ? 'EXPLORATION' : 'authoritative-map',
        mapId: 'map-e2e',
        version: Number(sessionStorage.getItem('dnd-master-e2e-map-version') ?? '1'),
        sessionVersion: 4,
        grid: { width: 3, height: 3 },
        tokens: [
          { id: 'hero', type: 'PLAYER', x: position.x, y: position.y },
          ...(inCombat ? [{ id: 'enemy', type: 'ENEMY', x: 1, y: 0 }] : []),
        ],
        current: [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 0, y: 1 }],
        explored: [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 0, y: 1 }, { x: 2, y: 2 }],
      }
    }
    return {
      adventureId,
      status: 'authoritative-map',
    }
  },
  async submitMapAction(_adventureId, candidate) {
    const to = candidate.location ?? candidate.path?.at(-1)
    if (to) sessionStorage.setItem('dnd-master-e2e-map-position', JSON.stringify(to))
    if (candidate.action === 'TARGET' && Number(sessionStorage.getItem('dnd-master-e2e-target-hp') ?? '9') > 0) throw new Error('명중과 피해 판정이 끝나기 전에는 대상을 제거할 수 없습니다.')
    const version = Number(sessionStorage.getItem('dnd-master-e2e-map-version') ?? '1') + 1
    sessionStorage.setItem('dnd-master-e2e-map-version', String(version))
    return { turnId: 'map-turn-e2e', version }
  },
  async rollDice(_adventureId, _ruleSetId, _characterSheetId, _role, action) {
    const totals: Record<string, number> = { '1d20+3': 16, '1d20+2': 14, '2d6': 8 }
    return { rollId: `roll-${action}`, total: totals[action] ?? 17 }
  },
  async listSaved() { return [...saved] },
  async save() {
    const item = { adventureId, title: 'The Sealed Crypt', newVersion: 1 }
    saved = [{ id: adventureId, title: 'The Sealed Crypt', updatedAt: new Date().toISOString(), version: 1 }]
    return item
  },
  async resume() {},
  async deleteAdventure(id) { saved = saved.filter(item => item.id !== id) },
}

const guidanceApi: RuleGuidanceApi = {
  async ask() {
    return { inquiryId: 'inquiry-e2e', status: 'SUFFICIENT' }
  },
}

const fullJourneyMode = window.location.search.includes('full-journey')
const providerStorageKey = 'dnd-master-e2e-provider'
const defaultProviderView = { sessionId: 'session-e2e', provider: 'ollama', model: 'qwen3:8b', reasoning: 'medium', version: 0, turnInProgress: false }
const persistedProvider = sessionStorage.getItem(providerStorageKey)
let sessionView: AdventureSessionView = {
  sessionId: 'session-e2e', characterLimit: 1, version: fullJourneyMode ? 4 : 0, status: fullJourneyMode ? 'STARTED' : 'DRAFT', adventureId: fullJourneyMode ? adventureId : null,
  runtimeConfiguration: acceptanceJourneyMode ? { scenarioId: 'scenario-e2e', ruleSetId: 'rules-e2e', rulebookIds: ['rules.txt-RULEBOOK'], engineId: 'ollama', toolIds: ['search', 'move'], initialScene: 'brewery' } : null,
  party: [],
}
let providerView = persistedProvider ? { ...defaultProviderView, ...JSON.parse(persistedProvider) } : defaultProviderView
const sessionApi = {
  async read() { return sessionView },
  async readGmProvider() { return providerView },
  async switchGmProvider(_sessionId: string, version: number, selection: typeof providerView) {
    if (version !== providerView.version) throw new Error('provider binding version mismatch')
    if (providerView.turnInProgress) throw new Error('provider cannot switch during a turn')
    providerView = { ...providerView, ...selection, version: version + 1 }
    sessionStorage.setItem(providerStorageKey, JSON.stringify(providerView))
    return providerView
  },
  async listOwnedCharacters() {
    return [{ characterSheetId: 'sheet-e2e', characterName: 'Aria', level: 1, race: '엘프', characterClass: '로그', background: '범죄자' }]
  },
  async copyOwnedCharacter(_sessionId: string, characterSheetId: string) { return { characterSheetId } },
  async addMember(_sessionId: string, version: number, member: AdventureSessionView['party'][number]) {
    sessionView = { ...sessionView, version: version + 1, party: [member] }
    return sessionView
  },
  async removeMember() { return sessionView },
  async start(_sessionId: string, version: number, adventureId: string) {
    sessionView = { ...sessionView, version: version + 2, status: 'STARTED', adventureId }
    return sessionView
  },
  async complete(_sessionId: string, version: number) {
    sessionView = { ...sessionView, version: version + 1, status: 'COMPLETED' }
    return sessionView
  },
  async delete(_sessionId: string, version: number) {
    sessionView = { ...sessionView, version: version + 1, status: 'DELETED' }
    return sessionView
  },
  async readStoryPlan() {
    throw new Error('모험 계획이 아직 생성되지 않았습니다.')
  },
  async generateStoryPlan() {
    return { planId: 'plan-e2e', packageRevision: 1, partyRevision: 1, version: 1, status: 'READY' as const, currentStage: 6, stageCount: 6, failureReason: null }
  },
  async retryStoryPlan() {
    return this.generateStoryPlan()
  },
  async readRuntimeBinding() {
    return { adventureId, binding: { scenarioId: 'scenario-e2e', ruleSetId: 'rules-e2e', rulebookIds: ['rules.txt-RULEBOOK'], engineId: 'fixture', toolIds: ['move'], initialScene: 'brewery' }, readiness: { ready: true, state: 'READY' as const, checkedAt: new Date().toISOString(), retryable: false, reasons: [] } }
  },
}

const characterSessionApi = {
  async read() {
    return { sessionId: 'character-session-e2e', scenarioPackageId: 'package-e2e', blueprintRevision: e2eState.blueprintRevision, characterLimit: 1, version: 0, status: 'DRAFT' as const, adventureId: null, runtimeConfiguration: null, party: [] }
  },
  async addMember() { return characterSessionApi.read() },
  async start() { return characterSessionApi.read() },
}

const nativeFetch = window.fetch.bind(window)
window.fetch = async (input, init) => {
  const url = String(input)
  if (url.includes('/internal/v1/character-rules/catalogs/DND_5E_2014')) {
    return new Response(JSON.stringify({
      edition: 'DND_5E_2014',
      baseSchema: 'DND_5E_2014',
      revision: 1,
      races: raceOptions.map(option => option.id),
      classes: classOptions.map(option => option.id),
      backgrounds: backgroundOptions.map(option => option.id),
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  if (url.includes('/internal/v1/adventure-sessions/') && url.endsWith('/character-builds/evaluate')) {
    return new Response(JSON.stringify({ valid: true, derived: {}, violations: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  return nativeFetch(input, init)
}

function Journey() {
  const auth = useAuth()
  if (!auth.session) return <main><h1>D&amp;D Master</h1><LoginForm /></main>
  return (
    <div>
      <RulebookSetup api={setupApi} playerId="player-e2e" asMain={false} />
      <AdventureSessionPanel api={sessionApi} ownerPlayerId="player-e2e" sessionId="session-e2e" />
      <ScenarioUploadPanel />
      <CharacterCreationPage sessionId="character-session-e2e" ownerPlayerId="player-e2e" setupApi={setupApi} sessionApi={characterSessionApi} />
      <div aria-label="모험 플레이">
        <AdventureStream adventureId={adventureId} api={adventureApi} />
        <RuleEvidence adventureId={adventureId} api={guidanceApi} />
        <CharacterSheetView sheetId="sheet-e2e" api={playApi} />
        <RoleDiceRoller adventureId={adventureId} api={playApi} />
        <CombatMapView adventureId={adventureId} api={playApi} />
      <SavedAdventurePanel playApi={playApi} setupApi={setupApi} playerId="player-e2e" />
      <AcceptanceStoryPlan api={sessionApi} />
      </div>
    </div>
  )
}

function AcceptanceStoryPlan({ api }: { api: typeof sessionApi }) {
  const [hash, setHash] = useState(window.location.hash)
  const [started, setStarted] = useState(window.location.hash.includes('/adventures/'))
  useEffect(() => {
    const update = () => { setHash(window.location.hash); setStarted(window.location.hash.includes('/adventures/')) }
    window.addEventListener('hashchange', update)
    return () => window.removeEventListener('hashchange', update)
  }, [])
  if (!acceptanceJourneyMode || (!hash.includes('/story-plan') && !started)) return null
  if (started) return <p role="status">모험 시작 완료 · 파티 1/1</p>
  return <AdventureStoryPlanPage api={api} sessionId="session-e2e" />
}

function ScenarioUploadPanel() {
  const [file, setFile] = useState<File | null>(null)
  const [message, setMessage] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file) return
    const result = await setupApi.uploadScenario(file)
    setMessage(`등록 완료: ${result.name}`)
  }

  return (
    <section aria-labelledby="scenario-upload-heading">
      <h2 id="scenario-upload-heading">시나리오 업로드</h2>
      <form onSubmit={submit}>
        <label>
          시나리오 파일
          <input
            type="file"
            onChange={event => setFile(event.currentTarget.files?.item(0) ?? null)}
          />
        </label>
        <button type="submit" disabled={!file}>시나리오 등록</button>
      </form>
      {message ? <p>{message}</p> : null}
    </section>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode><AuthProvider api={identityApi}><Journey /></AuthProvider></StrictMode>,
)
