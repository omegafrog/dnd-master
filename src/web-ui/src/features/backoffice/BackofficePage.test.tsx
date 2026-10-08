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

  it('requires opening the source PDF and confirming a page candidate before retry', async () => {
    const review = { rulebookId: 'rulebook-1', status: 'NEEDS_REVIEW', contentHash: 'hash',
      candidateExtractionVersion: 'ev-1', preprocessingPages: [{ pageNumber: 23, status: 'NEEDS_REVIEW', attempts: 1,
        findings: ['AMBIGUOUS_COLUMN_HYPOTHESIS'], layoutReview: { blocks: [{ blockId: 'b', text: 'source text', bbox: [0, 0, 1, 1] }],
          regions: [{ regionId: 'r1', candidates: [{ candidateIndex: 0, columnCount: 2, score: 0.791, columns: [] }] }] } }] }
    const calls: Array<{ path: string; init?: RequestInit }> = []
    vi.stubGlobal('fetch', vi.fn().mockImplementation((path: string, init?: RequestInit) => {
      calls.push({ path, init })
      if (path.endsWith('/source')) return Promise.resolve(new Response(new Blob(['pdf'], { type: 'application/pdf' })))
      if (path.endsWith('/review') || path.endsWith('/retry-pages')) return Promise.resolve(new Response(JSON.stringify(review), { headers: { 'Content-Type': 'application/json' } }))
      return Promise.resolve(new Response(JSON.stringify([{ catalogRevisionId: 'rev-1', edition: 'DND_5E_2014', displayName: 'D&D 5e',
        rulebookId: 'rulebook-1', revisionNumber: 1, status: 'QUEUED', published: false }]), { headers: { 'Content-Type': 'application/json' } }))
    }))
    vi.stubGlobal('crypto', { randomUUID: () => 'request-1' })
    vi.stubGlobal('URL', Object.assign(class extends URL {}, {
      createObjectURL: () => 'blob:source', revokeObjectURL: () => {},
    }))
    const user = userEvent.setup()
    render(<BackofficePage session={{ accessToken: 'opaque-session', playerName: 'Admin', playerId: 'admin',
      expiresAt: new Date(Date.now() + 60_000).toISOString() }} />)
    await user.click(await screen.findByRole('button', { name: '페이지 검토' }))
    await user.click(await screen.findByRole('button', { name: '23쪽' }))
    const retry = screen.getByRole('button', { name: '선택한 페이지 재검증' })
    expect(retry).toBeDisabled()
    await user.click(screen.getByRole('button', { name: '원본 PDF 열기' }))
    expect(await screen.findByRole('link', { name: '원본 PDF 내려받기' })).toHaveAttribute('href', 'blob:source')
    await user.selectOptions(screen.getByLabelText(/영역 r1의 읽기 순서 후보/), '0')
    await user.click(screen.getByRole('checkbox', { name: '원본 PDF와 선택한 후보를 대조했습니다.' }))
    await user.click(retry)
    await waitFor(() => expect(calls.some(call => call.path.endsWith('/retry-pages'))).toBe(true))
    const sent = calls.find(call => call.path.endsWith('/retry-pages'))!
    expect(JSON.parse(String(sent.init?.body))).toMatchObject({ candidateExtractionVersion: 'ev-1', pages: [23],
      layoutSelections: { 23: { r1: 0 } }, confirmedAgainstSource: true })
  })
})
