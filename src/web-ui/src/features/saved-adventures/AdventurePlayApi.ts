export type CharacterSheet = {
  characterSheetId: string
  name: string
  edition: string
  armorClass: number
  strength: number
  dexterity: number
  constitution: number
  intelligence: number
  wisdom: number
  charisma: number
}

type CharacterSheetResponse = {
  characterSheetId: string
  characterName: string
  edition: string
  derivedStatistics: string
}

export type SavedAdventure = { id: string; title: string; updatedAt: string }

export type SavedAdventureResponse = { adventureId: string; status: string }

export function toSavedAdventure(response: SavedAdventureResponse): SavedAdventure {
  return { id: response.adventureId, title: response.status, updatedAt: '' }
}

export type SessionKnowledgeSet = {
  adventureId: string
  sessionId: string
  knowledgeDocumentIds: string[]
}

export type CombatMapView = {
  adventureId: string
  status: string
  mapId?: string
  tokens?: Array<{ id: string; type: string; x: number; y: number; lastSeen?: boolean }>
  layers?: Array<{ type: string; value: string }>
  doors?: Array<{ x: number; y: number; open: boolean }>
  current?: Array<{ x: number; y: number }>
  explored?: Array<{ x: number; y: number }>
  version?: number
  sessionVersion?: number
  grid?: { width: number; height: number }
  obstacles?: Array<{ x: number; y: number }>
  objects?: Array<{ id: string; type: string; x: number; y: number }>
}

export type MapActionCandidate = {
  mapId: string
  mapVersion: number
  tokenId: string
  action: 'MOVE' | 'INTERACT' | 'TARGET' | 'LOCATION'
  path?: Array<{ x: number; y: number }>
  targetId?: string
  location?: { x: number; y: number }
}

export interface AdventurePlayApi {
  getCharacter(sheetId: string): Promise<CharacterSheet>
  rollDice(adventureId: string, ruleSetId: string, characterSheetId: string, role: string, action: string): Promise<{ rollId: string; total: number }>
  listSaved(ownerId: string): Promise<SavedAdventure[]>
  save(adventureId: string, playerId: string, expectedVersion: number, currentScene: string): Promise<{ adventureId: string; newVersion: number }>
  resume(adventureId: string): Promise<void>
  deleteAdventure(adventureId: string, playerId: string, expectedVersion: number): Promise<void>
  getSessionKnowledgeSet(adventureId: string): Promise<SessionKnowledgeSet>
  saveSessionKnowledgeSet(adventureId: string, playerId: string, knowledgeDocumentIds: string[]): Promise<SessionKnowledgeSet>
  getCombatMap(adventureId: string): Promise<CombatMapView>
  submitMapAction?(adventureId: string, candidate: MapActionCandidate, command?: { turnId: string; commandId: string }, expectedVersion?: number): Promise<{ turnId: string; version: number }>
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 409 || response.status === 422) throw new Error('적용 규칙상 해당 요청을 처리할 수 없습니다.')
  if (!response.ok) throw new Error('요청을 처리하지 못했습니다.')
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T
  return response.json() as Promise<T>
}

export class HttpAdventurePlayApi implements AdventurePlayApi {
  private readonly getToken: () => string

  constructor(getToken: () => string) {
    this.getToken = getToken
  }

  private authHeaders(): Record<string, string> {
    return { Authorization: `Bearer ${this.getToken()}` }
  }

  getCharacter(sheetId: string) {
    return request<CharacterSheetResponse>(`/internal/v1/character-sheets/${sheetId}?edition=DND_5E_2014`, {
      headers: this.authHeaders(),
    }).then(sheet => {
      let derived: { armorClass?: number; abilityScores?: Record<string, number> } = {}
      try {
        derived = JSON.parse(sheet.derivedStatistics) as typeof derived
      } catch {
        // Keep the detail view usable even when an older sheet has no derived JSON.
      }
      const scores = derived.abilityScores ?? {}
      return {
        characterSheetId: sheet.characterSheetId,
        name: sheet.characterName,
        edition: sheet.edition,
        armorClass: derived.armorClass ?? 0,
        strength: scores.strength ?? 0,
        dexterity: scores.dexterity ?? 0,
        constitution: scores.constitution ?? 0,
        intelligence: scores.intelligence ?? 0,
        wisdom: scores.wisdom ?? 0,
        charisma: scores.charisma ?? 0,
      }
    })
  }

  rollDice(adventureId: string, ruleSetId: string, characterSheetId: string, role: string, action: string) {
    return request<{ rollId: string; total: number }>(`/api/v1/adventures/${adventureId}/dice-rolls`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...this.authHeaders() },
      body: JSON.stringify({ ruleSetId, characterSheetId, role, action }),
    })
  }

  listSaved(ownerId: string) {
    return request<SavedAdventureResponse[]>(`/internal/v1/adventures?ownerId=${ownerId}`, {
      headers: this.authHeaders(),
    }).then(items => items.map(toSavedAdventure))
  }

  save(adventureId: string, playerId: string, expectedVersion: number, currentScene: string) {
    return request<{ adventureId: string; newVersion: number }>(`/api/v1/adventures/${adventureId}/save`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...this.authHeaders() },
      body: JSON.stringify({ playerId, expectedVersion, currentScene }),
    })
  }

  resume(adventureId: string) {
    return request<void>(`/api/v1/adventures/${adventureId}/resume`, {
      method: 'POST',
      headers: this.authHeaders(),
    })
  }

  deleteAdventure(adventureId: string, playerId: string, expectedVersion: number) {
    return request<void>(`/api/v1/adventures/${adventureId}`, {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json', ...this.authHeaders() },
      body: JSON.stringify({ playerId, expectedVersion }),
    })
  }

  getSessionKnowledgeSet(adventureId: string) {
    return request<SessionKnowledgeSet>(`/api/v1/adventures/${adventureId}/knowledge-documents`, {
      headers: this.authHeaders(),
    })
  }

  saveSessionKnowledgeSet(adventureId: string, playerId: string, knowledgeDocumentIds: string[]) {
    return request<SessionKnowledgeSet>(`/api/v1/adventures/${adventureId}/knowledge-documents`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...this.authHeaders() },
      body: JSON.stringify({ playerId, knowledgeDocumentIds }),
    })
  }

  getCombatMap(adventureId: string) {
    return request<CombatMapView>(`/api/v1/adventures/${adventureId}/combat-map`, {
      headers: this.authHeaders(),
    })
  }

  submitMapAction(adventureId: string, candidate: MapActionCandidate, command = createMapCommandIdentity(), expectedVersion = candidate.mapVersion) {
    return request<{ turnId: string; version: number }>(`/api/v1/adventures/${adventureId}/turns`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json', ...this.authHeaders(),
        'Idempotency-Key': command.commandId, 'If-Match-Version': String(expectedVersion),
      },
      body: JSON.stringify({ turnId: command.turnId, input: {
        type: 'MAP_ACTION', mapId: candidate.mapId, mapVersion: candidate.mapVersion,
        action: JSON.stringify(candidate),
      } }),
    }).then(result => ({ turnId: result.turnId, version: result.version }))
  }
}

function createMapCommandIdentity() {
  const value = globalThis.crypto && 'randomUUID' in globalThis.crypto ? globalThis.crypto.randomUUID() : `${Date.now()}-${Math.random()}`
  return { turnId: value, commandId: value }
}
