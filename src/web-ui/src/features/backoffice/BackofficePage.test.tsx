import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { BackofficePage } from './BackofficePage'

describe('BackofficePage catalog access', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads the administrator catalog with the opaque session token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([{
      catalogRevisionId: 'revision-1', edition: 'DND_5E_2014', displayName: 'Queued D&D 5e',
      rulebookId: 'rulebook-1', revisionNumber: 1, status: 'QUEUED', published: false,
    }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    render(<BackofficePage session={{
      accessToken: 'opaque-session', playerName: 'Admin', playerId: 'player-1',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    }} />)

    expect(await screen.findByText(/Queued D&D 5e/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/backoffice/rulebook-catalog', {
      headers: { Authorization: 'Bearer opaque-session' },
    })
  })

  it('offers publication for a ready unpublished revision', async () => {
    const fetchMock = vi.fn().mockImplementation((_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === 'POST') return Promise.resolve(new Response(null, { status: 204 }))
      return Promise.resolve(new Response(JSON.stringify([{
        catalogRevisionId: 'revision-ready', edition: 'DND_5E_2014', displayName: 'Ready D&D 5e',
        rulebookId: 'rulebook-ready', revisionNumber: 2, status: 'READY', published: false,
      }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<BackofficePage session={{
      accessToken: 'opaque-session', playerName: 'Admin', playerId: 'player-1',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    }} />)

    const publish = await screen.findByRole('button', { name: '공개' })
    expect(publish).toBeInTheDocument()
    await userEvent.setup().click(publish)
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/backoffice/rulebook-catalog/revision-ready/publish', {
      method: 'POST', headers: { Authorization: 'Bearer opaque-session' },
    }))
  })

  it('offers publication when indexing is complete even if the catalog revision remains queued', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([{
      catalogRevisionId: 'revision-indexed', edition: 'DND_5E_2014', displayName: 'Indexed D&D 5e',
      rulebookId: 'rulebook-indexed', revisionNumber: 3, status: 'QUEUED', processingStatus: 'INDEXED', published: false,
    }]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    render(<BackofficePage session={{
      accessToken: 'opaque-session', playerName: 'Admin', playerId: 'player-1',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    }} />)

    expect(await screen.findByRole('button', { name: '공개' })).toBeInTheDocument()
  })
})
