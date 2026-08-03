import { type FormEvent, StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { AdventureStream } from '../../src/features/adventure/AdventureStream'
import type { AdventureApi } from '../../src/features/adventure/AdventureApi'
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
import type { AdventureSessionView } from '../../src/features/adventure-session/AdventureSessionApi'

const adventureId = 'adventure-e2e'

const e2eState = {
  bundle: null as unknown,
  blueprint: null as unknown,
  creationRequest: null as unknown,
  blueprintStatus: 'NEEDS_REVIEW' as 'NEEDS_REVIEW' | 'READY' | 'PUBLISHED',
  blueprintRevision: 2,
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

const adventureApi: AdventureApi = {
  async sendMessage() {
    return {
      narration: '근거를 바탕으로 응답한다.',
      judgment: '판정 완료',
      currentScene: '새 장면',
      sourceRefs: [],
      warnings: [],
      version: 1,
    }
  },
}

let saved: SavedAdventure[] = []
const playApi: AdventurePlayApi = {
  async getCharacter() {
    return { characterSheetId: 'sheet-e2e', name: 'Aria', edition: '2024', armorClass: 16, strength: 14, dexterity: 18, constitution: 12, intelligence: 10, wisdom: 13, charisma: 15 }
  },
  async getCombatMap() {
    return {
      adventureId,
      status: 'authoritative-map',
    }
  },
  async rollDice() { return { rollId: 'roll-e2e', total: 17 } },
  async listSaved() { return [...saved] },
  async save() {
    const item = { adventureId, title: 'The Sealed Crypt', newVersion: 1 }
    saved = [{ id: adventureId, title: 'The Sealed Crypt', updatedAt: new Date().toISOString() }]
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

let sessionView: AdventureSessionView = {
  sessionId: 'session-e2e', characterLimit: 1, version: 0, status: 'DRAFT', adventureId: null,
  runtimeConfiguration: null, party: [],
}
const sessionApi = {
  async read() { return sessionView },
  async addMember(_sessionId: string, version: number, member: AdventureSessionView['party'][number]) {
    sessionView = { ...sessionView, version: version + 1, party: [member] }
    return sessionView
  },
  async removeMember() { return sessionView },
  async start(_sessionId: string, version: number, adventureId: string) {
    sessionView = { ...sessionView, version: version + 2, status: 'STARTED', adventureId }
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
  if (url.includes('/internal/v1/adventure-sessions/') && url.endsWith('/character-builds/evaluate')) {
    return new Response(JSON.stringify({ valid: true, derived: {}, violations: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  return nativeFetch(input, init)
}

function Journey() {
  const auth = useAuth()
  if (!auth.session) return <main><h1>D&amp;D Master</h1><LoginForm /></main>
  return (
    <main>
      <RulebookSetup api={setupApi} playerId="player-e2e" asMain={false} />
      <AdventureSessionPanel api={sessionApi} sessionId="session-e2e" />
      <ScenarioUploadPanel />
      <CharacterCreationPage sessionId="character-session-e2e" setupApi={setupApi} sessionApi={characterSessionApi} />
      <div aria-label="모험 플레이">
        <AdventureStream adventureId={adventureId} api={adventureApi} />
        <RuleEvidence adventureId={adventureId} api={guidanceApi} />
        <CharacterSheetView sheetId="sheet-e2e" api={playApi} />
        <RoleDiceRoller adventureId={adventureId} api={playApi} />
        <CombatMapView adventureId={adventureId} api={playApi} />
        <SavedAdventurePanel playApi={playApi} setupApi={setupApi} playerId="player-e2e" />
      </div>
    </main>
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
