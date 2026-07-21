import '@testing-library/jest-dom/vitest'
import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import type { IdentityApi, IdentitySession, LoginCredentials } from './IdentityApi'

class FakeIdentityApi implements IdentityApi {
  credentials?: LoginCredentials
  logoutToken?: string
  session: IdentitySession = {
    accessToken: 'public-api-token',
    playerName: 'Minsc',
    playerId: 'player-1',
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
  }

  async login(credentials: LoginCredentials) {
    this.credentials = credentials
    return this.session
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async register(_credentials: LoginCredentials) { /* noop */ }

  async logout(accessToken: string) {
    this.logoutToken = accessToken
  }
}

afterEach(() => vi.useRealTimers())

describe('authentication flow', () => {
  it('provides an accessible login and authenticated navigation', async () => {
    const api = new FakeIdentityApi()
    const user = userEvent.setup()
    render(<App identityApi={api} />)

    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('이메일'), 'hero@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'swordfish')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(api.credentials).toEqual({ email: 'hero@example.com', password: 'swordfish' })
    expect(screen.getByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument()
    expect(screen.getByText('Minsc님 환영합니다!')).toBeInTheDocument()
  })

  it('logs out through the Identity public API and returns to login', async () => {
    const api = new FakeIdentityApi()
    const user = userEvent.setup()
    render(<App identityApi={api} />)
    await user.type(screen.getByLabelText('이메일'), 'hero@example.com')
    await user.type(screen.getByLabelText('비밀번호'), 'swordfish')
    await user.click(screen.getByRole('button', { name: '로그인' }))
    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(api.logoutToken).toBe('public-api-token')
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('로그아웃되었습니다.')
  })

  it('expires authentication, hides navigation, and announces reauthentication', async () => {
    vi.useFakeTimers()
    const api = new FakeIdentityApi()
    api.session.expiresAt = new Date(Date.now() + 1_000).toISOString()
    render(<App identityApi={api} />)
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'hero@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'swordfish' } })
    await act(async () => fireEvent.click(screen.getByRole('button', { name: '로그인' })))

    await act(async () => vi.advanceTimersByTime(1_001))

    expect(screen.queryByRole('navigation', { name: '주요 메뉴' })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('인증이 만료되었습니다.')
    expect(screen.getByRole('heading', { name: '로그인' })).toBeInTheDocument()
  })
})
