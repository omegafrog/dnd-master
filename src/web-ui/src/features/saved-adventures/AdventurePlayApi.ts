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

export type SavedAdventure = {
  id: string
  title: string
  statusLabel: '진행 중인 모험' | '완료한 모험'
  resumable: boolean
  updatedAt: string
  version: number
  sessionId?: string
  scenarioBundleId?: string
}

export type SavedAdventureResponse = { adventureId: string; name?: string | null; title?: string | null; status: string; version: number; updatedAt?: string | null; sessionId?: string | null; scenarioBundleId?: string | null }

export function toSavedAdventure(response: SavedAdventureResponse): SavedAdventure {
  const resumable = response.status !== 'COMPLETED' && response.status !== 'DELETED'
  return {
    id: response.adventureId,
    title: response.name ?? response.title ?? '이름 없는 모험',
    statusLabel: resumable ? '진행 중인 모험' : '완료한 모험',
    resumable,
    updatedAt: response.updatedAt ?? '',
    version: response.version,
    ...(response.sessionId ? { sessionId: response.sessionId } : {}),
    ...(response.scenarioBundleId ? { scenarioBundleId: response.scenarioBundleId } : {}),
  }
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
  tokens?: Array<{ id: string; type: string; x: number; y: number; lastSeen?: boolean; selected?: boolean; currentTurn?: boolean }>
  currentTurnTokenId?: string
  layers?: Array<{ type: string; value: string; visibility?: string }>
  doors?: Array<{ x: number; y: number; open: boolean }>
  current?: Array<{ x: number; y: number }>
  explored?: Array<{ x: number; y: number }>
  version?: number
  sessionVersion?: number
  grid?: { width: number; height: number }
  obstacles?: Array<{ x: number; y: number }>
  objects?: Array<{ id: string; type: string; x: number; y: number }>
  spatialFeatures?: Array<{ id: string; type: string; cells: Array<{ x: number; y: number }>; visibility: string; state: string; interactable: boolean }>
  playerStartCandidates?: Array<{ x: number; y: number; confidence: number; evidence: string[]; source?: string }>
}

export type MapGridAlignment = { mapId: string; version: number; imageRevision: string; imageViewId?: string; originX: number; originY: number; cellSize: number }
export type MapGridAlignmentRequest = { mapId: string; commandId: string; expectedVersion: number; imageRevision: string; originX: number; originY: number; cellSize: number }
export type MapBoundary = { x: number; y: number; orientation: 'HORIZONTAL' | 'VERTICAL'; kind: 'WALL' | 'DOOR'; open: boolean }
export type MapBoundaryCandidate = { x: number; y: number; orientation: 'HORIZONTAL' | 'VERTICAL'; kind: 'WALL' | 'DOOR'; confidence: number; evidence: string[]; source?: string }
export type MapBoundaryProposal = { mapVersion: number; obstacles: Array<{ x: number; y: number }>; doors: Array<{ x: number; y: number; open: boolean }>; boundaries: MapBoundary[]; crop?: string; candidates?: MapBoundaryCandidate[]; alignmentVersion?: number; imageRevision?: string }
export type CombatMapLayoutDraft = { commandId: string; expectedVersion: number; obstacles: Array<{ x: number; y: number }>; doors: Array<{ x: number; y: number }>; boundaries?: MapBoundary[]; crop?: string; alignmentVersion?: number; imageRevision?: string; playerStart?: { x: number; y: number } }

export type MapActionCandidate = {
  mapId: string
  mapVersion: number
  tokenId: string
  action: 'MOVE' | 'INTERACT' | 'TARGET' | 'LOCATION'
  path?: Array<{ x: number; y: number }>
  waypoints?: Array<{ x: number; y: number }>
  fingerprint?: string
  commandId?: string
  targetId?: string
  location?: { x: number; y: number }
}

export type MapMovementPreviewRequest = {
  mapId: string
  mapVersion: number
  tokenId: string
  destination: { x: number; y: number }
  waypoints?: Array<{ x: number; y: number }>
  commandId?: string
  pendingTurnId?: string
  sourceText?: string
}

