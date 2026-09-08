import '@testing-library/jest-dom/vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { CharacterSheetView } from '../character/CharacterSheetView'
import { RoleDiceRoller } from '../dice/RoleDiceRoller'
import { HttpAdventurePlayApi, type AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import { CombatMapView } from './CombatMapView'

function fakeApi(): AdventurePlayApi {
  const submitMapAction = vi.fn(async () => ({ turnId: 't1', version: 1 }))
  return {
    async getCharacter() {
      return {
        characterSheetId: 'cs-1', name: "Lae'zel", edition: '2024',
        armorClass: 17, strength: 16, dexterity: 14, constitution: 15,
        intelligence: 10, wisdom: 12, charisma: 8,
      }
    },
    async getCombatMap() { return { adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0, sessionVersion: 7, grid: { width: 3, height: 2 }, tokens: [{ id: 'p1', type: 'PLAYER', x: 1, y: 1 }], current: [{ x: 1, y: 1 }, { x: 2, y: 1 }], explored: [{ x: 1, y: 1 }, { x: 2, y: 1 }] } },
    submitMapAction,
    async rollDice() { return { rollId: 'r1', total: 19, judgment: 'hit', resolutionStatus: 'RESOLVED', outcomeApplied: true } },
    async listSaved() { return [] },
    async save() { return { adventureId: 'a1', newVersion: 1 } },
    async resume() {},
    async deleteAdventure() {},
    async getSessionKnowledgeSet() { return { adventureId: 'a1', sessionId: 's1', knowledgeDocumentIds: [] } },
    async saveSessionKnowledgeSet() { return { adventureId: 'a1', sessionId: 's1', knowledgeDocumentIds: [] } },
  }
}

it('shows character sheet, rolls dice, and shows combat map', async () => {
  const api = fakeApi()
  const user = userEvent.setup()
  render(<>
    <CharacterSheetView sheetId="cs-1" api={api} />
    <RoleDiceRoller adventureId="a1" api={api} />
    <CombatMapView adventureId="a1" api={api} />
  </>)
  expect(await screen.findByRole('heading', { name: /Lae'zel/ })).toBeInTheDocument()
  expect(screen.getByText('17')).toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('담당 역할'), 'ENEMY')
  await user.click(screen.getByRole('button', { name: '굴리기' }))
  expect(await screen.findByText(/결과: 19/)).toBeInTheDocument()
  expect(await screen.findByText(/hit · 상태: RESOLVED/)).toBeInTheDocument()
  expect(await screen.getByRole('heading', { name: '플레이어 전투 맵' })).toBeInTheDocument()
  expect(await screen.findByText('현재 맵 상태: authoritative-map')).toBeInTheDocument()
})

it('uses the authenticated public image rather than a map image layer', async () => {
  const api = fakeApi()
  api.getPublicMapImage = async () => '/public-map-image.png'
  api.getCombatMap = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0,
    grid: { width: 20, height: 20 }, tokens: [{ id: 'p1', type: 'PLAYER', x: 0, y: 0 }],
    layers: [{ type: 'MAP_IMAGE', value: '/unsafe-original-map.png' }, { type: 'GRID_BOUNDS', value: '311,105,800,800,1403,992' }],
  })
  render(<CombatMapView adventureId="a1" api={api} />)
  const map = await screen.findByLabelText('tactical-map')
  expect(map).toHaveStyle({ backgroundImage: 'url(/public-map-image.png)' })
  expect(map.getAttribute('style')).not.toContain('unsafe-original-map')
  expect(map.getAttribute('style')).toContain('--map-aspect: 800 / 800')
  expect(map.getAttribute('style')).toContain('--map-background-size: 175.375% 124%')
  expect(map.querySelectorAll('button')).toHaveLength(400)
})

it('keeps the map usable when the public image is temporarily unavailable', async () => {
  const api = fakeApi()
  api.getPublicMapImage = async () => { throw new Error('not ready') }
  render(<CombatMapView adventureId="a1" api={api} />)

  expect(await screen.findByLabelText('tactical-map')).toBeInTheDocument()
  expect(screen.getByText('현재 맵 상태: authoritative-map')).toBeInTheDocument()
})

it('shows AI wall and door drafts across the full map during preparation', async () => {
  const api = fakeApi()
  api.getCombatMapPreparation = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0,
    grid: { width: 2, height: 2 }, tokens: [{ id: 'p1', type: 'PLAYER', x: 0, y: 0 }],
    obstacles: [{ x: 1, y: 0 }], doors: [{ x: 0, y: 1, open: false }], current: [{ x: 0, y: 0 }], explored: [{ x: 0, y: 0 }],
  })
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  expect(await screen.findByRole('button', { name: '벽 1,0' })).toHaveClass('map-draft-wall')
  expect(screen.getByRole('button', { name: '닫힌 문 0,1' })).toHaveClass('map-draft-door')
  expect(screen.queryByRole('button', { name: '위치 선택' })).not.toBeInTheDocument()
})

