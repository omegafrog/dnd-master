import { createContext, type PropsWithChildren, useContext, useEffect, useMemo, useState } from 'react'
import type { IdentityApi, IdentitySession, LoginCredentials } from './IdentityApi'

type AuthState = {
  session: IdentitySession | null
  message: string
  login(credentials: LoginCredentials): Promise<void>
  register(credentials: LoginCredentials): Promise<void>
  logout(): Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ api, children }: PropsWithChildren<{ api: IdentityApi }>) {
  const [session, setSession] = useState<IdentitySession | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!session) return
    const remaining = new Date(session.expiresAt).getTime() - Date.now()
    if (remaining <= 0) {
      setSession(null)
      setMessage('인증이 만료되었습니다. 다시 로그인하세요.')
      return
    }
    const timer = window.setTimeout(() => {
      setSession(null)
      setMessage('인증이 만료되었습니다. 다시 로그인하세요.')
    }, remaining)
    return () => window.clearTimeout(timer)
  }, [session])

  const value = useMemo<AuthState>(() => ({
    session,
    message,
    async login(credentials) {
      setMessage('')
      try {
        setSession(await api.login(credentials))
      } catch (error) {
        setMessage(error instanceof Error ? error.message : '로그인하지 못했습니다.')
      }
    },
    async register(credentials) {
      setMessage('')
      try {
        await api.register(credentials)
        setMessage('회원가입이 완료되었습니다. 로그인하세요.')
      } catch (error) {
        setMessage(error instanceof Error ? error.message : '회원가입하지 못했습니다.')
      }
    },
    async logout() {
      if (!session) return
      try {
        await api.logout(session.accessToken)
        setSession(null)
        setMessage('로그아웃되었습니다.')
      } catch (error) {
        setMessage(error instanceof Error ? error.message : '로그아웃하지 못했습니다.')
      }
    },
  }), [api, message, session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
