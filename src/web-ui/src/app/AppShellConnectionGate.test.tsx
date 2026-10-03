import '@testing-library/jest-dom/vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
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
})
