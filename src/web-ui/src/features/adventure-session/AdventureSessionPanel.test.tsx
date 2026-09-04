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
    const start = await screen.findByRole('button', { name: '시나리오 런타임 시작' })
    expect((start as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('파티 정원 2명에 맞춰야 시작할 수 있습니다.')).toBeTruthy()
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
    const start = await screen.findByRole('button', { name: '시나리오 런타임 시작' })
    expect((start as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('런타임 설정이 없어 시나리오를 시작할 수 없습니다.')).toBeTruthy()
  })

  it('presents the party assembly workspace with clear preparation stages', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 3, version: 1, status: 'DRAFT', adventureId: null, runtimeConfiguration: { engineId: 'ollama' }, party: [{ characterSheetId: 'sheet-1', controlMode: 'DIRECT' }] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([{ characterSheetId: 'sheet-1', characterName: '리아', race: '엘프', characterClass: '로그', level: 1 }]),
      copyOwnedCharacter: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      complete: vi.fn(), delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    expect(await screen.findByText('파티 조립 현황')).toBeTruthy()
    expect(screen.getByRole('heading', { name: '내 플레이 캐릭터' })).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'AI 동료 제안' })).toBeTruthy()
    expect(screen.getByRole('button', { name: /시나리오 런타임 시작/ })).toBeTruthy()
  })
})
