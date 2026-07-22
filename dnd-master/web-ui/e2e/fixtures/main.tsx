import { StrictMode } from 'react'
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

const setupApi: SetupApi = {
  async uploadRulebooks(documents, _ownerId) {
    return documents.map(document => ({
      knowledgeDocumentId: `${document.file.name}-${document.documentType}`,
      documentType: document.documentType,
      originalFilename: document.file.name,
      status: 'ACCEPTED' as const,
    }))
  },
  async getRulebookStatus(rulebookId) { return { rulebookId, status: 'INDEXED' } },
  async uploadScenario(file) { return { id: 'scenario-e2e', name: file.name } },
  async saveRuleSet() {},
}

const adventureApi: AdventureApi = {
  async sendMessage() {},
}

let saved: SavedAdventure[] = []
const playApi: AdventurePlayApi = {
  async getCharacter() {
    return { characterSheetId: 'sheet-e2e', name: 'Aria', edition: '2024', armorClass: 16, strength: 14, dexterity: 18, constitution: 12, intelligence: 10, wisdom: 13, charisma: 15 }
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
  return <>
    <RulebookSetup api={setupApi} playerId="player-e2e" />
    <div aria-label="모험 플레이">
      <AdventureStream adventureId={adventureId} api={adventureApi} />
      <RuleEvidence adventureId={adventureId} api={guidanceApi} />
      <CharacterSheetView sheetId="sheet-e2e" api={playApi} />
      <RoleDiceRoller adventureId={adventureId} api={playApi} />
      <CombatMapView adventureId={adventureId} />
      <SavedAdventurePanel api={playApi} playerId="player-e2e" />
    </div>
  </>
}

createRoot(document.getElementById('root')!).render(
  <StrictMode><AuthProvider api={identityApi}><Journey /></AuthProvider></StrictMode>,
)
