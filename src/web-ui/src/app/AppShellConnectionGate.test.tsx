import '@testing-library/jest-dom/vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'
import type { IdentityApi, IdentitySession } from '../features/auth/IdentityApi'

const session: IdentitySession = {
  accessToken: 'gate-session',
  playerName: 'Adventurer',
  playerId: 'player-1',
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
}

const identityApi: IdentityApi = {
  login: async () => session,
  register: async () => undefined,
  logout: async () => undefined,
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  window.location.hash = ''
  vi.unstubAllGlobals()
})

describe('AppShell Codex connection gate', () => {
  it('keeps application routes unavailable until the installation is connected', async () => {
    localStorage.setItem('dnd-master.auth-session', JSON.stringify(session))
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/profile/codex-connection')) {
        return Response.json({ status: 'AUTH_REQUIRED', cliAvailable: true })
      }
      return Response.json([])
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App identityApi={identityApi} />)

    expect(await screen.findByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/profile/codex-connection',
      expect.objectContaining({ headers: { Authorization: 'Bearer gate-session' } })))
    expect(screen.queryByRole('navigation', { name: '주요 메뉴' })).not.toBeInTheDocument()
  })

  it('gates protected routes when an account switch is cancelled from profile settings', async () => {
    localStorage.setItem('dnd-master.auth-session', JSON.stringify(session))
    window.location.hash = '#/profile'
    let switchStarted = false
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/profile/codex-connection/operations') && init?.method === 'POST') {
        switchStarted = true
        return Response.json({ operationId: 'switch-1', status: 'AUTHENTICATING', pending: true,
          authUrl: 'https://auth.example/approve' })
      }
      if (url.endsWith('/profile/codex-connection/operations/switch-1')) {
        return Response.json({ operationId: 'switch-1', status: 'CANCELLED', pending: false,
          message: 'Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.' })
      }
      if (url.endsWith('/profile/codex-connection')) {
        return Response.json({ status: switchStarted ? 'DISCONNECTED' : 'CONNECTED', cliAvailable: true })
      }
      if (url.endsWith('/profile/agent-endpoints')) return Response.json([])
      return Response.json({})
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(window, 'open').mockReturnValue({ opener: null, location: { href: 'about:blank' }, close: vi.fn() } as unknown as Window)

    render(<App identityApi={identityApi} />)

    fireEvent.click(await screen.findByRole('button', { name: '다른 Codex 계정으로 전환' }))

    expect(await screen.findByRole('heading', { name: 'Codex 계정을 연결해 주세요' }, { timeout: 5000 })).toBeInTheDocument()
    expect(await screen.findAllByText('Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.', { exact: true }, { timeout: 5000 })).not.toHaveLength(0)
    expect(screen.getByRole('button', { name: /^Codex 계정 연결$/ })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: '주요 메뉴' })).not.toBeInTheDocument()
    await act(async () => { await new Promise(resolve => window.setTimeout(resolve, 1700)) })
    expect(await screen.findAllByText('Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.', { exact: true })).not.toHaveLength(0)
    expect(screen.getByRole('button', { name: /^Codex 계정 연결$/ })).toBeInTheDocument()
  }, 12000)

  it('keeps a completed cancellation when a later status refresh has no operation id', async () => {
    localStorage.setItem('dnd-master.auth-session', JSON.stringify(session))
    window.location.hash = '#/profile'
    let switchStarted = false
    let disconnectedStatusReads = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/profile/codex-connection/operations') && init?.method === 'POST') {
        switchStarted = true
        return Response.json({ operationId: 'switch-2', status: 'AUTHENTICATING', pending: true })
      }
      if (url.endsWith('/profile/codex-connection/operations/switch-2')) {
        return Response.json({ operationId: 'switch-2', status: 'CANCELLED', pending: false,
          message: 'Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.' })
      }
      if (url.endsWith('/profile/codex-connection')) {
        if (!switchStarted) return Response.json({ status: 'CONNECTED', cliAvailable: true })
        disconnectedStatusReads += 1
        return Response.json({ status: 'DISCONNECTED', cliAvailable: true })
      }
      if (url.endsWith('/profile/agent-endpoints')) return Response.json([])
      return Response.json({})
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(window, 'open').mockReturnValue({ opener: null, location: { href: 'about:blank' }, close: vi.fn() } as unknown as Window)

    render(<App identityApi={identityApi} />)
    fireEvent.click(await screen.findByRole('button', { name: '다른 Codex 계정으로 전환' }))

    expect(await screen.findByRole('heading', { name: 'Codex 계정을 연결해 주세요' })).toBeInTheDocument()
    expect(await screen.findAllByText('Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.', { exact: true })).not.toHaveLength(0)
    await act(async () => { await new Promise(resolve => window.setTimeout(resolve, 1700)) })

    expect(disconnectedStatusReads).toBeGreaterThan(0)
    expect(await screen.findAllByText('Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.', { exact: true })).not.toHaveLength(0)
    expect(screen.getByRole('button', { name: /^Codex 계정 연결$/ })).toBeInTheDocument()
  }, 12000)
})