export type MapMovementPreview = {
  mapId: string
  orderedPositions: Array<{ x: number; y: number }>
  distance: number
  baseMapVersion: number
  fingerprint: string
}

export type PendingMapMovement = {
  mapId: string
  tokenId: string
  mapVersion: number
  path: Array<{ x: number; y: number }>
  distance: number
  fingerprint: string
  waypoints: Array<{ x: number; y: number }>
  sourceText?: string
  destination?: { x: number; y: number }
  pendingTurnId?: string
  confirmationCommandId?: string
  terminal?: boolean
}
export type NaturalLanguageMovementPreviewRequest = { mapId: string; mapVersion: number; tokenId: string; sourceText: string; tacticalContext?: string }
export type NaturalLanguageMovementPreview = { status: 'RESOLVED' | 'AMBIGUOUS' | 'UNRESOLVED'; destination?: { x: number; y: number }; candidates: Array<{ destination: { x: number; y: number }; confidence: number; reason: string }>; playerMessage: string; pendingTurnId?: string; confirmationCommandId?: string; path: Array<{ x: number; y: number }>; distance?: number; baseMapVersion?: number; fingerprint?: string }
export type NaturalLanguageMovementConfirmation = { pendingTurnId: string; commandId: string; tokenId: string; mapVersion: number }
export type NaturalLanguageMovementConfirmationResult = MapMovementResult

export type MapMovementResult = {
  version: number
  operationId?: string
  status: 'RETRY_REQUIRED' | 'CHECK_REQUIRED' | 'COMMITTED' | 'INTERRUPTED' | 'CANCELLED'
  requestedPath: Array<{ x: number; y: number }>
  traversedPath: Array<{ x: number; y: number }>
  finalPosition?: { x: number; y: number }
  publicEvents: string[]
  interruptionReason?: string
  followUp?: { commandId: string; operationId: string; kind: 'COMBAT' | 'WARNING' | 'CONTINUATION'; trigger: 'HOSTILE_OBSERVED' }
  pendingCheck?: { checkId: string; operationId: string; label: string; diceExpression: string; ownerPlayerId: string; actor: 'PLAYER' }
}

export type SpatialActionRequest = { mapId: string; tokenId: string; x: number; y: number; expectedVersion: number; commandId: string }
export type SpatialTurnRequest = { mapId: string; expectedVersion: number; commandId: string }
export type SpatialActionResult = { mapId: string; mapVersion: number; publicEvents: string[]; operationId?: string; status?: string; pendingCheck?: { checkId: string; operationId: string; label: string; diceExpression: string; ownerPlayerId: string; actor: 'PLAYER' } }
export type SpatialRollContext = { mapId: string; operationId: string; checkId: string; ownerPlayerId: string; actor: 'PLAYER' }

export type CombatResolutionStatus = 'RESOLVED' | 'PENDING_RULE_INPUT'
export type DiceRollResponse = {
  rollId: string
  total: number
  judgment?: string
  resolutionStatus?: CombatResolutionStatus | string
  outcomeApplied?: boolean
}

