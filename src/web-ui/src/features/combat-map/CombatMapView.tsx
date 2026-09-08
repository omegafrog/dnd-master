import { useEffect, useState, type CSSProperties } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState } from '../saved-adventures/AdventurePlayApi'
import { actionCandidate, moveCandidate, type MapInteractionCandidate } from './MapInteractionCandidate'
import { MapGridAlignmentEditor } from './MapGridAlignmentEditor'
import { MapCropEditor } from './MapCropEditor'

export function CombatMapView({ adventureId, api, refreshToken = 0, compact = false, preparationMode = false, onPreparationComplete }: { adventureId: string; api: AdventurePlayApi; refreshToken?: number; compact?: boolean; preparationMode?: boolean; onPreparationComplete?: () => void }) {
  const [map, setMap] = useState<CombatMapState | null>(null)
  const [publicMapImage, setPublicMapImage] = useState<string | null>(null)
  const [selectedToken, setSelectedToken] = useState<string | null>(null)
  const [candidate, setCandidate] = useState<MapInteractionCandidate | null>(null)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [locationMode, setLocationMode] = useState(false)
  const [gridEditor, setGridEditor] = useState(false)
  const [gridMessage, setGridMessage] = useState('')
  const [alignment, setAlignment] = useState({ mapId: '', version: 0, imageRevision: '', originX: 0, originY: 0, cellSize: 30 })
  const [alignmentAvailable, setAlignmentAvailable] = useState(false)
  const [mapImageSize, setMapImageSize] = useState({ width: 1, height: 1 })
  const [layoutEditing, setLayoutEditing] = useState(false)
  const [layoutDirty, setLayoutDirty] = useState(false)
  const [gridConfirmed, setGridConfirmed] = useState(!preparationMode)
  const [crop, setCrop] = useState({ x: 0, y: 0, width: 0, height: 0 })
  const [layoutSaving, setLayoutSaving] = useState(false)

  useEffect(() => () => {
    if (publicMapImage?.startsWith('blob:')) URL.revokeObjectURL(publicMapImage)
  }, [publicMapImage])

  useEffect(() => {
    let active = true
    void (async () => {
      try {
        const nextMap = preparationMode
          ? await (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId))
          : await api.getCombatMap(adventureId)
        if (!active) return
        setMap(nextMap)
        const bounds = nextMap.layers?.find(layer => layer.type === 'GRID_BOUNDS')?.value?.split(',').map(Number)
        const nextGrid = nextMap.grid ?? { width: 20, height: 20 }
        if (bounds?.length === 6 && bounds.every(Number.isFinite)) {
          setAlignment({ mapId: nextMap.mapId ?? '', version: 0, imageRevision: '', cellSize: bounds[2] / nextGrid.width, originX: bounds[0], originY: bounds[1] })
        }
        try { const current = await (api.getMapGridAlignment?.(adventureId) ?? Promise.reject(new Error('unavailable'))); if (active) { setAlignment(current); setAlignmentAvailable(true) } } catch { if (active) { setAlignmentAvailable(false); setGridMessage('저장된 격자 정렬을 불러오지 못했습니다.') } }
        try {
          const image = preparationMode
            ? await (api.getCombatMapPreparationImage?.(adventureId) ?? api.getPublicMapImage?.(adventureId) ?? Promise.resolve(null))
            : await (api.getPublicMapImage?.(adventureId) ?? Promise.resolve(null))
          if (!active) {
            if (image?.startsWith('blob:')) URL.revokeObjectURL(image)
            return
          }
          setPublicMapImage(image)
        } catch {
          if (active) { setPublicMapImage(null); setGridMessage('공개된 지도 이미지를 불러오지 못했습니다.') }
        }
      } catch {
        if (active) { setMap(null); setPublicMapImage(null) }
      }
    })()
    return () => { active = false }
  }, [adventureId, api, preparationMode, refreshToken])

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
  const mapImage = publicMapImage
  useEffect(() => {
    const raw = map?.layers?.find(layer => layer.type === 'MAP_CROP')?.value?.split(',').map(Number)
    if (raw?.length === 4 && raw.every(Number.isFinite)) setCrop({ x: raw[0], y: raw[1], width: raw[2], height: raw[3] })
    else if (mapImageSize.width > 1 && mapImageSize.height > 1) setCrop({ x: 0, y: 0, width: mapImageSize.width, height: mapImageSize.height })
  }, [map, mapImageSize])
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
  const showGridEditor = !!mapImage && alignmentAvailable && !!api.getMapGridAlignment && !!api.applyMapGridAlignment
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
    }
    image.src = mapImage
  }, [mapImage])
  const previewGrid = gridEditor && showGridEditor
    ? { ...grid, ...alignment }
    : { width: grid.width, height: grid.height, cellSize: hasGridBounds ? Math.round(boundsWidth / Math.max(grid.width, 1)) : 30, originX: hasGridBounds ? gridBounds[0] : 0, originY: hasGridBounds ? gridBounds[1] : 0, imageWidth: imageWidth || mapImageSize.width, imageHeight: imageHeight || mapImageSize.height }
  const renderedGridWidth = hasGridBounds && !gridEditor ? boundsWidth : previewGrid.width * previewGrid.cellSize
  const renderedGridHeight = hasGridBounds && !gridEditor ? boundsHeight : previewGrid.height * previewGrid.cellSize
  const mapStyle = {
    '--grid-columns': previewGrid.width,
    '--grid-rows': previewGrid.height,
    '--map-aspect': `${Math.max(renderedGridWidth, 1)} / ${Math.max(renderedGridHeight, 1)}`,
    ...(mapImage ? { backgroundImage: `url(${mapImage})` } : {}),
    ...(hasGridBounds || gridEditor ? {
      '--map-background-size': `${((imageWidth || mapImageSize.width) / Math.max(renderedGridWidth, 1)) * 100}% ${((imageHeight || mapImageSize.height) / Math.max(renderedGridHeight, 1)) * 100}%`,
      '--map-background-position': `${gridEditor ? 'left top' : backgroundPositionX} ${gridEditor ? 'left top' : backgroundPositionY}`,
    } : {}),
    ...(!gridEditor && crop.width > 0 && crop.height > 0 ? {
      '--map-background-size': `${((imageWidth || mapImageSize.width) / crop.width) * 100}% ${((imageHeight || mapImageSize.height) / crop.height) * 100}%`,
      '--map-background-position': `${(crop.x / Math.max((imageWidth || mapImageSize.width) - crop.width, 1)) * 100}% ${(crop.y / Math.max((imageHeight || mapImageSize.height) - crop.height, 1)) * 100}%`,
    } : {}),
  } as CSSProperties
  const hasVisibilityMetadata = Array.isArray(map?.current) && Array.isArray(map?.explored)
  // A combat map is stage-scoped.  The backend returns an empty projection for
  // event/town stages; keep the entire tactical panel out of the player UI in
  // that state instead of showing a permanent "no map" panel.
  if (!map || !map.mapId) return null
  async function saveLayout() {
    if (!api.updateCombatMapLayout || !map?.mapId) return
    setLayoutSaving(true); setMessage('')
    try {
      await api.updateCombatMapLayout(adventureId, { commandId: globalThis.crypto.randomUUID(), expectedVersion: map.version ?? 0,
        obstacles: map.obstacles ?? [], doors: (map.doors ?? []).map(door => ({ x: door.x, y: door.y })),
        crop: crop.width > 0 && crop.height > 0 ? `${Math.max(0, crop.x)},${Math.max(0, crop.y)},${crop.width},${crop.height}` : undefined })
      const refreshed = await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId))
      setMap(refreshed)
      const refreshedAlignment = await (api.getMapGridAlignment?.(adventureId) ?? Promise.reject(new Error('unavailable')))
      setAlignment(refreshedAlignment); setAlignmentAvailable(true); setMessage('벽·문·자르기 설정을 저장했습니다.'); setLayoutEditing(false); setLayoutDirty(false)
    } catch (error) { setMessage(error instanceof Error ? error.message : '맵 초안을 저장하지 못했습니다.') }
    finally { setLayoutSaving(false) }
  }
  function toggleLayoutCell(cell: { x: number; y: number }) {
    if (!map) return
    const door = map.doors?.find(item => item.x === cell.x && item.y === cell.y)
    if (door) setMap({ ...map, doors: map.doors?.filter(item => item.x !== cell.x || item.y !== cell.y) })
    else if (map.obstacles?.some(item => item.x === cell.x && item.y === cell.y)) setMap({ ...map, obstacles: map.obstacles?.filter(item => item.x !== cell.x || item.y !== cell.y), doors: [...(map.doors ?? []), { x: cell.x, y: cell.y, open: false }] })
    else setMap({ ...map, obstacles: [...(map.obstacles ?? []), cell] })
    setLayoutDirty(true)
  }
  return (
    <section className={`adventure-tool map-panel${compact ? ' combat-map-panel' : ''}`} aria-labelledby="map-heading">
      <h2 id="map-heading">{compact ? '전장 지도' : '플레이어 전투 맵'}</h2>
      {!compact && <p>모험 ID: {adventureId}</p>}
      {!compact && <p role="status">{map ? `현재 맵 상태: ${map.status}` : '전투 맵을 불러오는 중…'}</p>}
      {showGridEditor ? <section aria-label="맵 격자 맞추기" className="map-grid-editor">
        <button type="button" onClick={() => setGridEditor(true)}>격자 맞추기</button>
        {gridEditor && <MapGridAlignmentEditor image={mapImage!} initial={alignment} onCancel={() => { setGridEditor(false); setGridMessage('이번 정렬 초안을 취소했습니다.') }} onApply={async value => {
          const saved = await api.applyMapGridAlignment!(adventureId, value)
          setAlignment(saved); setGridConfirmed(true); setGridEditor(false); setGridMessage('격자 정렬을 저장했습니다. 이제 벽과 문 초안을 검수하세요.'); setMap(await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId)))
        }} />}
        <p role="status">{gridMessage}</p>
      </section> : null}
      {preparationMode && <section className="map-preparation-editor" aria-label="맵 초안 검수"><h3>맵 초안 검수</h3><p>{gridConfirmed ? 'AI가 제안한 벽과 문을 격자 경계선마다 고치고, 여백을 잘라내세요.' : '먼저 격자 맞추기에서 격자를 저장하세요. 저장 뒤 벽·문 초안을 검수할 수 있습니다.'}</p>{gridConfirmed && <><button type="button" onClick={() => setLayoutEditing(current => !current)}>{layoutEditing ? '검수 닫기' : '벽·문·자르기 편집'}</button>{layoutEditing && <div>{mapImage && <MapCropEditor image={mapImage} crop={crop} onChange={next => { setCrop(next); setLayoutDirty(true) }} />}<button type="button" disabled={layoutSaving} onClick={() => void saveLayout()}>{layoutSaving ? '저장 중…' : '맵 초안 저장'}</button></div>}</>}</section>}
      {map.tokens ? (
        <div className="tactical-map-window">
          {!preparationMode && <button type="button" aria-pressed={locationMode} onClick={() => setLocationMode(current => !current)}>위치 선택</button>}
          <div aria-label="tactical-map" data-map-id={map.mapId} data-version={map.version ?? 0} className="tactical-map" style={mapStyle}>
            {Array.from({ length: previewGrid.width * previewGrid.height }, (_, index) => {
              const cell = { x: index % previewGrid.width, y: Math.floor(index / previewGrid.width) }
              const token = map.tokens?.find(item => item.x === cell.x && item.y === cell.y)
              const blocked = map.obstacles?.some(obstacle => obstacle.x === cell.x && obstacle.y === cell.y)
              const door = map.doors?.find(item => item.x === cell.x && item.y === cell.y)
              const visible = preparationMode || (map.current?.some(item => item.x === cell.x && item.y === cell.y)
                ?? (!hasVisibilityMetadata && token?.type === 'PLAYER'))
              const explored = map.explored?.some(item => item.x === cell.x && item.y === cell.y) ?? false
              const draftLabel = door ? `${door.open ? '열린 문' : '닫힌 문'} ${cell.x},${cell.y}` : blocked ? `벽 ${cell.x},${cell.y}` : token ? `플레이어 시작 위치 ${cell.x},${cell.y}` : `빈 격자 ${cell.x},${cell.y}`
              return <button key={`${cell.x}-${cell.y}`} type="button" className={preparationMode ? door ? 'map-draft-door' : blocked ? 'map-draft-wall' : undefined : undefined} aria-label={preparationMode ? draftLabel : visible && token ? `${token.type} ${token.x},${token.y}` : visible ? `격자 ${cell.x},${cell.y}` : explored ? `탐험한 격자 ${cell.x},${cell.y}` : '미탐험 영역'} data-visibility={visible ? 'current' : explored ? 'explored' : 'hidden'} data-token-type={visible && token ? token.type : undefined} data-last-seen={token?.lastSeen ? 'true' : 'false'} disabled={preparationMode ? token?.type === 'PLAYER' : blocked || !visible} draggable={!preparationMode && token?.type === 'PLAYER'} onDragStart={() => { if (!preparationMode && token?.type === 'PLAYER') setSelectedToken(token.id) }} onClick={() => { if (preparationMode && !token) toggleLayoutCell(cell); else if (token?.type === 'PLAYER') setSelectedToken(token.id); else chooseCell(cell) }} onDragOver={event => event.preventDefault()} onDrop={() => chooseCell(cell)}>
                {preparationMode ? door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '벽' : token ? '시작' : '' : visible && token ? `${token.type} (${token.x},${token.y})` : door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '장애물' : visible && !mapImage ? `${cell.x},${cell.y}` : explored ? '안개' : ''}
              </button>
            })}
          </div>
          {!preparationMode && <aside aria-label="맵 범례" className="map-legend">{[
            ['PLAYER', '●', '플레이어 캐릭터'], ['FRIENDLY_NPC', '◆', '우호 NPC'], ['NEUTRAL_NPC', '◇', '중립 NPC'],
            ['ENEMY', '▲', '적대 몬스터'], ['BOSS', '★', '보스'], ['TRAP', '⚠', '발견된 함정'], ['OBJECT', '■', '상호작용 오브젝트'],
          ].filter(([type]) => map.tokens?.some(token => token.type === type &&
            (token.type === 'PLAYER' && !hasVisibilityMetadata || map.current?.some(cell => cell.x === token.x && cell.y === token.y))))
            .map(([type, icon, label]) => <span key={type} className={`legend-token legend-${type.toLowerCase()}`}><span aria-hidden="true">{icon}</span><span>{label}</span></span>)}</aside>}
        </div>
      ) : null}
      {map?.tokens?.filter(token => token.type !== 'PLAYER' && !token.lastSeen && map.current?.some(cell => cell.x === token.x && cell.y === token.y)).map(token => <button key={`target-${token.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'TARGET', { x: token.x, y: token.y }, token.id)) }}>대상 선택: {token.type}</button>)}
      {map?.objects?.filter(object => map.current?.some(cell => cell.x === object.x && cell.y === object.y)).map(object => <button key={`object-${object.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'INTERACT', { x: object.x, y: object.y }, object.id)) }}>상호작용: {object.type}</button>)}
      {candidate && <div role="dialog" aria-label="맵 행동 확인"><p>{candidate.action === 'MOVE' && candidate.from && candidate.to ? `이동: (${candidate.from.x},${candidate.from.y}) → (${candidate.to.x},${candidate.to.y})` : `맵 행동: ${candidate.action}`}</p><button type="button" disabled={submitting} onClick={() => void confirm()}>확인</button><button type="button" disabled={submitting} onClick={() => { setCandidate(null); setSelectedToken(null) }}>취소</button></div>}
      <p role="status">{message}</p>
      {preparationMode && <button type="button" disabled={layoutSaving || layoutDirty} onClick={onPreparationComplete}>맵 준비 완료, 모험 시작</button>}
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
