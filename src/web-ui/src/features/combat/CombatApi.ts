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
}

export interface CombatApi { readSnapshot(adventureId: string): Promise<CombatSnapshot | null> }

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
}
