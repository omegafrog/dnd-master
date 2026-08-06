import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureSessionPanel } from './AdventureSessionPanel'

describe('AdventureSessionPanel lifecycle', () => {
  it('requires the storybook-defined party capacity before starting', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 2, version: 3, status: 'DRAFT', adventureId: null, runtimeConfiguration: null, party: [{ characterSheetId: 'sheet-1', controlMode: 'DIRECT' }] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([]),
      copyOwnedCharacter: vi.fn(),
      addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      complete: vi.fn(), delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    const start = await screen.findByRole('button', { name: '모험 계획 만들기' })
    expect((start as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('파티 정원 2명에 맞춰야 합니다.')).toBeTruthy()
  })

  it('offers termination actions and submits completion', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 1, version: 3, status: 'STARTED', adventureId: 'a', runtimeConfiguration: null, party: [] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([]),
      copyOwnedCharacter: vi.fn(),
      addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      complete: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 1, version: 4, status: 'COMPLETED', adventureId: 'a', runtimeConfiguration: null, party: [] }),
      delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    expect(await screen.findByRole('button', { name: '세션 완료' })).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: '세션 완료' }))
    await userEvent.click(screen.getByRole('button', { name: '종료 확인' }))
    expect(api.complete).toHaveBeenCalledWith('s', 3)
  })

  it('blocks start when runtime configuration is missing', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 1, version: 3, status: 'DRAFT', adventureId: null, runtimeConfiguration: null, party: [{ characterSheetId: 'sheet-1', controlMode: 'DIRECT' }] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([]),
      copyOwnedCharacter: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      complete: vi.fn(), delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    const start = await screen.findByRole('button', { name: '모험 계획 만들기' })
    expect((start as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('런타임 설정이 없어 계획을 만들 수 없습니다.')).toBeTruthy()
  })
})
