import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SessionRuntime } from './SessionRuntime'
import type { AdventureApi } from './AdventureApi'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'

vi.mock('./AdventureStream', () => ({ AdventureStream: ({ onCurrentSceneChanged }: { onCurrentSceneChanged?: (scene: string) => void }) => <div><button type="button" onClick={() => onCurrentSceneChanged?.('맥주 저장고')}>장면 변경 테스트</button>게임 기록</div> }))

describe('SessionRuntime', () => {
  it('현재 장면이 갱신되면 현재 위치에 반영한다', async () => {
    const user = userEvent.setup()
    render(
      <SessionRuntime
        adventureId="adventure-1"
        adventureApi={{} as AdventureApi}
        playApi={{} as AdventurePlayApi}
        initialScene="시작 장면"
      />,
    )

    expect(screen.getByText('시작 장면')).toBeVisible()
    await user.click(screen.getByRole('button', { name: '장면 변경 테스트' }))
    expect(screen.getByText('맥주 저장고')).toBeVisible()
  })

  it('이름이 같은 파티원도 캐릭터 시트 ID로 전투 상태와 현재 차례를 연결한다', () => {
    render(
      <SessionRuntime
        adventureId="adventure-1"
        adventureApi={{} as AdventureApi}
        playApi={{} as AdventurePlayApi}
        partyCharacters={[
          { characterSheetId: 'sheet-1', name: '린', characterClass: '클레릭', level: 1 },
          { characterSheetId: 'sheet-2', name: '린', characterClass: '파이터', level: 1 },
        ]}
        combatSnapshot={{
          encounterId: 'encounter-1', adventureId: 'adventure-1', status: 'ACTIVE', round: 1,
          currentParticipantId: 'sheet-2', version: 3, eventCursor: 2,
          resources: { movement: 30, actionAvailable: true, bonusActionAvailable: true, reactionAvailable: true },
          initiative: [
            { participantId: 'sheet-1', displayName: '린', controller: 'PLAYER', initiative: 12, publicCondition: '건강함' },
            { participantId: 'sheet-2', displayName: '린', controller: 'PLAYER', initiative: 20, publicCondition: '부상' },
          ],
        }}
      />,
    )

    const partyRows = [...document.querySelectorAll('.runtime-party-list li')]
    expect(partyRows).toHaveLength(2)
    expect(partyRows[0]).toHaveTextContent('INIT 12')
    expect(partyRows[0]).toHaveTextContent('건강함')
    expect(partyRows[0]).not.toHaveClass('runtime-party-current')
    expect(partyRows[1]).toHaveTextContent('INIT 20')
    expect(partyRows[1]).toHaveTextContent('부상')
    expect(partyRows[1]).toHaveClass('runtime-party-current')
  })

  it('접기 버튼으로 플레이 캐릭터 패널을 숨겼다가 다시 펼친다', async () => {
    const user = userEvent.setup()
    render(
      <SessionRuntime
        adventureId="adventure-1"
        adventureApi={{} as AdventureApi}
        playApi={{} as AdventurePlayApi}
        partyCharacters={[{ characterSheetId: 'sheet-1', name: '아리아', characterClass: '위저드', level: 1 }]}
      />,
    )

    expect(screen.getByText('아리아')).toBeVisible()
    await user.click(screen.getByRole('button', { name: '플레이 캐릭터 패널 접기' }))
    expect(screen.queryByText('아리아')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '플레이 캐릭터 패널 펼치기' })).toHaveAttribute('aria-expanded', 'false')

    await user.click(screen.getByRole('button', { name: '플레이 캐릭터 패널 펼치기' }))
    expect(screen.getByText('아리아')).toBeVisible()
  })

  it('게임 시작 후 핸드아웃 목록을 항상 표시하고 열람할 수 있다', async () => {
    const user = userEvent.setup()
    const getHandoutPreview = vi.fn().mockResolvedValue({ content: '문 안쪽에 숨겨진 단서', originalFilename: '던전 단서.txt' })
    render(
      <SessionRuntime
        adventureId="adventure-1"
        adventureApi={{} as AdventureApi}
        playApi={{} as AdventurePlayApi}
        handouts={[{ knowledgeDocumentId: 'document-1', originalFilename: '던전 단서.txt' }]}
        getHandoutPreview={getHandoutPreview}
      />,
    )

    expect(screen.getByRole('heading', { name: '핸드아웃' })).toBeVisible()
    const handoutButton = screen.getByRole('button', { name: '던전 단서.txt 열기' })
    expect(handoutButton).toBeVisible()
    await user.click(handoutButton)
    expect(await screen.findByText('문 안쪽에 숨겨진 단서')).toBeVisible()
    expect(getHandoutPreview).toHaveBeenCalledWith('document-1')
  })

  it('현재 차례의 공간 발동 뒤 최신 지도 버전으로 지속 시간을 진행한다', async () => {
    const combatTurnStartSpatial = vi.fn().mockResolvedValue({ mapId: 'map-1', mapVersion: 8, publicEvents: [] })
    const advanceSpatialDurations = vi.fn().mockResolvedValue({ mapId: 'map-1', mapVersion: 9, publicEvents: [] })
    const playApi = {
      getCombatMap: vi.fn().mockResolvedValue({ mapId: 'map-1', version: 7, status: 'authoritative-map' }),
      combatTurnStartSpatial,
      advanceSpatialDurations,
    } as unknown as AdventurePlayApi
    render(
      <SessionRuntime
        adventureId="adventure-1"
        adventureApi={{} as AdventureApi}
        playApi={playApi}
        combatSnapshot={{
          encounterId: 'encounter-1', adventureId: 'adventure-1', status: 'ACTIVE', round: 2,
          currentParticipantId: 'character-1', version: 4, eventCursor: 3,
          resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true },
          initiative: [],
        }}
      />,
    )

    await waitFor(() => expect(advanceSpatialDurations).toHaveBeenCalledTimes(1))
    expect(combatTurnStartSpatial).toHaveBeenCalledWith('adventure-1', expect.objectContaining({ mapId: 'map-1', expectedVersion: 7 }))
    expect(advanceSpatialDurations).toHaveBeenCalledWith('adventure-1', expect.objectContaining({ mapId: 'map-1', expectedVersion: 8 }))
    expect(combatTurnStartSpatial.mock.calls[0][1].commandId).not.toBe(advanceSpatialDurations.mock.calls[0][1].commandId)
  })

  it('같은 전투 차례로 화면을 다시 열어도 지도 요청 번호를 재사용한다', async () => {
    const combatTurnStartSpatial = vi.fn().mockResolvedValue({ mapId: 'map-1', mapVersion: 8, publicEvents: [] })
    const advanceSpatialDurations = vi.fn().mockResolvedValue({ mapId: 'map-1', mapVersion: 9, publicEvents: [] })
    const playApi = {
      getCombatMap: vi.fn().mockResolvedValue({ mapId: 'map-1', version: 7, status: 'authoritative-map' }),
      combatTurnStartSpatial,
      advanceSpatialDurations,
    } as unknown as AdventurePlayApi
    const props = {
      adventureId: 'adventure-1', adventureApi: {} as AdventureApi, playApi,
      combatSnapshot: {
        encounterId: 'encounter-1', adventureId: 'adventure-1', status: 'ACTIVE' as const, round: 2,
        currentParticipantId: 'character-1', version: 4, eventCursor: 3,
        resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true }, initiative: [],
      },
    }
    const first = render(<SessionRuntime {...props} />)
    await waitFor(() => expect(advanceSpatialDurations).toHaveBeenCalledTimes(1))
    const firstStartId = combatTurnStartSpatial.mock.calls[0][1].commandId
    const firstAdvanceId = advanceSpatialDurations.mock.calls[0][1].commandId

    first.unmount()
    render(<SessionRuntime {...props} />)
    await waitFor(() => expect(advanceSpatialDurations).toHaveBeenCalledTimes(2))

    expect(combatTurnStartSpatial.mock.calls[1][1].commandId).toBe(firstStartId)
    expect(advanceSpatialDurations.mock.calls[1][1].commandId).toBe(firstAdvanceId)
  })

  it('지도 버전 충돌 뒤 최신 버전으로 같은 요청을 한 번 재시도한다', async () => {
    const conflict = Object.assign(new Error('conflict'), { status: 409, code: 'SPATIAL_MAP_VERSION_CONFLICT' })
    const combatTurnStartSpatial = vi.fn()
      .mockRejectedValueOnce(conflict)
      .mockResolvedValueOnce({ mapId: 'map-1', mapVersion: 9, publicEvents: [] })
    const advanceSpatialDurations = vi.fn().mockResolvedValue({ mapId: 'map-1', mapVersion: 10, publicEvents: [] })
    const getCombatMap = vi.fn()
      .mockResolvedValueOnce({ mapId: 'map-1', version: 7, status: 'authoritative-map' })
      .mockResolvedValueOnce({ mapId: 'map-1', version: 8, status: 'authoritative-map' })
    render(<SessionRuntime
      adventureId="adventure-1"
      adventureApi={{} as AdventureApi}
      playApi={{ getCombatMap, combatTurnStartSpatial, advanceSpatialDurations } as unknown as AdventurePlayApi}
      combatSnapshot={{
        encounterId: 'encounter-1', adventureId: 'adventure-1', status: 'ACTIVE', round: 2,
        currentParticipantId: 'character-1', version: 4, eventCursor: 3,
        resources: { movement: 30, actionAvailable: true, bonusActionAvailable: false, reactionAvailable: true }, initiative: [],
      }}
    />)

    await waitFor(() => expect(advanceSpatialDurations).toHaveBeenCalledTimes(1))
    expect(combatTurnStartSpatial).toHaveBeenCalledTimes(2)
    expect(combatTurnStartSpatial.mock.calls[0][1].commandId).toBe(combatTurnStartSpatial.mock.calls[1][1].commandId)
    expect(combatTurnStartSpatial.mock.calls[1][1].expectedVersion).toBe(8)
  })
})
