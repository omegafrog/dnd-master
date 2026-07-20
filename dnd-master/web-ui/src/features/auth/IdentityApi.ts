export type IdentitySession = {
  accessToken: string
  playerName: string
  expiresAt: string
}

export type LoginCredentials = {
  email: string
  password: string
}

type LoginResponse = {
  token: string
  playerId: string
}

export interface IdentityApi {
  login(credentials: LoginCredentials): Promise<IdentitySession>
  logout(accessToken: string): Promise<void>
}

export class HttpIdentityApi implements IdentityApi {
  async login(credentials: LoginCredentials): Promise<IdentitySession> {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: credentials.email, password: credentials.password }),
    })
    if (!response.ok) throw new Error('로그인하지 못했습니다.')
    const body: LoginResponse = await response.json()
    return { accessToken: body.token, playerName: credentials.email, expiresAt: new Date(Date.now() + 86400000).toISOString() }
  }

  async logout(accessToken: string): Promise<void> {
    const response = await fetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    if (!response.ok) throw new Error('로그아웃하지 못했습니다.')
  }
}
