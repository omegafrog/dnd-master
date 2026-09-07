import { useEffect, useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState } from '../saved-adventures/AdventurePlayApi'
import { actionCandidate, moveCandidate, type MapInteractionCandidate } from './MapInteractionCandidate'

export function CombatMapView({ adventureId, api, refreshToken = 0, compact = false }: { adventureId: string; api: AdventurePlayApi; refreshToken?: number; compact?: boolean }) {
  const [map, setMap] = useState<CombatMapState | null>(null)
  const [selectedToken, setSelectedToken] = useState<string | null>(null)
  const [candidate, setCandidate] = useState<MapInteractionCandidate | null>(null)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [locationMode, setLocationMode] = useState(false)
  const [gridEditor, setGridEditor] = useState(false)
  const [gridMessage, setGridMessage] = useState('')
  const [gridDraft, setGridDraft] = useState({ width: 20, height: 20, cellSize: 30, originX: 0, originY: 0, imageWidth: 1, imageHeight: 1, playerX: 0, playerY: 0 })
  const [mapImageSize, setMapImageSize] = useState({ width: 1, height: 1 })
  const gridDrag = useRef<{ mode: 'move' | 'resize'; startX: number; startY: number; rect: DOMRect; draft: typeof gridDraft } | null>(null)

  useEffect(() => {
    let active = true
    void api.getCombatMap(adventureId).then(nextMap => {
      if (active) {
        setMap(nextMap)
        const bounds = nextMap.layers?.find(layer => layer.type === 'GRID_BOUNDS')?.value?.split(',').map(Number)
        const player = nextMap.tokens?.find(token => token.type === 'PLAYER')
        const nextGrid = nextMap.grid ?? { width: 20, height: 20 }
        if (bounds?.length === 6 && bounds.every(Number.isFinite)) {
          setGridDraft({ width: nextGrid.width, height: nextGrid.height, cellSize: Math.max(1, Math.round(bounds[2] / nextGrid.width)), originX: bounds[0], originY: bounds[1], imageWidth: bounds[4], imageHeight: bounds[5], playerX: player?.x ?? 0, playerY: player?.y ?? 0 })
        }
      }
    }).catch(() => {
      if (active) setMap(null)
    })
    return () => { active = false }
  }, [adventureId, api, refreshToken])

  function chooseCell(cell: { x: number; y: number }) {
    if (!map || !selectedToken) return
    const token = map.tokens?.find(item => item.id === selectedToken)
    if (!token || (token.x === cell.x && token.y === cell.y)) {
      if (locationMode && selectedToken && map.mapId) {
        setCandidate(actionCandidate(map.mapId, map.version ?? 0, selectedToken, 'LOCATION', cell))
      }
      return
    }
    setCandidate(moveCandidate(map.mapId ?? '', map.version ?? 0, token.id, { x: token.x, y: token.y }, cell))
  }

  async function confirm() {
    if (!candidate) return
    if (submitting) return
    setSubmitting(true)
    try {
      if (!api.submitMapAction) throw new Error('맵 행동 API를 사용할 수 없습니다.')
      await api.submitMapAction(adventureId, {
        mapId: candidate.mapId, mapVersion: candidate.mapVersion, tokenId: candidate.tokenId,
        action: candidate.action, path: candidate.from && candidate.to ? gridPath(candidate.from, candidate.to) : undefined,
        targetId: candidate.targetId, location: candidate.location ?? candidate.to,
      }, undefined, map?.sessionVersion ?? map?.version ?? 0)
      const refreshed = await api.getCombatMap(adventureId)
      setMap(refreshed); setCandidate(null); setSelectedToken(null); setMessage('맵 행동을 GM 턴으로 전송했습니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '맵 행동을 처리하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const grid = map?.grid ?? { width: 0, height: 0 }
  const mapImage = map?.layers?.find(layer => layer.type === 'MAP_IMAGE')?.value
  const gridBounds = map?.layers?.find(layer => layer.type === 'GRID_BOUNDS')?.value?.split(',').map(Number)
  const hasGridBounds = gridBounds && gridBounds.length >= 6 && gridBounds.every(Number.isFinite)
    && gridBounds[0] >= 0 && gridBounds[1] >= 0 && gridBounds[2] > 0 && gridBounds[3] > 0
    && gridBounds[4] >= gridBounds[0] + gridBounds[2] && gridBounds[5] >= gridBounds[1] + gridBounds[3]
  const imageWidth = hasGridBounds ? gridBounds[4] : 0
  const imageHeight = hasGridBounds ? gridBounds[5] : 0
  const boundsWidth = hasGridBounds ? gridBounds[2] : 0
  const boundsHeight = hasGridBounds ? gridBounds[3] : 0
  const backgroundPositionX = hasGridBounds && imageWidth > boundsWidth
    ? `${(gridBounds[0] / (imageWidth - boundsWidth)) * 100}%` : 'center'
  const backgroundPositionY = hasGridBounds && imageHeight > boundsHeight
    ? `${(gridBounds[1] / (imageHeight - boundsHeight)) * 100}%` : 'center'
  const gridSource = map?.layers?.find(layer => layer.type === 'GRID_SOURCE')?.value
  const showGridEditor = !!mapImage && gridSource !== 'MANUAL'
  const canEditGridGeometry = gridSource !== 'MANUAL'
  useEffect(() => {
    if (!mapImage) {
      setMapImageSize({ width: 1, height: 1 })
      return
    }
    const image = new Image()
    image.onload = () => {
      const width = image.naturalWidth || image.width
      const height = image.naturalHeight || image.height
      if (width < 1 || height < 1) return
      setMapImageSize({ width, height })
      setGridDraft(current => {
        if (current.imageWidth > 1 && current.imageHeight > 1) return current
        const cellSize = Math.max(1, Math.floor(Math.min(width / Math.max(1, current.width), height / Math.max(1, current.height))))
        return {
          ...current,
          cellSize,
          originX: Math.max(0, Math.floor((width - current.width * cellSize) / 2)),
          originY: Math.max(0, Math.floor((height - current.height * cellSize) / 2)),
          imageWidth: width,
          imageHeight: height,
        }
      })
    }
    image.src = mapImage
  }, [mapImage])
  const previewGrid = gridEditor && showGridEditor
    ? gridDraft
    : { width: grid.width, height: grid.height, cellSize: hasGridBounds ? Math.round(boundsWidth / Math.max(grid.width, 1)) : 30, originX: hasGridBounds ? gridBounds[0] : 0, originY: hasGridBounds ? gridBounds[1] : 0, imageWidth: imageWidth || mapImageSize.width, imageHeight: imageHeight || mapImageSize.height }
  const mapStyle = {
    '--grid-columns': previewGrid.width,
    '--grid-rows': previewGrid.height,
    '--map-aspect': `${Math.max(previewGrid.width * previewGrid.cellSize, 1)} / ${Math.max(previewGrid.height * previewGrid.cellSize, 1)}`,
    ...(mapImage ? { backgroundImage: `url(${mapImage})` } : {}),
    ...(hasGridBounds || gridEditor ? {
      '--map-background-size': `${(gridDraft.imageWidth / Math.max(gridDraft.width * gridDraft.cellSize, 1)) * 100}% ${(gridDraft.imageHeight / Math.max(gridDraft.height * gridDraft.cellSize, 1)) * 100}%`,
      '--map-background-position': `${gridEditor ? `${(gridDraft.originX / Math.max(gridDraft.imageWidth - gridDraft.width * gridDraft.cellSize, 1)) * 100}%` : backgroundPositionX} ${gridEditor ? `${(gridDraft.originY / Math.max(gridDraft.imageHeight - gridDraft.height * gridDraft.cellSize, 1)) * 100}%` : backgroundPositionY}`,
    } : {}),
  } as CSSProperties
  const hasVisibilityMetadata = Array.isArray(map?.current) && Array.isArray(map?.explored)
  // A combat map is stage-scoped.  The backend returns an empty projection for
  // event/town stages; keep the entire tactical panel out of the player UI in
  // that state instead of showing a permanent "no map" panel.
  if (!map || !map.mapId) return null
  async function saveGrid() {
    if (!api.calibrateCombatMap || !map?.mapId) {
      setGridMessage('격자 보정 저장 기능을 사용할 수 없습니다.')
      return
    }
    try {
      await api.calibrateCombatMap(adventureId, { mapId: map.mapId, expectedVersion: map.version ?? 0, ...gridDraft })
      setGridEditor(false)
      setGridMessage('격자와 플레이어 시작 위치를 저장했습니다. 시야를 다시 계산합니다.')
      setMap(await api.getCombatMap(adventureId))
    } catch (error) {
      setGridMessage(error instanceof Error ? error.message : '격자 보정을 저장하지 못했습니다.')
    }
  }
  function startGridDrag(event: PointerEvent<HTMLElement>, mode: 'move' | 'resize') {
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    gridDrag.current = { mode, startX: event.clientX, startY: event.clientY, rect: event.currentTarget.getBoundingClientRect(), draft: { ...gridDraft } }
  }
  function updateGridDrag(event: PointerEvent<HTMLElement>) {
    const drag = gridDrag.current
    if (!drag) return
    const deltaX = event.clientX - drag.startX
    const deltaY = event.clientY - drag.startY
    if (drag.mode === 'resize') {
      const nextCellSize = Math.max(1, Math.round(drag.draft.cellSize + (deltaX * drag.draft.width) / Math.max(1, drag.rect.width)))
      const maxCellSize = Math.max(1, Math.min(
        Math.floor((drag.draft.imageWidth - drag.draft.originX) / drag.draft.width),
        Math.floor((drag.draft.imageHeight - drag.draft.originY) / drag.draft.height),
      ))
      setGridDraft({ ...drag.draft, cellSize: Math.min(nextCellSize, maxCellSize) })
      return
    }
    const imageDeltaX = (deltaX * drag.draft.width * drag.draft.cellSize) / Math.max(1, drag.rect.width)
    const imageDeltaY = (deltaY * drag.draft.height * drag.draft.cellSize) / Math.max(1, drag.rect.height)
    const maxOriginX = Math.max(0, drag.draft.imageWidth - drag.draft.width * drag.draft.cellSize)
    const maxOriginY = Math.max(0, drag.draft.imageHeight - drag.draft.height * drag.draft.cellSize)
    setGridDraft({ ...drag.draft, originX: Math.round(Math.min(maxOriginX, Math.max(0, drag.draft.originX + imageDeltaX))), originY: Math.round(Math.min(maxOriginY, Math.max(0, drag.draft.originY + imageDeltaY))) })
  }
  function finishGridDrag(event: PointerEvent<HTMLElement>) {
    if (gridDrag.current) {
      try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* pointer capture may already be released */ }
    }
    gridDrag.current = null
  }
  return (
    <section className={`adventure-tool map-panel${compact ? ' combat-map-panel' : ''}`} aria-labelledby="map-heading">
      <h2 id="map-heading">{compact ? '전장 지도' : '플레이어 전투 맵'}</h2>
      {!compact && <p>모험 ID: {adventureId}</p>}
      {!compact && <p role="status">{map ? `현재 맵 상태: ${map.status}` : '전투 맵을 불러오는 중…'}</p>}
      {showGridEditor ? <section aria-label={canEditGridGeometry ? '맵 격자 보정' : '플레이어 시작 위치 설정'} className="map-grid-editor">
        <button type="button" onClick={() => setGridEditor(current => !current)}>{gridEditor ? '설정 닫기' : canEditGridGeometry ? '격자 맞추기' : '시작 위치 정하기'}</button>
        {gridEditor ? <div>
          <p>{canEditGridGeometry ? '원본 맵에 격자가 없어 직접 맞춰야 합니다.' : '인쇄된 격자는 유지하고 플레이어 시작 위치만 정합니다.'}</p>
          <label>가로 셀 수<input type="number" min="1" disabled={!canEditGridGeometry} value={gridDraft.width} onChange={event => setGridDraft(current => ({ ...current, width: Number(event.target.value) }))} /></label>
          <label>세로 셀 수<input type="number" min="1" disabled={!canEditGridGeometry} value={gridDraft.height} onChange={event => setGridDraft(current => ({ ...current, height: Number(event.target.value) }))} /></label>
          <label>칸 크기<input type="number" min="1" disabled={!canEditGridGeometry} value={gridDraft.cellSize} onChange={event => setGridDraft(current => ({ ...current, cellSize: Number(event.target.value) }))} /></label>
          <label>왼쪽 위치<input type="number" min="0" disabled={!canEditGridGeometry} value={gridDraft.originX} onChange={event => setGridDraft(current => ({ ...current, originX: Number(event.target.value) }))} /></label>
          <label>위쪽 위치<input type="number" min="0" disabled={!canEditGridGeometry} value={gridDraft.originY} onChange={event => setGridDraft(current => ({ ...current, originY: Number(event.target.value) }))} /></label>
          <label>플레이어 시작 가로 셀<input type="number" min="0" value={gridDraft.playerX} onChange={event => setGridDraft(current => ({ ...current, playerX: Number(event.target.value) }))} /></label>
          <label>플레이어 시작 세로 셀<input type="number" min="0" value={gridDraft.playerY} onChange={event => setGridDraft(current => ({ ...current, playerY: Number(event.target.value) }))} /></label>
          <button type="button" onClick={() => void saveGrid()}>격자와 시작 위치 저장</button>
        </div> : null}
        <p role="status">{gridMessage}</p>
      </section> : null}
      {map.tokens ? (
        <div className="tactical-map-window">
          <button type="button" aria-pressed={locationMode} onClick={() => setLocationMode(current => !current)}>위치 선택</button>
          <div aria-label="tactical-map" data-map-id={map.mapId} data-version={map.version ?? 0} className="tactical-map" style={mapStyle}>
            {Array.from({ length: previewGrid.width * previewGrid.height }, (_, index) => {
              const cell = { x: index % previewGrid.width, y: Math.floor(index / previewGrid.width) }
              const token = map.tokens?.find(item => item.x === cell.x && item.y === cell.y)
              const blocked = map.obstacles?.some(obstacle => obstacle.x === cell.x && obstacle.y === cell.y)
              const door = map.doors?.find(item => item.x === cell.x && item.y === cell.y)
              const visible = map.current?.some(item => item.x === cell.x && item.y === cell.y)
                ?? (!hasVisibilityMetadata && token?.type === 'PLAYER')
              const explored = map.explored?.some(item => item.x === cell.x && item.y === cell.y) ?? false
              return <button key={`${cell.x}-${cell.y}`} type="button" aria-label={visible && token ? `${token.type} ${token.x},${token.y}` : visible ? `격자 ${cell.x},${cell.y}` : explored ? `탐험한 격자 ${cell.x},${cell.y}` : '미탐험 영역'} data-visibility={visible ? 'current' : explored ? 'explored' : 'hidden'} data-token-type={visible && token ? token.type : undefined} data-last-seen={token?.lastSeen ? 'true' : 'false'} disabled={blocked || !visible} draggable={token?.type === 'PLAYER'} onDragStart={() => { if (token?.type === 'PLAYER') setSelectedToken(token.id) }} onClick={() => { if (token?.type === 'PLAYER') setSelectedToken(token.id); else chooseCell(cell) }} onDragOver={event => event.preventDefault()} onDrop={() => chooseCell(cell)}>
                {visible && token ? `${token.type} (${token.x},${token.y})` : door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '장애물' : visible && !mapImage ? `${cell.x},${cell.y}` : explored ? '안개' : ''}
              </button>
            })}
            {gridEditor && canEditGridGeometry && <div className="grid-calibration-overlay" aria-label="격자 보정 드래그 영역" onPointerDown={event => startGridDrag(event, 'move')} onPointerMove={updateGridDrag} onPointerUp={finishGridDrag} onPointerCancel={finishGridDrag}>
              <span>지도를 드래그해 격자를 맞추세요</span>
              <button type="button" className="grid-calibration-handle" aria-label="격자 크기 조정" onPointerDown={event => { event.stopPropagation(); startGridDrag(event, 'resize') }} onPointerMove={updateGridDrag} onPointerUp={finishGridDrag} onPointerCancel={finishGridDrag}>↘</button>
            </div>}
          </div>
          <aside aria-label="맵 범례" className="map-legend">{[
            ['PLAYER', '●', '플레이어 캐릭터'], ['FRIENDLY_NPC', '◆', '우호 NPC'], ['NEUTRAL_NPC', '◇', '중립 NPC'],
            ['ENEMY', '▲', '적대 몬스터'], ['BOSS', '★', '보스'], ['TRAP', '⚠', '발견된 함정'], ['OBJECT', '■', '상호작용 오브젝트'],
          ].filter(([type]) => map.tokens?.some(token => token.type === type &&
            (token.type === 'PLAYER' && !hasVisibilityMetadata || map.current?.some(cell => cell.x === token.x && cell.y === token.y))))
            .map(([type, icon, label]) => <span key={type} className={`legend-token legend-${type.toLowerCase()}`}><span aria-hidden="true">{icon}</span><span>{label}</span></span>)}</aside>
        </div>
      ) : null}
      {map?.tokens?.filter(token => token.type !== 'PLAYER' && !token.lastSeen && map.current?.some(cell => cell.x === token.x && cell.y === token.y)).map(token => <button key={`target-${token.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'TARGET', { x: token.x, y: token.y }, token.id)) }}>대상 선택: {token.type}</button>)}
      {map?.objects?.filter(object => map.current?.some(cell => cell.x === object.x && cell.y === object.y)).map(object => <button key={`object-${object.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'INTERACT', { x: object.x, y: object.y }, object.id)) }}>상호작용: {object.type}</button>)}
      {candidate && <div role="dialog" aria-label="맵 행동 확인"><p>{candidate.action === 'MOVE' && candidate.from && candidate.to ? `이동: (${candidate.from.x},${candidate.from.y}) → (${candidate.to.x},${candidate.to.y})` : `맵 행동: ${candidate.action}`}</p><button type="button" disabled={submitting} onClick={() => void confirm()}>확인</button><button type="button" disabled={submitting} onClick={() => { setCandidate(null); setSelectedToken(null) }}>취소</button></div>}
      <p role="status">{message}</p>
    </section>
  )
}

function gridPath(from: { x: number; y: number }, to: { x: number; y: number }) {
  const path = [{ ...from }]
  let current = { ...from }
  while (current.x !== to.x || current.y !== to.y) {
    current = { x: current.x + Math.sign(to.x - current.x), y: current.y + Math.sign(to.y - current.y) }
    path.push({ ...current })
  }
  return path
}