export interface AdventurePlayApi {
  getCharacter(sheetId: string): Promise<CharacterSheet>
  rollDice(adventureId: string, ruleSetId: string, characterSheetId: string, role: string, action: string): Promise<DiceRollResponse>
  listSaved(ownerId: string): Promise<SavedAdventure[]>
  save(adventureId: string, playerId: string, expectedVersion: number, currentScene: string): Promise<{ adventureId: string; newVersion: number }>
  resume(adventureId: string): Promise<void>
  deleteAdventure(adventureId: string, playerId: string, expectedVersion: number): Promise<void>
  getSessionKnowledgeSet(adventureId: string): Promise<SessionKnowledgeSet>
  saveSessionKnowledgeSet(adventureId: string, playerId: string, knowledgeDocumentIds: string[]): Promise<SessionKnowledgeSet>
  getCombatMap(adventureId: string): Promise<CombatMapView>
  getCombatMapPreparation?(adventureId: string): Promise<CombatMapView>
  getPublicMapImage?(adventureId: string): Promise<string | null>
  getCombatMapPreparationImage?(adventureId: string): Promise<string | null>
  detectMapBoundaries?(adventureId: string): Promise<MapBoundaryProposal>
  getMapGridAlignment?(adventureId: string): Promise<MapGridAlignment>
  applyMapGridAlignment?(adventureId: string, alignment: MapGridAlignmentRequest): Promise<MapGridAlignment>
  updateCombatMapLayout?(adventureId: string, draft: CombatMapLayoutDraft): Promise<void>
  previewMapMovement?(adventureId: string, request: MapMovementPreviewRequest): Promise<MapMovementPreview>
  getPendingMapMovement?(adventureId: string): Promise<PendingMapMovement | null>
  clearPendingMapMovement?(adventureId: string): Promise<void>
  previewNaturalLanguageMovement?(adventureId: string, request: NaturalLanguageMovementPreviewRequest): Promise<NaturalLanguageMovementPreview>
  confirmNaturalLanguageMovement?(adventureId: string, request: NaturalLanguageMovementConfirmation): Promise<NaturalLanguageMovementConfirmationResult>
  movementOperation?(adventureId: string, mapId: string, operationId: string): Promise<MapMovementResult>
  latestMovementOperation?(adventureId: string, mapId: string): Promise<MapMovementResult | null>
  cancelMovementOperation?(adventureId: string, mapId: string, operationId: string, cancelCommandId: string): Promise<MapMovementResult>
  resumeMovementOperation?(adventureId: string, mapId: string, operationId: string, check?: { commandId: string; operationId: string; checkId: string; success: boolean; ownerPlayerId: string; actor: 'PLAYER' }): Promise<MapMovementResult>
  rollSpatialCheck?(adventureId: string, expectedVersion: number, spatial: SpatialRollContext): Promise<MapMovementResult>
  resumeRuntimeTurn?(adventureId: string, turnId: string, idempotencyKey: string): Promise<{ turnId: string; version: number; movementResult?: MapMovementResult }>
  submitMapAction?(adventureId: string, candidate: MapActionCandidate, command?: { turnId: string; commandId: string }, expectedVersion?: number): Promise<{ turnId: string; version: number; movementResult?: MapMovementResult }>
  observeSpatial?(adventureId: string, request: SpatialActionRequest): Promise<SpatialActionResult>
  interactSpatial?(adventureId: string, request: SpatialActionRequest): Promise<SpatialActionResult>
  combatTurnStartSpatial?(adventureId: string, request: SpatialTurnRequest): Promise<SpatialActionResult>
  advanceSpatialDurations?(adventureId: string, request: SpatialTurnRequest): Promise<SpatialActionResult>
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 409 || response.status === 422) {
    const problem = await response.clone().json().catch(() => null) as { error?: string; message?: string } | null
    if (problem?.error === 'ADVENTURE_START_BLOCKED' && problem.message === 'combat map alignment save failed') {
      throw new Error('전체 격자가 지도 밖으로 나갑니다. 시작점을 지도 안쪽으로 옮기고 다시 맞추세요.')
    }
    if (problem?.error === 'MAP_PLACEMENT_REQUIRED') {
      throw new AdventureRequestError('맵은 준비됐지만 시작 위치가 정해지지 않았습니다. 지도에서 시작 위치를 선택해주세요.', response.status)
    }
    throw new AdventureRequestError('적용 규칙상 해당 요청을 처리할 수 없습니다.', response.status)
  }
  if (!response.ok) throw new Error('요청을 처리하지 못했습니다.')
  if (response.status === 204 || response.headers.get('content-length') === '0') return undefined as T
  return response.json() as Promise<T>
}

