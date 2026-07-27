import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureSessionPanel } from './AdventureSessionPanel'

describe('AdventureSessionPanel lifecycle', () => {
  it('offers termination actions and submits completion', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 1, version: 3, status: 'STARTED', adventureId: 'a', runtimeConfiguration: null, party: [] }),
      addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      complete: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 1, version: 4, status: 'COMPLETED', adventureId: 'a', runtimeConfiguration: null, party: [] }),
      delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} sessionId="s" />)
    expect(await screen.findByRole('button', { name: '세션 완료' })).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: '세션 완료' }))
    expect(api.complete).toHaveBeenCalledWith('s', 3)
  })
})
