import { type FormEvent, StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { AdventureStream } from '../../src/features/adventure/AdventureStream'
import type { AdventureApi } from '../../src/features/adventure/AdventureApi'
import { AuthProvider, useAuth } from '../../src/features/auth/AuthContext'
import { LoginForm } from '../../src/features/auth/LoginForm'
import type { IdentityApi } from '../../src/features/auth/IdentityApi'
import { CharacterSheetView } from '../../src/features/character/CharacterSheetView'
import { CharacterCreationPage } from '../../src/features/character/CharacterCreationPage'
import { PackageBlueprintReviewPage } from '../../src/features/character/PackageBlueprintReviewPage'
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
import type { AdventureSessionView } from '../../src/features/adventure-session/AdventureSessionApi'
import '@fontsource-variable/noto-sans-kr/wght.css'
import '../../src/app.css'

const adventureId = 'adventure-e2e'
const decisionReviewMode = window.location.search.includes('package-review-decisions')

const e2eState = {
  bundle: null as unknown,
  blueprint: null as unknown,
  creationRequest: null as unknown,
  sessionCreationRequest: null as unknown,
  createdSessionId: null as string | null,
  blueprintStatus: (window.location.search.includes('package-review-published') ? 'PUBLISHED' : 'NEEDS_REVIEW') as 'NEEDS_REVIEW' | 'READY' | 'PUBLISHED',
  blueprintRevision: 2,
  proposalDecisions: {
    'proposal-e2e-1': window.location.search.includes('package-review-published') ? 'APPLIED' : 'UNDECIDED',
    'proposal-e2e-2': window.location.search.includes('package-review-published') ? 'EXCLUDED' : 'UNDECIDED',
  } as Record<string, 'UNDECIDED' | 'APPLIED' | 'EXCLUDED'>,
  blueprintValues: { 'node-str': '12' } as Record<string, string>,
  creation: null as unknown,
}

const identityApi: IdentityApi = {
  async login() {
    return { accessToken: 'owner-token', playerName: '테스터', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }
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
        baseSchema: {
          edition: 'DND 5판 2014',
          fields: [{
            key: 'starting_ability_scores', label: '능력치', options: [], required: true, sourceType: 'RULEBOOK', inputStatus: 'EXTRACTED', inputMode: 'FREE_TEXT' as const,
            suggestions: [], diagnostics: [], optionDetails: [], sourceQuote: '능력치를 결정합니다.', evidence: [],
          }, {
            key: 'starting_ability_scores.str', label: '힘', options: [], required: true, sourceType: 'RULEBOOK', inputStatus: 'EXTRACTED', inputMode: 'FREE_TEXT' as const,
            suggestions: [], diagnostics: [], optionDetails: [], sourceQuote: '힘 능력치를 입력합니다.', evidence: [],
          }],
        },
        storybookProposals: [{
          proposalId: 'proposal-e2e-1', key: 'alignment', label: '스토리 속 성향', description: '질서 선 성향으로 묘사됩니다.',
          sourceDocument: { knowledgeDocumentId: 'storybook.txt-STORYBOOK', originalFilename: 'storybook.txt', extractionVersion: 1 },
          sourceQuote: '질서 선의 수호자였다.', evidence: [{ locator: 'page:2', excerpt: '질서 선의 수호자였다.' }],
          decisionState: e2eState.proposalDecisions['proposal-e2e-1'], readinessState: 'READY' as const,
        }, ...(decisionReviewMode ? [{
          proposalId: 'proposal-e2e-2', key: 'faction', label: '스토리 속 소속', description: '수호자 길드와 연결됩니다.',
          sourceDocument: { knowledgeDocumentId: 'storybook.txt-STORYBOOK', originalFilename: 'storybook.txt', extractionVersion: 1 },
          sourceQuote: '수호자 길드의 일원이었다.', evidence: [{ locator: 'page:3', excerpt: '수호자 길드의 일원이었다.' }],
          decisionState: e2eState.proposalDecisions['proposal-e2e-2'], readinessState: 'READY' as const,
        }] : [])],
        storybookExtractionState: 'PROPOSALS_AVAILABLE' as const,
        appliedSettingsSummary: {
          baseSchemaIncluded: true,
          appliedProposalIds: Object.entries(e2eState.proposalDecisions).filter(([, state]) => state === 'APPLIED').map(([id]) => id),
          excludedProposalIds: Object.entries(e2eState.proposalDecisions).filter(([, state]) => state === 'EXCLUDED').map(([id]) => id),
          unresolvedProposalCount: Object.values(e2eState.proposalDecisions).filter(state => state === 'UNDECIDED').length,
        },
        baseSchemaValid: true,
      },
    }
    e2eState.blueprint = preparation.characterCreationBlueprint
    return preparation
  },
  async useStorybookProposal(_packageId, proposalId) {
    e2eState.proposalDecisions[proposalId] = 'APPLIED'
    e2eState.blueprintStatus = Object.values(e2eState.proposalDecisions).every(state => state !== 'UNDECIDED') ? 'READY' : 'NEEDS_REVIEW'
    e2eState.blueprintRevision += 1
    return (await setupApi.getPlayPreparation!('package-e2e')).characterCreationBlueprint
  },
  async excludeStorybookProposal(_packageId, proposalId) {
    e2eState.proposalDecisions[proposalId] = 'EXCLUDED'
    e2eState.blueprintStatus = Object.values(e2eState.proposalDecisions).every(state => state !== 'UNDECIDED') ? 'READY' : 'NEEDS_REVIEW'
    e2eState.blueprintRevision += 1
    return (await setupApi.getPlayPreparation!('package-e2e')).characterCreationBlueprint
  },
  async publishBlueprint() {
    if (e2eState.blueprintStatus !== 'READY') throw new Error('Blueprint must be ready before publishing')
    e2eState.blueprintStatus = 'PUBLISHED'
    e2eState.blueprintRevision += 1
    return {
      publishedRevision: e2eState.blueprintRevision,
      appliedSettingsSummary: {
        baseSchemaIncluded: true,
        appliedProposalIds: Object.entries(e2eState.proposalDecisions).filter(([, state]) => state === 'APPLIED').map(([id]) => id),
        excludedProposalIds: Object.entries(e2eState.proposalDecisions).filter(([, state]) => state === 'EXCLUDED').map(([id]) => id),
        unresolvedProposalCount: 0,
      },
    }
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

const adventureApi: AdventureApi = {
  async readConversation() {
    const entries = JSON.parse(sessionStorage.getItem('dnd-master-e2e-conversation') ?? '[]') as Array<{ sequence: number; speaker: string; content: string }>
    return { adventureId, version: Number(sessionStorage.getItem('dnd-master-e2e-turn-version') ?? '0'), entries }
  },
  async sendMessage(_adventureId, message) {
    const version = Number(sessionStorage.getItem('dnd-master-e2e-turn-version') ?? '0') + 1
    const entries = JSON.parse(sessionStorage.getItem('dnd-master-e2e-conversation') ?? '[]') as Array<{ sequence: number; speaker: string; content: string }>
    const narration = window.location.search.includes('full-journey') ? `턴 ${version}: 근거를 바탕으로 응답한다.` : '근거를 바탕으로 응답한다.'
    entries.push({ sequence: entries.length + 1, speaker: 'PLAYER', content: message }, { sequence: entries.length + 2, speaker: 'AI_GAME_MASTER', content: narration })
    sessionStorage.setItem('dnd-master-e2e-turn-version', String(version))
    sessionStorage.setItem('dnd-master-e2e-conversation', JSON.stringify(entries))
    return {
      narration,
      judgment: '판정 완료',
      currentScene: '새 장면',
      sourceRefs: [],
      warnings: [],
      version,
    }
  },
}

let saved: SavedAdventure[] = []
const playApi: AdventurePlayApi = {
  async getCharacter() {
    return { characterSheetId: 'sheet-e2e', name: 'Aria', edition: '2024', armorClass: 16, strength: 14, dexterity: 18, constitution: 12, intelligence: 10, wisdom: 13, charisma: 15 }
  },
  async getCombatMap() {
    if (window.location.search.includes('full-journey')) {
      const position = JSON.parse(sessionStorage.getItem('dnd-master-e2e-map-position') ?? '{"x":0,"y":0}') as { x: number; y: number }
      return { adventureId, status: 'authoritative-map', mapId: 'map-e2e', version: Number(sessionStorage.getItem('dnd-master-e2e-map-version') ?? '1'), sessionVersion: 4, grid: { width: 3, height: 3 }, tokens: [{ id: 'hero', type: 'PLAYER', x: position.x, y: position.y }, { id: 'hidden', type: 'ENEMY', x: 2, y: 2, lastSeen: true }], layers: [{ type: 'MAP_IMAGE', value: '/assets/maps/a-potent-brew-map.png' }, { type: 'GRID_BOUNDS', value: '311,105,800,800,1403,992' }], current: [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 0, y: 1 }], explored: [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 0, y: 1 }, { x: 2, y: 2 }] }
    }
    return {
      adventureId,
      status: 'authoritative-map',
    }
  },
  async submitMapAction(_adventureId, candidate) {
    const to = candidate.location ?? candidate.path?.at(-1)
    if (to) sessionStorage.setItem('dnd-master-e2e-map-position', JSON.stringify(to))
    const version = Number(sessionStorage.getItem('dnd-master-e2e-map-version') ?? '1') + 1
    sessionStorage.setItem('dnd-master-e2e-map-version', String(version))
    return { turnId: 'map-turn-e2e', version }
  },
  async rollDice() { return { rollId: 'roll-e2e', total: 17 } },
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
  runtimeConfiguration: null, party: [],
}
let providerView = persistedProvider ? { ...defaultProviderView, ...JSON.parse(persistedProvider) } : defaultProviderView
const sessionApi = {
  async create(request: { scenarioPackageId: string; blueprintId: string; blueprintRevision: number }) {
    if (e2eState.blueprintStatus !== 'PUBLISHED' || request.blueprintRevision !== e2eState.blueprintRevision) {
      throw new Error('character creation requires the published blueprint revision')
    }
    e2eState.sessionCreationRequest = request
    e2eState.createdSessionId = 'session-created-e2e'
    sessionView = {
      ...sessionView,
      sessionId: 'session-created-e2e',
      scenarioPackageId: request.scenarioPackageId,
      blueprintRevision: request.blueprintRevision,
      status: 'DRAFT',
    }
    return sessionView
  },
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
  if (url.includes('/api/v1/rulebook-catalog')) {
    return new Response(JSON.stringify([
      { catalogRevisionId: 'catalog-2014', edition: 'DND_5E_2014', displayName: 'D&D 5e (2014)', rulebookId: 'catalog-rulebook-2014', revisionNumber: 1, status: 'READY', extractionVersion: 1 },
      { catalogRevisionId: 'catalog-2024', edition: 'DND_5E_2024', displayName: 'D&D 5e (2024)', rulebookId: 'catalog-rulebook-2024', revisionNumber: 1, status: 'READY', extractionVersion: 1 },
    ]), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  if (url.includes('/internal/v1/adventure-sessions/') && url.endsWith('/character-builds/evaluate')) {
    return new Response(JSON.stringify({ valid: true, derived: {}, violations: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  return nativeFetch(input, init)
}

function Journey() {
  const auth = useAuth()
  if (!auth.session) return <main><h1>D&amp;D Master</h1><LoginForm /></main>
  if (window.location.search.includes('package-review')) {
    return <PackageBlueprintReviewPage
      packageId="package-e2e"
      setupApi={setupApi}
      sessionApi={sessionApi}
      onSessionCreated={sessionId => { e2eState.createdSessionId = sessionId; window.location.hash = `#/sessions/${sessionId}/character-blueprint` }}
    />
  }
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
        <div className="app-content"><CombatMapView adventureId={adventureId} api={playApi} /></div>
        <SavedAdventurePanel playApi={playApi} setupApi={setupApi} playerId="player-e2e" />
      </div>
    </div>
  )
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