class AdventureRequestError extends Error {
  constructor(message: string, readonly status: number) { super(message); this.name = 'AdventureRequestError' }
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
    return request<DiceRollResponse>(`/api/v1/adventures/${adventureId}/dice-rolls`, {
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

  getCombatMapPreparation(adventureId: string) {
    return request<CombatMapView>(`/api/v1/adventures/${adventureId}/combat-map/preparation`, { headers: this.authHeaders() })
  }

  async getPublicMapImage(adventureId: string): Promise<string | null> {
    const alignment = await request<{ imageViewId?: string }>(`/api/v1/adventures/${adventureId}/combat-map/alignment`, {
      headers: this.authHeaders(),
    })
    if (!alignment.imageViewId) return null
    const response = await fetch(`/api/v1/adventures/${adventureId}/combat-map/alignment/image/${encodeURIComponent(alignment.imageViewId)}`, {
      headers: this.authHeaders(), cache: 'no-store',
    })
    if (!response.ok) throw new Error(`공개된 지도 이미지를 불러오지 못했습니다. (${response.status})`)
    return URL.createObjectURL(await response.blob())
  }

  async getCombatMapPreparationImage(adventureId: string): Promise<string | null> {
    const response = await fetch(`/api/v1/adventures/${adventureId}/combat-map/preparation-image`, { headers: this.authHeaders(), cache: 'no-store' })
    if (!response.ok) throw new Error('맵 준비 이미지를 불러오지 못했습니다.')
    return URL.createObjectURL(await response.blob())
  }

  detectMapBoundaries(adventureId: string) {
    return request<MapBoundaryProposal>(`/api/v1/adventures/${adventureId}/combat-map/detect-boundaries`, {
      method: 'POST', headers: this.authHeaders(),
    })
  }

  getMapGridAlignment(adventureId: string) {
    return request<MapGridAlignment>(`/api/v1/adventures/${adventureId}/combat-map/alignment`, {
      headers: this.authHeaders(),
    })
  }

  applyMapGridAlignment(adventureId: string, alignment: MapGridAlignmentRequest) {
    return request<MapGridAlignment>(`/api/v1/adventures/${adventureId}/combat-map/alignment`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...this.authHeaders() },
      body: JSON.stringify(alignment),
    })
  }

