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
const SESSION_STORAGE_KEY = 'dnd-master.auth-session'

function readStoredSession(): IdentitySession | null {
  try {
    const raw = window.localStorage.getItem(SESSION_STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as IdentitySession
    if (!session.accessToken || new Date(session.expiresAt).getTime() <= Date.now()) {
      window.localStorage.removeItem(SESSION_STORAGE_KEY)
      return null
    }
    return session
  } catch {
    window.localStorage.removeItem(SESSION_STORAGE_KEY)
    return null
  }
}

export function AuthProvider({ api, children }: PropsWithChildren<{ api: IdentityApi }>) {
  const [session, setSession] = useState<IdentitySession | null>(() => readStoredSession())
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!session) {
      window.localStorage.removeItem(SESSION_STORAGE_KEY)
      return
    }
    window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
    const remaining = new Date(session.expiresAt).getTime() - Date.now()
    if (remaining <= 0) {
      setSession(null)
      window.localStorage.removeItem(SESSION_STORAGE_KEY)
      setMessage('인증이 만료되었습니다. 다시 로그인하세요.')
      return
    }
    const timer = window.setTimeout(() => {
      setSession(null)
      window.localStorage.removeItem(SESSION_STORAGE_KEY)
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
        window.localStorage.removeItem(SESSION_STORAGE_KEY)
        window.location.hash = '#/login'
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
