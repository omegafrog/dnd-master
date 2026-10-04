import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AiEndpointSettings } from './AiEndpointSettings'
import type { IdentitySession } from '../auth/IdentityApi'

const session: IdentitySession = {
  accessToken: 'profile-session',
  playerName: 'Adventurer',
  playerId: 'player-1',
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
}

afterEach(() => vi.unstubAllGlobals())

describe('Codex account settings', () => {
  it('recovers an in-progress login operation from connection status after a lost start response', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/profile/codex-connection/operations/op-recovered')) {
        return Response.json({ operationId: 'op-recovered', status: 'AUTHENTICATING', pending: true,
          authUrl: 'https://auth.example/recovered' })
      }
      if (url.endsWith('/profile/codex-connection')) {
        return Response.json({ status: 'AUTHENTICATING', cliAvailable: true, operationId: 'op-recovered' })
      }
      return Response.json([])
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<AiEndpointSettings session={session} />)

    expect(await screen.findByRole('button', { name: '승인 페이지 다시 열기' })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/profile/codex-connection/operations/op-recovered',
      expect.objectContaining({ headers: { Authorization: 'Bearer profile-session' } }))
  })

  it('recovers a cancelled result from the gate hint after status has become disconnected', async () => {
    const operationMessage = '승인 흐름이 취소되었습니다. 다시 연결할 수 있습니다.'
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/profile/codex-connection/operations/op-cancelled')) {
        return Response.json({ operationId: 'op-cancelled', status: 'CANCELLED', pending: false,
          message: operationMessage })
      }
      if (url.endsWith('/profile/codex-connection')) {
        return Response.json({ status: 'DISCONNECTED', cliAvailable: true })
      }
      return Response.json([])
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<AiEndpointSettings session={session} connectionOnly connectionHint={{
      status: 'CANCELLED', cliAvailable: true, operationId: 'op-cancelled',
    }} />)

    expect(await screen.findAllByText(operationMessage)).not.toHaveLength(0)
    expect(screen.getByRole('button', { name: /^Codex 계정 연결$/ })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/profile/codex-connection/operations/op-cancelled',
      expect.objectContaining({ headers: { Authorization: 'Bearer profile-session' } }))
  })

  it('loads a terminal operation when the gate receives its operation hint after mounting', async () => {
    const operationMessage = 'Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.'
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/profile/codex-connection/operations/op-late')) {
        return Response.json({ operationId: 'op-late', status: 'CANCELLED', pending: false, message: operationMessage })
      }
      if (url.endsWith('/profile/codex-connection')) {
        return Response.json({ status: 'DISCONNECTED', cliAvailable: true })
      }
      return Response.json([])
    })
    vi.stubGlobal('fetch', fetchMock)

    const { rerender } = render(<AiEndpointSettings session={session} connectionOnly connectionHint={{
      status: 'DISCONNECTED', cliAvailable: true,
    }} />)

    rerender(<AiEndpointSettings session={session} connectionOnly connectionHint={{
      status: 'CANCELLED', cliAvailable: true, operationId: 'op-late', message: operationMessage,
    }} />)

    expect(await screen.findAllByText(operationMessage)).not.toHaveLength(0)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/profile/codex-connection/operations/op-late',
      expect.objectContaining({ headers: { Authorization: 'Bearer profile-session' } }))
  })

  it('starts an explicit account switch, opens the returned approval URL, and polls operation status', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/profile/codex-connection') && init?.method === 'DELETE') {
        return Response.json({ status: 'DISCONNECTED', cliAvailable: true })
      }
      if (url.endsWith('/profile/codex-connection/operations') && init?.method === 'POST') {
        return Response.json({ operationId: 'op-1', status: 'AUTHENTICATING', pending: true,
          authUrl: 'https://auth.example/approve', message: '승인해 주세요.' })
      }
      if (url.endsWith('/profile/codex-connection/operations/op-1')) {
        return Response.json({ operationId: 'op-1', status: 'CONNECTED', pending: false })
      }
      if (url.endsWith('/profile/codex-connection')) {
        return Response.json({ status: 'CONNECTED', cliAvailable: true })
      }
      return Response.json([])
    })
    vi.stubGlobal('fetch', fetchMock)
    const popup = { opener: window, location: { href: 'about:blank' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(popup as unknown as Window)

    render(<AiEndpointSettings session={session} />)
    fireEvent.click(await screen.findByRole('button', { name: '다른 Codex 계정으로 전환' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/profile/codex-connection/operations',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ type: 'SWITCH_ACCOUNT' }) }),
    ))
    expect(popup.location.href).toBe('https://auth.example/approve')
    expect(await screen.findByRole('button', { name: '승인 페이지 다시 열기' })).toBeInTheDocument()
  })

  it('disconnects only this installation and explains that Codex remains signed in', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/profile/codex-connection') && init?.method === 'DELETE') {
        return Response.json({ status: 'DISCONNECTED', cliAvailable: true })
      }
      if (url.endsWith('/profile/codex-connection')) return Response.json({ status: 'CONNECTED', cliAvailable: true })
      return Response.json([])
    })
    vi.stubGlobal('fetch', fetchMock)
    render(<AiEndpointSettings session={session} />)

    fireEvent.click(await screen.findByRole('button', { name: '이 설치의 연결 해제' }))

    expect(await screen.findByText('이 설치의 Codex 연결을 해제했습니다. Codex CLI 로그인은 유지됩니다.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/profile/codex-connection',
      expect.objectContaining({ method: 'DELETE' }))
  })
})
