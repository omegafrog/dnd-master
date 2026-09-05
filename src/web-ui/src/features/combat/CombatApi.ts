export type CombatParticipant = {
  participantId: string
  displayName: string
  controller: 'PLAYER' | 'AI'
  initiative: number
  publicCondition: string | null
}

export type CombatSnapshot = {
  encounterId: string
  adventureId: string
  status: 'PREPARING' | 'ACTIVE' | 'ENDED'
  round: number
  currentParticipantId: string
  initiative: CombatParticipant[]
  resources: { movement: number; actionAvailable: boolean; bonusActionAvailable: boolean; reactionAvailable: boolean }
  version: number
  eventCursor: number
  narrativePositions?: Array<{ subjectId: string; targetId: string; rangeBand: string; cover: string }>
}

export type CombatActionRequest = {
  characterSheetId: string
  action: string
  targetArmorClass?: number
  attackModifier?: number
  targetCharacterSheetId?: string
  damageAmount?: number
  combatMapId?: string
  tokenId?: string
  movementPath?: Array<{ x: number; y: number }>
  narrativePosition?: { subjectId: string; targetId: string; rangeBand: string; cover: string }
  movementDistance?: number
}

export type CombatCommandResult = {
  status: string
  encounterId?: string
  operationId?: string
  encounterVersion?: number
  diceTotal?: number
  judgment?: string
  violations?: string[]
}

export interface CombatApi {
  readSnapshot(adventureId: string): Promise<CombatSnapshot | null>
  submitAction(adventureId: string, request: CombatActionRequest, version: number): Promise<CombatCommandResult>
  submitMovement?(adventureId: string, request: CombatActionRequest, version: number): Promise<CombatCommandResult>
  endTurn(adventureId: string, characterSheetId: string, version: number): Promise<CombatCommandResult>
}

export class HttpCombatApi implements CombatApi {
  constructor(private readonly getToken: () => string) {}
  async readSnapshot(adventureId: string): Promise<CombatSnapshot | null> {
    const response = await fetch(`/api/v1/adventures/${adventureId}/combat`, {
      headers: { Authorization: `Bearer ${this.getToken()}` },
    })
    if (response.status === 404) return null
    if (!response.ok) throw new Error(`combat snapshot failed: ${response.status}`)
    return response.json() as Promise<CombatSnapshot | null>
  }

  async submitAction(adventureId: string, request: CombatActionRequest, version: number): Promise<CombatCommandResult> {
    return this.postCommand(`/api/v1/adventures/${adventureId}/combat/actions`, request, version)
  }

  async submitMovement(adventureId: string, request: CombatActionRequest, version: number): Promise<CombatCommandResult> {
    return this.submitAction(adventureId, { ...request, action: 'MOVE' }, version)
  }

  async endTurn(adventureId: string, characterSheetId: string, version: number): Promise<CombatCommandResult> {
    return this.postCommand(`/api/v1/adventures/${adventureId}/combat/turn/end`, { characterSheetId }, version)
  }

  private async postCommand(path: string, body: unknown, version: number): Promise<CombatCommandResult> {
    const idempotencyKey = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    const response = await fetch(path, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${this.getToken()}`,
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
        'If-Match-Version': String(version),
      },
      body: JSON.stringify(body),
    })
    if (!response.ok) throw new Error(`combat command failed: ${response.status}`)
    return response.json() as Promise<CombatCommandResult>
  }
}