  updateCombatMapLayout(adventureId: string, draft: CombatMapLayoutDraft) {
    return request<void>(`/api/v1/adventures/${adventureId}/combat-map/layout`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json', ...this.authHeaders() }, body: JSON.stringify(draft),
    })
  }

  previewMapMovement(adventureId: string, preview: MapMovementPreviewRequest) {
    return request<MapMovementPreview>(`/api/v1/adventures/${adventureId}/combat-map/movement-preview`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders() }, body: JSON.stringify(preview),
    })
  }

  getPendingMapMovement(adventureId: string) {
    return request<PendingMapMovement | null>(`/api/v1/adventures/${adventureId}/map-movement/pending`, {
      headers: this.authHeaders(),
    }).then(pending => pending ?? null)
  }

  clearPendingMapMovement(adventureId: string) {
    return request<void>(`/api/v1/adventures/${adventureId}/map-movement/pending`, {
      method: 'DELETE', headers: this.authHeaders(),
    })
  }

  previewNaturalLanguageMovement(adventureId: string, preview: NaturalLanguageMovementPreviewRequest) {
    return request<NaturalLanguageMovementPreview>(`/api/v1/adventures/${adventureId}/map-movement/preview`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders() }, body: JSON.stringify(preview),
    })
  }

  confirmNaturalLanguageMovement(adventureId: string, confirmation: NaturalLanguageMovementConfirmation) {
    return request<NaturalLanguageMovementConfirmationResult>(`/api/v1/adventures/${adventureId}/map-movement/confirm`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders() }, body: JSON.stringify(confirmation),
    })
  }

  movementOperation(adventureId: string, mapId: string, operationId: string) {
    return request<MapMovementResult>(`/api/v1/adventures/${adventureId}/combat-map/movement-operations/${operationId}?mapId=${mapId}`, {
      headers: this.authHeaders(),
    })
  }

  latestMovementOperation(adventureId: string, mapId: string) {
    return request<MapMovementResult | null>(`/api/v1/adventures/${adventureId}/combat-map/movement-operations?mapId=${mapId}`, {
      headers: this.authHeaders(),
    }).catch(error => {
      // A map with no durable movement operation is a normal reconnect state.
      if (error instanceof AdventureRequestError && error.status === 404) return null
      throw error
    })
  }

  cancelMovementOperation(adventureId: string, mapId: string, operationId: string, cancelCommandId: string) {
    return request<MapMovementResult>(`/api/v1/adventures/${adventureId}/combat-map/movement-operations/${operationId}?mapId=${mapId}`, {
      method: 'DELETE', headers: { ...this.authHeaders(), 'Idempotency-Key': cancelCommandId },
    })
  }

  resumeMovementOperation(adventureId: string, mapId: string, operationId: string, check?: { commandId: string; operationId: string; checkId: string; success: boolean; ownerPlayerId: string; actor: 'PLAYER' }) {
    return request<MapMovementResult>(`/api/v1/adventures/${adventureId}/combat-map/movement-operations/${operationId}/resume?mapId=${mapId}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': check?.commandId ?? operationId, ...this.authHeaders() }, body: check ? JSON.stringify(check) : undefined,
    })
  }

  rollSpatialCheck(adventureId: string, expectedVersion: number, spatial: SpatialRollContext) {
    const commandId = spatial.checkId
    return request<MapMovementResult>(`/api/v1/adventures/${adventureId}/combat-map/movement-operations/${spatial.operationId}/roll`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders(), 'Idempotency-Key': commandId },
      body: JSON.stringify({ mapId: spatial.mapId, operationId: spatial.operationId, checkId: spatial.checkId,
        ownerPlayerId: spatial.ownerPlayerId, commandId, expectedVersion }),
    })
  }

  resumeRuntimeTurn(adventureId: string, turnId: string, idempotencyKey: string) {
    return request<{ turnId: string; version: number; movementResult?: MapMovementResult }>(`/api/v1/adventures/${adventureId}/turns/${turnId}/resume`, {
      method: 'POST', headers: { ...this.authHeaders(), 'Idempotency-Key': idempotencyKey },
    })
  }

  observeSpatial(adventureId: string, action: SpatialActionRequest) {
    return request<SpatialActionResult>(`/api/v1/adventures/${adventureId}/combat-map/spatial/observe`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders(), 'Idempotency-Key': action.commandId }, body: JSON.stringify(action),
    })
  }

  interactSpatial(adventureId: string, action: SpatialActionRequest) {
    return request<SpatialActionResult>(`/api/v1/adventures/${adventureId}/combat-map/spatial/interact`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders(), 'Idempotency-Key': action.commandId }, body: JSON.stringify(action),
    })
  }

  combatTurnStartSpatial(adventureId: string, action: SpatialTurnRequest) {
    return request<SpatialActionResult>(`/api/v1/adventures/${adventureId}/combat-map/spatial/combat-turn-start`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders(), 'Idempotency-Key': action.commandId }, body: JSON.stringify(action),
    })
  }

  submitMapAction(adventureId: string, candidate: MapActionCandidate, command = createMapCommandIdentity(), expectedVersion = candidate.mapVersion) {
    const { commandId: _commandId, ...requestCandidate } = candidate
    void _commandId
    return request<{ turnId: string; version: number; movementResult?: MapMovementResult }>(`/api/v1/adventures/${adventureId}/turns`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json', ...this.authHeaders(),
        'Idempotency-Key': command.commandId, 'If-Match-Version': String(expectedVersion),
      },
      body: JSON.stringify({ turnId: command.turnId, input: {
        type: 'MAP_ACTION', mapId: candidate.mapId, mapVersion: candidate.mapVersion,
        action: JSON.stringify(requestCandidate),
        previewFingerprint: candidate.action === 'MOVE' ? candidate.fingerprint : undefined,
      } }),
    }).then(result => ({ turnId: result.turnId, version: result.version, movementResult: result.movementResult }))
  }

  advanceSpatialDurations(adventureId: string, action: SpatialTurnRequest) {
    return request<SpatialActionResult>(`/api/v1/adventures/${adventureId}/combat-map/spatial/advance-durations`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', ...this.authHeaders(), 'Idempotency-Key': action.commandId }, body: JSON.stringify(action),
    })
  }
}

function createMapCommandIdentity() {
  const value = globalThis.crypto && 'randomUUID' in globalThis.crypto ? globalThis.crypto.randomUUID() : `${Date.now()}-${Math.random()}`
  return { turnId: value, commandId: value }
}