it('releases the replaced public image blob URL', async () => {
  const api = fakeApi()
  api.getPublicMapImage = vi.fn().mockResolvedValueOnce('blob:first-map').mockResolvedValueOnce('blob:second-map')
  const revoke = vi.fn()
  vi.stubGlobal('URL', { revokeObjectURL: revoke })
  try {
    const { rerender, unmount } = render(<CombatMapView adventureId="a1" api={api} refreshToken={0} />)
    await screen.findByLabelText('tactical-map')
    rerender(<CombatMapView adventureId="a1" api={api} refreshToken={1} />)
    await waitFor(() => expect(api.getPublicMapImage).toHaveBeenCalledTimes(2))
    expect(revoke).toHaveBeenCalledWith('blob:first-map')
    unmount()
  } finally {
    vi.unstubAllGlobals()
  }
})

it('gives each visible token type a stable styling hook', async () => {
  const api = fakeApi()
  api.getCombatMap = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0,
    grid: { width: 2, height: 1 }, tokens: [
      { id: 'p1', type: 'PLAYER', x: 0, y: 0 }, { id: 'e1', type: 'ENEMY', x: 1, y: 0 },
    ], current: [{ x: 0, y: 0 }, { x: 1, y: 0 }], explored: [{ x: 0, y: 0 }, { x: 1, y: 0 }],
  })
  render(<CombatMapView adventureId="a1" api={api} />)
  const map = await screen.findByLabelText('tactical-map')
  expect(map.querySelector('[data-token-type="PLAYER"]')).toBeInTheDocument()
  expect(map.querySelector('[data-token-type="ENEMY"]')).toBeInTheDocument()
})

it('fails closed when visibility metadata is missing and only shows visible token types in the legend', async () => {
  const api = fakeApi()
  api.getCombatMap = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0,
    grid: { width: 2, height: 1 }, tokens: [
      { id: 'p1', type: 'PLAYER', x: 0, y: 0 }, { id: 'e1', type: 'ENEMY', x: 1, y: 0 },
    ],
  })
  render(<CombatMapView adventureId="a1" api={api} />)
  const map = await screen.findByLabelText('tactical-map')
  expect(map.querySelector('[data-visibility="hidden"]')).toBeInTheDocument()
  expect(screen.getByLabelText('맵 범례')).toHaveTextContent('플레이어 캐릭터')
  expect(screen.getByLabelText('맵 범례')).not.toHaveTextContent('적대 몬스터')
})

it('keeps a drag candidate local until confirmed, and cancel sends nothing', async () => {
  const api = fakeApi()
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} />)

  await user.click(await screen.findByRole('button', { name: /PLAYER.*1,1/ }))
  await user.click(screen.getByRole('button', { name: '격자 2,1' }))

  expect(screen.getByRole('dialog', { name: '맵 행동 확인' })).toHaveTextContent('이동: (1,1) → (2,1)')
  await user.click(screen.getByRole('button', { name: '취소' }))
  expect(screen.queryByRole('dialog', { name: '맵 행동 확인' })).not.toBeInTheDocument()
  expect(api.submitMapAction).not.toHaveBeenCalled()
})

it('submits exactly one typed map action after confirmation', async () => {
  const api = fakeApi()
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} />)
  await user.click(await screen.findByRole('button', { name: /PLAYER.*1,1/ }))
  await user.click(screen.getByRole('button', { name: '격자 2,1' }))
  await user.click(screen.getByRole('button', { name: '확인' }))
  expect(api.submitMapAction).toHaveBeenCalledTimes(1)
  expect(api.submitMapAction).toHaveBeenCalledWith('a1', expect.objectContaining({ action: 'MOVE', path: [{ x: 1, y: 1 }, { x: 2, y: 1 }] }), undefined, 7)
})

it('refetches the map when the parent refresh token changes', async () => {
  const api = fakeApi()
  const getCombatMap = vi.spyOn(api, 'getCombatMap')
  const { rerender } = render(<CombatMapView adventureId="a1" api={api} refreshToken={0} />)
  await screen.findByRole('heading', { name: '플레이어 전투 맵' })
  expect(getCombatMap).toHaveBeenCalledTimes(1)
  rerender(<CombatMapView adventureId="a1" api={api} refreshToken={1} />)
  await waitFor(() => expect(getCombatMap).toHaveBeenCalledTimes(2))
})

