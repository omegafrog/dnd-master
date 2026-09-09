import '@testing-library/jest-dom/vitest'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { CharacterSheetView } from '../character/CharacterSheetView'
import { RoleDiceRoller } from '../dice/RoleDiceRoller'
import { HttpAdventurePlayApi, type AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import { boundariesInStroke, CombatMapView } from './CombatMapView'

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
    async updateCombatMapLayout() {},
  }
}

async function confirmCrop(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: '전체 지도' }))
  await user.click(screen.getByRole('button', { name: '자르기 적용' }))
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

it('falls back to the reviewed preparation image when the play image is not ready', async () => {
  const api = fakeApi()
  api.getPublicMapImage = async () => null
  api.getCombatMapPreparationImage = async () => '/reviewed-preparation.png'
  api.getCombatMap = vi.fn().mockResolvedValue({
    adventureId: 'a1', status: 'map-view', mapId: null, version: 2,
    grid: { width: 2, height: 2 }, tokens: [],
  })
  api.getCombatMapPreparation = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 3,
    grid: { width: 2, height: 2 }, tokens: [], layers: [{ type: 'MAP_BOUNDARIES', value: '1,0,HORIZONTAL,WALL,false' }],
  })
  render(<CombatMapView adventureId="a1" api={api} />)
  const map = await screen.findByLabelText('tactical-map')
  expect(map).toHaveStyle({ backgroundImage: 'url(/reviewed-preparation.png)' })
})

it('renders only the reviewed crop in the player map', async () => {
  const api = fakeApi()
  api.getPublicMapImage = async () => '/public-map-image.png'
  api.getCombatMap = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0,
    grid: { width: 3, height: 2 }, tokens: [{ id: 'p1', type: 'PLAYER', x: 1, y: 0 }],
    current: [{ x: 1, y: 0 }], explored: [{ x: 1, y: 0 }],
    layers: [{ type: 'GRID_BOUNDS', value: '0,0,300,200,300,200' }, { type: 'MAP_CROP', value: '100,0,100,200' }],
  })
  render(<CombatMapView adventureId="a1" api={api} />)
  const map = await screen.findByLabelText('tactical-map')
  expect(map).toHaveStyle({ '--map-aspect': '100 / 200' })
  expect(map.querySelectorAll('button')).toHaveLength(2)
  expect(screen.getByRole('button', { name: 'PLAYER 1,0' })).toBeInTheDocument()
  expect(screen.queryByLabelText('지도 밖 영역')).not.toBeInTheDocument()
})

it('renders reviewed wall and door boundaries during an active turn', async () => {
  const api = fakeApi()
  api.getCombatMap = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 4,
    grid: { width: 2, height: 2 }, tokens: [{ id: 'p1', type: 'PLAYER', x: 0, y: 0 }],
    current: [{ x: 0, y: 0 }], explored: [{ x: 0, y: 0 }],
    layers: [{ type: 'MAP_BOUNDARIES', value: '1,0,HORIZONTAL,WALL,false;0,1,VERTICAL,DOOR,false' }],
  })
  render(<CombatMapView adventureId="a1" api={api} />)
  const map = await screen.findByLabelText('tactical-map')
  expect(map.querySelector('[data-boundary="HORIZONTAL:1:0"]')).toHaveClass('map-boundary-wall')
  expect(map.querySelector('[data-boundary="VERTICAL:0:1"]')).toHaveClass('map-boundary-door')
})

it('renders the saved grid alignment in the map below the editor', async () => {
  const api = fakeApi()
  api.getPublicMapImage = async () => '/public-map-image.png'
  api.getMapGridAlignment = async () => ({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 200, originY: 10, cellSize: 30 })
  api.getCombatMap = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0,
    grid: { width: 20, height: 20 }, tokens: [], layers: [{ type: 'GRID_BOUNDS', value: '311,105,800,800,1403,992' }],
  })
  render(<CombatMapView adventureId="a1" api={api} />)

  const map = await screen.findByLabelText('tactical-map')
  await waitFor(() => expect(map.getAttribute('style')).toContain('--map-aspect: 600 / 600'))
  expect(map.getAttribute('style')).toContain('--map-background-position: 24.906600249066003% 2.5510204081632653%')
})

it('keeps the map usable when the public image is temporarily unavailable', async () => {
  const api = fakeApi()
  api.getPublicMapImage = async () => { throw new Error('not ready') }
  render(<CombatMapView adventureId="a1" api={api} />)

  expect(await screen.findByLabelText('tactical-map')).toBeInTheDocument()
  expect(screen.getByText('현재 맵 상태: authoritative-map')).toBeInTheDocument()
})

