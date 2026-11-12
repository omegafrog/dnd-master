import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AdventureSessionPanel } from './AdventureSessionPanel'
import type { AdventureSessionApi, AdventureSessionView } from './AdventureSessionApi'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import { SessionRuntimeRoute } from '../adventure/SessionRuntimeRoute'
import type { AdventureApi } from '../adventure/AdventureApi'
import type { SetupApi } from '../rulebooks/SetupApi'
import type { CombatApi } from '../combat/CombatApi'

describe('AdventureSessionPanel lifecycle', () => {
  it('allows a direct character to start before the party reaches its maximum size', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 6, version: 3, status: 'DRAFT', adventureId: null, runtimeConfiguration: { engineId: 'ollama' }, party: [{ characterSheetId: 'sheet-1', controlMode: 'DIRECT' }] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([]),
      copyOwnedCharacter: vi.fn(),
      addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      complete: vi.fn(), delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    const start = await screen.findByRole('button', { name: '시나리오 런타임 시작' })
    expect((start as HTMLButtonElement).disabled).toBe(false)
    expect(screen.getByText('최대 6명까지 참가할 수 있습니다. 시작하려면 사용자가 직접 조작할 캐릭터가 한 명 이상 필요합니다.')).toBeTruthy()
  })

  it('still requires one directly controlled character before starting', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 6, version: 3, status: 'DRAFT', adventureId: null, runtimeConfiguration: { engineId: 'ollama' }, party: [{ characterSheetId: 'sheet-1', controlMode: 'AGENT' }] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([]),
      copyOwnedCharacter: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      complete: vi.fn(), delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    const start = await screen.findByRole('button', { name: '시나리오 런타임 시작' })
    expect((start as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('직접 조작할 캐릭터를 한 명 이상 추가해야 시작할 수 있습니다.')).toBeTruthy()
  })

  it('does not let the player complete a session manually', async () => {
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 1, version: 3, status: 'STARTED', adventureId: 'a', runtimeConfiguration: null, party: [] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([]),
      copyOwnedCharacter: vi.fn(),
      addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(),
      delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    expect(await screen.findByRole('button', { name: '세션 삭제' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '세션 완료' })).toBeNull()
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

  it('shows runtime preparation progress and ignores repeated starts', async () => {
    let resolveStart!: (value: AdventureSessionView) => void
    const start = vi.fn<AdventureSessionApi['start']>((sessionId, version, adventureId, prepareMapOnly) => {
      void sessionId
      void version
      void adventureId
      void prepareMapOnly
      return new Promise<AdventureSessionView>(resolve => { resolveStart = resolve })
    })
    const api = {
      read: vi.fn().mockResolvedValue({ sessionId: 's', characterLimit: 1, version: 3, status: 'DRAFT', adventureId: null, runtimeConfiguration: { engineId: 'ollama' }, party: [{ characterSheetId: 'sheet-1', controlMode: 'DIRECT' }] }),
      listOwnedCharacters: vi.fn().mockResolvedValue([]),
      copyOwnedCharacter: vi.fn(), addMember: vi.fn(), removeMember: vi.fn(), start,
      complete: vi.fn(), delete: vi.fn(),
    }
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" />)
    const button = await screen.findByRole('button', { name: '시나리오 런타임 시작' })
    await userEvent.click(button)
    expect(start).toHaveBeenCalledTimes(1)
    expect((screen.getByRole('button', { name: '시나리오 런타임 준비 중…' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText('맵을 준비하는 동안 잠시만 기다려 주세요. 같은 요청을 다시 보내지 않습니다.')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: '시나리오 런타임 준비 중…' }))
    expect(start).toHaveBeenCalledTimes(1)
    resolveStart({ sessionId: 's', characterLimit: 1, version: 4, status: 'STARTING', adventureId: 'a', runtimeConfiguration: { scenarioId: 'scenario', ruleSetId: 'rules', rulebookIds: [], engineId: 'ollama', toolIds: [], initialScene: 'intro' }, party: [{ characterSheetId: 'sheet-1', controlMode: 'DIRECT', nameMutableAfterStart: false, raceMutableAfterStart: false, characterClassMutableAfterStart: false, backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false }] })
    await waitFor(() => expect(screen.queryByRole('button', { name: '시나리오 런타임 준비 중…' })).toBeNull())
  })

  it('resumes map preparation from the same starting adventure when its draft is missing', async () => {
    const starting: AdventureSessionView = { sessionId: 's', characterLimit: 1, version: 2, status: 'STARTING', adventureId: 'a', runtimeConfiguration: { scenarioId: 'scenario', ruleSetId: 'rules', rulebookIds: [], engineId: 'ollama', toolIds: [], initialScene: 'intro' }, party: [{ characterSheetId: 'sheet-1', controlMode: 'DIRECT', nameMutableAfterStart: false, raceMutableAfterStart: false, characterClassMutableAfterStart: false, backgroundMutableAfterStart: false, startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false }] }
    const api = {
      read: vi.fn().mockResolvedValue(starting), listOwnedCharacters: vi.fn().mockResolvedValue([]), copyOwnedCharacter: vi.fn(),
      addMember: vi.fn(), removeMember: vi.fn(), start: vi.fn(), prepareMap: vi.fn().mockResolvedValue(starting), delete: vi.fn(),
    }
    const getCombatMapPreparation = vi.fn().mockRejectedValueOnce(new Error('map draft is missing')).mockResolvedValue({
      adventureId: 'a', status: 'authoritative-map', mapId: 'm1', version: 0, grid: { width: 2, height: 2 }, tokens: [],
    })
    const playApi = { getCombatMapPreparation, getMapGridAlignment: vi.fn().mockRejectedValue(new Error('not saved')) } as unknown as AdventurePlayApi
    render(<AdventureSessionPanel api={api} ownerPlayerId="p" sessionId="s" playApi={playApi} />)

    await userEvent.click(await screen.findByRole('button', { name: '맵 준비 다시 불러오기' }))

    expect(api.prepareMap).toHaveBeenCalledTimes(1)
    expect(api.prepareMap).toHaveBeenCalledWith('s', 2, 'a')
    await waitFor(() => expect(getCombatMapPreparation).toHaveBeenCalledTimes(2))
  })

  it('keeps the turn input closed until the starting session finishes map preparation', async () => {
    const session: AdventureSessionView = {
      sessionId: 's', characterLimit: 1, version: 2, status: 'STARTING', adventureId: 'a',
      runtimeConfiguration: null, storyLength: 'NORMAL', party: [],
    }
    const sessionApi = { read: vi.fn().mockResolvedValue(session) } as unknown as AdventureSessionApi
    const combatApi = { readSnapshot: vi.fn().mockRejectedValue(new Error('no combat yet')) } as unknown as CombatApi

    render(<SessionRuntimeRoute
      sessionId="s"
      sessionApi={sessionApi}
      adventureApi={{} as AdventureApi}
      playApi={{} as AdventurePlayApi}
      setupApi={{} as SetupApi}
      combatApi={combatApi}
    />)

    expect(await screen.findByRole('heading', { name: '세션 시작을 완료하는 중입니다' })).toBeTruthy()
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.getByRole('link', { name: '세션 준비 화면으로 돌아가기' }).getAttribute('href')).toBe('#/sessions/s')
  })
})
