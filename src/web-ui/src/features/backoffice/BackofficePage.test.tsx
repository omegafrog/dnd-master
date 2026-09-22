import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
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
})