it('does not render a tactical map while the story has not entered combat', async () => {
  const api = fakeApi()
  api.getCombatMap = vi.fn().mockResolvedValue({ adventureId: 'a1', status: 'map-view', mapId: null, version: 2, grid: { width: 20, height: 20 }, tokens: [] })

  render(<CombatMapView adventureId="a1" api={api} />)

  await waitFor(() => expect(api.getCombatMap).toHaveBeenCalled())
  expect(screen.queryByLabelText('tactical-map')).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '플레이어 전투 맵' })).not.toBeInTheDocument()
})

it('shows the reviewed map during story conversation before combat starts', async () => {
  const api = fakeApi()
  api.getCombatMap = vi.fn().mockResolvedValue({ adventureId: 'a1', status: 'map-view', mapId: null, version: 2, grid: { width: 2, height: 2 }, tokens: [] })
  api.getCombatMapPreparation = vi.fn().mockResolvedValue({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 3,
    grid: { width: 2, height: 2 }, tokens: [], current: [], explored: [],
    layers: [{ type: 'MAP_BOUNDARIES', value: '1,0,HORIZONTAL,WALL,false;0,1,VERTICAL,DOOR,false' }],
  })

  render(<CombatMapView adventureId="a1" api={api} />)

  expect(await screen.findByLabelText('tactical-map')).toBeInTheDocument()
  expect(screen.getByText('현재 맵 상태: story-map')).toBeInTheDocument()
  expect(api.getCombatMapPreparation).toHaveBeenCalledWith('a1')
  expect(screen.getAllByLabelText(/격자/)).toHaveLength(4)
  expect(screen.getByLabelText('tactical-map').querySelector('[data-boundary="HORIZONTAL:1:0"]')).toHaveClass('map-boundary-wall')
  expect(screen.getByLabelText('tactical-map').querySelector('[data-boundary="VERTICAL:0:1"]')).toHaveClass('map-boundary-door')
})

it('shows AI wall and door drafts across the full map during preparation', async () => {
  const api = fakeApi()
  api.getCombatMapPreparation = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0,
    grid: { width: 2, height: 2 }, tokens: [{ id: 'p1', type: 'PLAYER', x: 0, y: 0 }],
    obstacles: [{ x: 1, y: 0 }], doors: [{ x: 0, y: 1, open: false }], current: [{ x: 0, y: 0 }], explored: [{ x: 0, y: 0 }],
  })
  api.getCombatMapPreparationImage = async () => '/preparation-map.png'
  api.getMapGridAlignment = async () => ({ mapId: 'm1', version: 0, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.applyMapGridAlignment = async (_adventureId, request) => ({ ...request, version: 2 })
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  expect(screen.queryByRole('button', { name: '격자 맞추기' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '벽·문 편집' })).not.toBeInTheDocument()
  await confirmCrop(user)
  const align = await screen.findByRole('button', { name: '격자 맞추기' })
  await user.click(align)
  await user.click(screen.getByRole('button', { name: '적용' }))
  await user.click(screen.getByRole('button', { name: '벽·문 편집' }))
  const editor = screen.getByLabelText('맵 초안 검수')
  const mapCanvas = within(editor).getByLabelText('tactical-map')
  const wall = mapCanvas.querySelector('[data-boundary="HORIZONTAL:1:0"]')
  expect(wall).toHaveClass('map-boundary-wall')
  expect(wall).toHaveStyle({ left: '50%', top: '0%', height: '5px' })
  expect(mapCanvas.querySelector('[data-boundary="HORIZONTAL:0:1"]')).toHaveClass('map-boundary-door')
  await user.click(within(editor).getByRole('button', { name: '문 그리기' }))
  expect(boundariesInStroke({ orientation: 'HORIZONTAL', fixed: 0, from: 0, to: 1 })).toEqual([
    { x: 0, y: 0, orientation: 'HORIZONTAL' }, { x: 1, y: 0, orientation: 'HORIZONTAL' },
  ])
  expect(within(editor).getByRole('button', { name: '빈 격자 1,0' })).toBeDisabled()
  expect(within(editor).getByText('칸은 이동하거나 선택되지 않습니다.')).toBeInTheDocument()
  await user.click(within(editor).getByRole('button', { name: '편집 취소' }))
  expect(screen.getByRole('button', { name: '벽·문 편집' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '위치 선택' })).not.toBeInTheDocument()
})

