import { type FormEvent, StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { AdventureStream } from '../../src/features/adventure/AdventureStream'
import type { AdventureApi } from '../../src/features/adventure/AdventureApi'
import { AuthProvider, useAuth } from '../../src/features/auth/AuthContext'
import { LoginForm } from '../../src/features/auth/LoginForm'
import type { IdentityApi } from '../../src/features/auth/IdentityApi'
import { CharacterSheetView } from '../../src/features/character/CharacterSheetView'
import { CombatMapView } from '../../src/features/combat-map/CombatMapView'
import { RoleDiceRoller } from '../../src/features/dice/RoleDiceRoller'
import { RuleEvidence } from '../../src/features/rule-guidance/RuleEvidence'
import type { RuleGuidanceApi } from '../../src/features/rule-guidance/RuleGuidanceApi'
import { RulebookSetup } from '../../src/features/rulebooks/RulebookSetup'
import type { SetupApi } from '../../src/features/rulebooks/SetupApi'
import type { AdventurePlayApi, SavedAdventure } from '../../src/features/saved-adventures/AdventurePlayApi'
import { SavedAdventurePanel } from '../../src/features/saved-adventures/SavedAdventurePanel'

const adventureId = 'adventure-e2e'

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
  async createCharacterSheet() {
    return {
      characterSheetId: 'sheet-e2e',
      adventureId: adventureId,
      edition: 'DND_5E_2024',
      characterName: 'Aria',
      level: 1,
      inspiration: false,
      version: 0,
    }
  },
  async saveRuleSet() {},
  async listKnowledgeDocuments(ownerId) {
    return privateDocuments
      .filter(document => document.ownerId === ownerId)
      .map(document => ({
        knowledgeDocumentId: document.knowledgeDocumentId,
        documentType: document.documentType,
        originalFilename: document.originalFilename,
        status: 'QUEUED' as const,
      }))
  },
}

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

function Journey() {
  const auth = useAuth()
  if (!auth.session) return <main><h1>D&amp;D Master</h1><LoginForm /></main>
  return (
    <main>
      <RulebookSetup api={setupApi} playerId="player-e2e" asMain={false} />
      <ScenarioUploadPanel />
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
