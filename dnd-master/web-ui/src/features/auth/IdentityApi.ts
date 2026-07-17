export type IdentitySession = {
  accessToken: string
  playerName: string
  expiresAt: string
}

export type LoginCredentials = {
  email: string
  password: string
}

export interface IdentityApi {
  login(credentials: LoginCredentials): Promise<IdentitySession>
  logout(accessToken: string): Promise<void>
}

export class HttpIdentityApi implements IdentityApi {
  async login(credentials: LoginCredentials): Promise<IdentitySession> {
    const response = await fetch('/api/public/identity/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials),
    })
    if (!response.ok) throw new Error('로그인하지 못했습니다.')
    return response.json() as Promise<IdentitySession>
  }

  async logout(accessToken: string): Promise<void> {
    const response = await fetch('/api/public/identity/sessions/current', {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    if (!response.ok) throw new Error('로그아웃하지 못했습니다.')
  }
}
