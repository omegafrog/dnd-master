export type TokenVisualFaction = 'PLAYER' | 'FRIENDLY' | 'NEUTRAL' | 'HOSTILE'
export type TokenVisualStatus = 'CURRENT_TURN' | 'SELECTED' | 'LAST_SEEN' | 'NONE'

export type TokenVisualToken = {
  id: string
  type: string
  x: number
  y: number
  selected?: boolean
  currentTurn?: boolean
  lastSeen?: boolean
}

export type TokenVisualContext = {
  selectedTokenId?: string | null
  currentTurnTokenId?: string | null
}

export type TokenVisual = {
  assetPath: string
  framePath: string
  faction: TokenVisualFaction
  primaryStatus: TokenVisualStatus
  classes: string[]
  isThumbnail: boolean
}

type TokenVisualEntry = {
  assetPath: string
  faction: TokenVisualFaction
}

const FRAME_PATH = '/assets/tokens/token-frame.svg'
const STATUS_PRIORITY: TokenVisualStatus[] = ['CURRENT_TURN', 'SELECTED', 'LAST_SEEN', 'NONE']

const TOKEN_VISUALS: Record<string, TokenVisualEntry> = {
  PLAYER: { assetPath: '/assets/tokens/player.svg', faction: 'PLAYER' },
  FRIENDLY_NPC: { assetPath: '/assets/tokens/friendly-npc.svg', faction: 'FRIENDLY' },
  NEUTRAL_NPC: { assetPath: '/assets/tokens/neutral-npc.svg', faction: 'NEUTRAL' },
  ENEMY: { assetPath: '/assets/tokens/enemy.svg', faction: 'HOSTILE' },
  BOSS: { assetPath: '/assets/tokens/boss.svg', faction: 'HOSTILE' },
  TRAP: { assetPath: '/assets/tokens/trap.svg', faction: 'NEUTRAL' },
  OBJECT: { assetPath: '/assets/tokens/object.svg', faction: 'NEUTRAL' },
}

const FALLBACK_VISUAL: TokenVisualEntry = { assetPath: '/assets/tokens/object.svg', faction: 'NEUTRAL' }

function statusFor(token: TokenVisualToken, context: TokenVisualContext): TokenVisualStatus[] {
  const selected = token.selected === true || context.selectedTokenId === token.id
  const currentTurn = token.currentTurn === true || context.currentTurnTokenId === token.id
  return [
    ...(currentTurn ? ['CURRENT_TURN' as const] : []),
    ...(selected ? ['SELECTED' as const] : []),
    ...(token.lastSeen ? ['LAST_SEEN' as const] : []),
  ]
}

function primaryStatus(statuses: TokenVisualStatus[]): TokenVisualStatus {
  return STATUS_PRIORITY.find(status => statuses.includes(status)) ?? 'NONE'
}

export class TokenVisualCatalog {
  constructor(private readonly entries: Record<string, TokenVisualEntry> = TOKEN_VISUALS) {}

  resolve(token: TokenVisualToken, context: TokenVisualContext = {}): TokenVisual {
    const entry = this.entries[token.type] ?? FALLBACK_VISUAL
    const statuses = statusFor(token, context)
    const status = primaryStatus(statuses)
    return {
      assetPath: entry.assetPath,
      framePath: FRAME_PATH,
      faction: entry.faction,
      primaryStatus: status,
      classes: [
        `token-faction-${entry.faction.toLowerCase()}`,
        ...statuses.map(value => `token-status-${value.toLowerCase().replaceAll('_', '-')}`),
      ],
      isThumbnail: statuses.includes('LAST_SEEN'),
    }
  }
}

export const tokenVisualCatalog = new TokenVisualCatalog()