it('keeps the saved grid scale while reviewing a separately cropped image', async () => {
  const api = fakeApi()
  api.getCombatMapPreparation = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 2,
    grid: { width: 2, height: 2 }, tokens: [],
    layers: [
      { type: 'GRID_BOUNDS', value: '0,0,60,60,120,90' },
      { type: 'MAP_CROP', value: '0,0,100,80' },
      { type: 'MAP_LAYOUT_CONFIRMED', value: 'USER|ALIGNMENT_VERSION=1' },
    ],
  })
  api.getCombatMapPreparationImage = async () => '/preparation-map.png'
  api.getMapGridAlignment = async () => ({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.applyMapGridAlignment = vi.fn().mockResolvedValue({ mapId: 'm1', version: 2, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  await confirmCrop(user)
  await user.click(await screen.findByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  await user.click(await screen.findByRole('button', { name: '벽·문 편집' }))
  const map = within(screen.getByLabelText('맵 초안 검수')).getByLabelText('tactical-map')
  expect(map.getAttribute('style')).toContain('--map-background-size: 200% 150%')
  expect(map.getAttribute('style')).not.toContain('--map-background-size: 120% 112.5%')
})

it('updates boundary feedback while dragging and restores the stroke on cancel', async () => {
  const api = fakeApi()
  api.getCombatMapPreparation = async () => ({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0, grid: { width: 2, height: 2 }, tokens: [] })
  api.getCombatMapPreparationImage = async () => '/preparation-map.png'
  api.getMapGridAlignment = async () => ({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.applyMapGridAlignment = async (_adventureId, request) => ({ ...request, version: 1 })
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  await confirmCrop(user)
  await user.click(await screen.findByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  await user.click(await screen.findByRole('button', { name: '벽·문 편집' }))
  const editor = screen.getByLabelText('맵 초안 검수')
  const canvas = within(editor).getByLabelText('tactical-map')
  Object.defineProperty(canvas, 'getBoundingClientRect', { value: () => ({ left: 0, top: 0, width: 200, height: 200 }) })

  fireEvent.pointerDown(canvas, { clientX: 25, clientY: 100, pointerId: 1 })
  expect(await screen.findByText(/벽 그리는 중/)).toBeInTheDocument()
  await waitFor(() => expect(canvas.querySelector('.map-boundary-preview')).toHaveClass('map-boundary-wall'))
  fireEvent.pointerMove(canvas, { clientX: 175, clientY: 100, pointerId: 1 })
  await waitFor(() => expect(canvas.querySelector('.map-boundary-preview')).toHaveClass('map-boundary-wall'))
  fireEvent.pointerCancel(canvas, { pointerId: 1 })
  expect(canvas.querySelector('[data-boundary="HORIZONTAL:0:1"]')).not.toBeInTheDocument()
  expect(canvas.querySelector('[data-boundary="HORIZONTAL:1:1"]')).not.toBeInTheDocument()
})

it('runs AI wall detection only after grid confirmation and keeps crop editing collapsed', async () => {
  const api = fakeApi()
  const before = { adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0, grid: { width: 2, height: 2 }, tokens: [] }
  api.getCombatMapPreparation = vi.fn().mockResolvedValue(before)
  api.getCombatMapPreparationImage = vi.fn().mockResolvedValue('/preparation-map.png')
  api.getMapGridAlignment = vi.fn().mockResolvedValue({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.applyMapGridAlignment = vi.fn().mockResolvedValue({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.detectMapBoundaries = vi.fn().mockResolvedValue({
    mapVersion: 0, obstacles: [], doors: [], boundaries: [{ x: 0, y: 1, orientation: 'HORIZONTAL', kind: 'WALL', open: false }], crop: '',
  })
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  await confirmCrop(user)
  expect(screen.queryByRole('button', { name: 'AI 벽·문 감지' })).not.toBeInTheDocument()
  await user.click(await screen.findByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  const detect = await screen.findByRole('button', { name: 'AI 벽·문 감지' })
  await user.click(detect)

  expect(api.detectMapBoundaries).toHaveBeenCalledWith('a1')
  expect(screen.queryByLabelText('지도 자르기')).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '3. AI 초안 생성 및 검수' }).parentElement).toHaveTextContent('현재 자른 영역과 격자를 기준으로 AI 초안을 생성합니다.')
  await user.click(screen.getByRole('button', { name: '자르기 다시 수정' }))
  expect(await screen.findByLabelText('지도 자르기')).toBeInTheDocument()
})

it('does not show image-analysis candidate controls', async () => {
  const api = fakeApi()
  const map = { adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0, grid: { width: 2, height: 2 }, tokens: [] }
  api.getCombatMapPreparation = vi.fn().mockResolvedValue(map)
  api.getCombatMapPreparationImage = vi.fn().mockResolvedValue('/preparation-map.png')
  api.getMapGridAlignment = vi.fn().mockResolvedValue({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.applyMapGridAlignment = vi.fn().mockResolvedValue({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.detectMapBoundaries = vi.fn().mockResolvedValue({
    mapVersion: 0, obstacles: [], doors: [], boundaries: [], crop: '', alignmentVersion: 1, imageRevision: 'r1',
    candidates: [
      { x: 0, y: 1, orientation: 'HORIZONTAL', kind: 'WALL', confidence: .84, evidence: ['continuous-edge'] },
      { x: 1, y: 1, orientation: 'VERTICAL', kind: 'DOOR', confidence: .44, evidence: ['door-frame'] },
    ],
  })
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  await confirmCrop(user)
  await user.click(await screen.findByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  await user.click(await screen.findByRole('button', { name: 'AI 벽·문 감지' }))

  expect(screen.queryByLabelText('이미지 분석 후보 설명')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /후보 승인|후보 제외/ })).not.toBeInTheDocument()
})

it('does not treat a draft saved for an older grid as ready after reload', async () => {
  const api = fakeApi()
  api.getCombatMapPreparation = async () => ({
    adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 4,
    grid: { width: 2, height: 2 }, tokens: [],
    layers: [{ type: 'MAP_LAYOUT_CONFIRMED', value: 'USER|ALIGNMENT_VERSION=1' }],
  })
  api.getMapGridAlignment = async () => ({ mapId: 'm1', version: 2, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  const ready = await screen.findByRole('button', { name: '맵 준비 완료, 모험 시작' })
  expect(ready).toBeDisabled()
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
  expect(screen.getByLabelText('지도 공개 범례')).toHaveTextContent('아직 확인하지 않은 영역')
  expect(screen.getByLabelText('지도 공개 범례')).toHaveTextContent('전에 확인했지만 지금은 시야 밖인 영역')
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
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 1, grid: { width: 2, height: 2 }, tokens: [] })
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm2', version: 2, grid: { width: 2, height: 2 }, tokens: [] })
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm2', version: 3, grid: { width: 2, height: 2 }, tokens: [] })
  api.getCombatMapPreparationImage = vi.fn().mockResolvedValue('/map.png')
  api.getMapGridAlignment = vi.fn()
    .mockResolvedValueOnce({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
    .mockResolvedValueOnce({ mapId: 'm2', version: 2, imageRevision: 'r2', originX: 0, originY: 0, cellSize: 30 })
  api.updateCombatMapLayout = vi.fn().mockResolvedValue(undefined)
  api.applyMapGridAlignment = vi.fn()
    .mockResolvedValueOnce({ mapId: 'm1', version: 2, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
    .mockResolvedValueOnce({ mapId: 'm2', version: 3, imageRevision: 'r2', originX: 0, originY: 0, cellSize: 30 })
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  await confirmCrop(user)
  await user.click(await screen.findByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  await user.click(screen.getByRole('button', { name: '벽·문 편집' }))
  await user.click(screen.getByRole('button', { name: '맵 초안 저장' }))
  await user.click(screen.getByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))

  expect(api.applyMapGridAlignment).toHaveBeenLastCalledWith('a1', expect.objectContaining({ mapId: 'm2', expectedVersion: 2, imageRevision: 'r2' }))
})

it('retries a map draft against the latest version after an AI update wins the race', async () => {
  const api = fakeApi()
  api.getCombatMapPreparation = vi.fn()
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 1, grid: { width: 2, height: 2 }, tokens: [] })
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 1, grid: { width: 2, height: 2 }, tokens: [] })
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 2, grid: { width: 2, height: 2 }, tokens: [] })
    .mockResolvedValueOnce({ adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 3, grid: { width: 2, height: 2 }, tokens: [] })
  api.getCombatMapPreparationImage = vi.fn().mockResolvedValue('/map.png')
  api.getMapGridAlignment = vi.fn()
    .mockResolvedValueOnce({ mapId: 'm1', version: 1, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
    .mockResolvedValueOnce({ mapId: 'm1', version: 2, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  api.applyMapGridAlignment = vi.fn().mockResolvedValue({ mapId: 'm1', version: 2, imageRevision: 'r1', originX: 0, originY: 0, cellSize: 30 })
  const update = vi.fn()
    .mockResolvedValueOnce(undefined)
    .mockRejectedValueOnce(Object.assign(new Error('conflict'), { status: 409 }))
    .mockResolvedValueOnce(undefined)
  api.updateCombatMapLayout = update
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} preparationMode />)

  await confirmCrop(user)
  await user.click(await screen.findByRole('button', { name: '격자 맞추기' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  await user.click(screen.getByRole('button', { name: '벽·문 편집' }))
  await user.click(screen.getByRole('button', { name: '맵 초안 저장' }))

  await waitFor(() => expect(update).toHaveBeenCalledTimes(3))
  expect(update.mock.calls[2][1]).toEqual(expect.objectContaining({ expectedVersion: 3 }))
  expect(screen.getByText('최신 초안에 변경 내용을 다시 적용했습니다.')).toBeInTheDocument()
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
