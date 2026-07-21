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
import type { AdventurePlayApi, CombatMapView as MapView, SavedAdventure } from '../../src/features/saved-adventures/AdventurePlayApi'
import { SavedAdventurePanel } from '../../src/features/saved-adventures/SavedAdventurePanel'

const adventureId = 'adventure-e2e'

const identityApi: IdentityApi = {
  async login() {
    return { accessToken: 'owner-token', playerName: '테스터', expiresAt: new Date(Date.now() + 3_600_000).toISOString() }
  },
  async logout() {},
}

const setupApi: SetupApi = {
  async uploadRulebook(file) { return { rulebookId: file.name, status: 'INDEXED' } },
  async getRulebookStatus(rulebookId) { return { rulebookId, status: 'INDEXED' } },
  async uploadScenario(file) { return { id: 'scenario-e2e', name: file.name } },
}

const adventureApi: AdventureApi = {
  async *streamMessage() { yield 'The crypt '; yield 'opens after the Dexterity check.' },
}

let currentMap: MapView = {
  token: { x: 1, y: 1 },
  layers: [
    { id: 'floor', label: 'Stone floor', visibility: 'PLAYER_VISIBLE' },
    { id: 'secret', label: 'Hidden guardian route', visibility: 'AI_ONLY' },
  ],
}
let saved: SavedAdventure[] = []
const playApi: AdventurePlayApi = {
  async getCharacter() { return { edition: '2024', name: 'Aria', armorClass: 16, heroicInspiration: true } },
  async roll() { return { total: 17 } },
  async getMap() { return currentMap },
  async move() { currentMap = { ...currentMap, token: { x: 2, y: 1 } }; return currentMap },
  async listSaved() { return [...saved] },
  async save() {
    const item = { id: adventureId, title: 'The Sealed Crypt', updatedAt: new Date().toISOString() }
    saved = [item]
    return item
  },
  async resume() {},
  async delete(id) { saved = saved.filter(item => item.id !== id) },
}

const guidanceApi: RuleGuidanceApi = {
  async ask() {
    return {
      inquiryId: 'inquiry-e2e', status: 'SUFFICIENT', answer: 'Roll a Dexterity check.',
      sources: [{ rulebook: 'rules.txt', locator: 'page 12' }], candidates: [],
    }
  },
  async selectFinalRule() {},
}

function Journey() {
  const auth = useAuth()
  if (!auth.session) return <main><h1>D&amp;D Master</h1><LoginForm /></main>
  return <>
    <RulebookSetup api={setupApi} playerId="player-e2e" />
    <div aria-label="모험 플레이">
      <AdventureStream adventureId={adventureId} api={adventureApi} />
      <RuleEvidence adventureId={adventureId} api={guidanceApi} />
      <CharacterSheetView adventureId={adventureId} api={playApi} />
      <RoleDiceRoller adventureId={adventureId} api={playApi} />
      <CombatMapView adventureId={adventureId} api={playApi} />
      <SavedAdventurePanel currentAdventureId={adventureId} api={playApi} />
    </div>
  </>
}

createRoot(document.getElementById('root')!).render(
  <StrictMode><AuthProvider api={identityApi}><Journey /></AuthProvider></StrictMode>,
)
