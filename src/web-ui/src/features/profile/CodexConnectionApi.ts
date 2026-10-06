import type { IdentitySession } from '../auth/IdentityApi'

export type CodexConnectionStatus =
  | 'CLI_UNAVAILABLE'
  | 'AUTH_REQUIRED'
  | 'AUTHENTICATING'
  | 'CONNECTED'
  | 'REAUTH_REQUIRED'
  | 'DISCONNECTED'
  | 'FAILED'
  | 'CANCELLED'
  | 'UNAVAILABLE'

export type CodexConnectionState = {
  status: CodexConnectionStatus
  cliAvailable: boolean
  operationId?: string | null
  message?: string | null
}

export type CodexConnectionOperation = {
  operationId: string
  status: CodexConnectionStatus
  authUrl?: string | null
  message?: string | null
  pending: boolean
}

export class CodexConnectionApi {
  constructor(private readonly session: IdentitySession) {}

  async status(): Promise<CodexConnectionState> {
    return this.request('/api/v1/profile/codex-connection')
  }

  async start(type: 'CONNECT' | 'REAUTHENTICATE' | 'SWITCH_ACCOUNT'): Promise<CodexConnectionOperation> {
    return this.request('/api/v1/profile/codex-connection/operations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type }),
    })
  }

  async operation(operationId: string): Promise<CodexConnectionOperation> {
    return this.request(`/api/v1/profile/codex-connection/operations/${encodeURIComponent(operationId)}`)
  }

  async disconnect(): Promise<CodexConnectionState> {
    return this.request('/api/v1/profile/codex-connection', { method: 'DELETE' })
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(path, {
      ...init,
      headers: { Authorization: `Bearer ${this.session.accessToken}`, ...init.headers },
    })
    if (!response.ok) throw new Error('Codex 계정 연결 요청을 완료하지 못했습니다. 다시 시도해 주세요.')
    return await response.json() as T
  }
}