it('does not let a slower stale refresh overwrite the latest map', async () => {
  let resolveFirst!: (map: Awaited<ReturnType<AdventurePlayApi['getCombatMap']>>) => void
  let resolveSecond!: (map: Awaited<ReturnType<AdventurePlayApi['getCombatMap']>>) => void
  const first = new Promise<Awaited<ReturnType<AdventurePlayApi['getCombatMap']>>>(resolve => { resolveFirst = resolve })
  const second = new Promise<Awaited<ReturnType<AdventurePlayApi['getCombatMap']>>>(resolve => { resolveSecond = resolve })
  const api = fakeApi()
  api.getCombatMap = vi.fn().mockReturnValueOnce(first).mockReturnValueOnce(second)
  const { rerender } = render(<CombatMapView adventureId="a1" api={api} refreshToken={0} />)
  rerender(<CombatMapView adventureId="a1" api={api} refreshToken={1} />)
  await act(async () => { resolveSecond({ adventureId: 'a1', status: 'latest', mapId: 'm1', version: 2, grid: { width: 1, height: 1 }, tokens: [] }) })
  await screen.findByText('현재 맵 상태: latest')
  await act(async () => { resolveFirst({ adventureId: 'a1', status: 'stale', mapId: 'm1', version: 1, grid: { width: 1, height: 1 }, tokens: [] }) })
  expect(screen.getByText('현재 맵 상태: latest')).toBeInTheDocument()
})

it('refreshes the grid alignment after saving the map draft', async () => {
  const api = fakeApi()
  api.getCombatMapPreparation = vi.fn()
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 1, grid: { width: 2, height: 2 }, tokens: [] })
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm2', version: 2, grid: { width: 2, height: 2 }, tokens: [] })
  api.getCombatMapPreparationImage = vi.fn().mockResolvedValue('/map.png')
  api.getMapGridAlignment = vi.fn()
    .mockResolvedValueOnce({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
    .mockResolvedValueOnce({ mapId: 'm2', version: 2, imageRevision: 'r2', originX: 0, originY: 0, cellSize: 30 })
  api.updateCombatMapLayout = vi.fn().mockResolvedValue(undefined)
  api.applyMapGridAlignment = vi.fn().mockResolvedValue({ mapId: 'm2', version: 3, imageRevision: 'r2', originX: 0, originY: 0, cellSize: 30 })
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  await user.click(await screen.findByRole('button', { name: '맵 초안 저장' }))
  await user.click(await screen.findByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))

  expect(api.applyMapGridAlignment).toHaveBeenCalledWith('a1', expect.objectContaining({ mapId: 'm2', expectedVersion: 2, imageRevision: 'r2' }))
})

it('downloads the public image through the authenticated no-store endpoint', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ imageViewId: 'public:image:reference' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    .mockResolvedValueOnce(new Response(new Blob(['safe-png'], { type: 'image/png' }), { status: 200, headers: { 'Content-Type': 'image/png' } }))
  vi.stubGlobal('fetch', fetchMock)
  const createObjectUrl = vi.fn(() => 'blob:public-map')
  vi.stubGlobal('URL', { ...URL, createObjectURL: createObjectUrl })
  try {
    const image = await new HttpAdventurePlayApi(() => 'player-token').getPublicMapImage('a1')

    expect(image).toBe('blob:public-map')
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/adventures/a1/combat-map/alignment', { headers: { Authorization: 'Bearer player-token' } })
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/adventures/a1/combat-map/alignment/image/public%3Aimage%3Areference', {
      headers: { Authorization: 'Bearer player-token' }, cache: 'no-store',
    })
  } finally {
    vi.unstubAllGlobals()
  }
})

it('saves only an alignment draft through the dedicated endpoint', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ mapId: 'm1', version: 2, imageRevision: 'r1', originX: 12.25, originY: 8.5, cellSize: 31.75 }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  vi.stubGlobal('fetch', fetchMock)
  try {
    await new HttpAdventurePlayApi(() => 'player-token').applyMapGridAlignment('a1', { mapId: 'm1', commandId: 'c1', expectedVersion: 1, imageRevision: 'r1', originX: 12.25, originY: 8.5, cellSize: 31.75 })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/adventures/a1/combat-map/alignment', expect.objectContaining({ method: 'PUT', body: JSON.stringify({ mapId: 'm1', commandId: 'c1', expectedVersion: 1, imageRevision: 'r1', originX: 12.25, originY: 8.5, cellSize: 31.75 }) }))
  } finally { vi.unstubAllGlobals() }
})
