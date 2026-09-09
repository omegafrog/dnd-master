import { useEffect, useRef, useState, type CSSProperties } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState, MapBoundary, MapBoundaryProposal } from '../saved-adventures/AdventurePlayApi'
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
  const [layoutBeforeEdit, setLayoutBeforeEdit] = useState<CombatMapState | null>(null)
  const [layoutDirty, setLayoutDirty] = useState(false)
  const [layoutSaved, setLayoutSaved] = useState(!preparationMode)
  const [gridConfirmed, setGridConfirmed] = useState(!preparationMode)
  const [cropConfirmed, setCropConfirmed] = useState(!preparationMode)
  const [cropEditorOpen, setCropEditorOpen] = useState(preparationMode)
  const [crop, setCrop] = useState({ x: 0, y: 0, width: 0, height: 0 })
  const [layoutSaving, setLayoutSaving] = useState(false)
  const [boundaryTool, setBoundaryTool] = useState<'WALL' | 'DOOR' | 'ERASE'>('WALL')
  const [boundaryDetecting, setBoundaryDetecting] = useState(false)
  const boundaryStroke = useRef<BoundaryStroke | null>(null)
  const [boundaryPreview, setBoundaryPreview] = useState<BoundaryStroke | null>(null)

  useEffect(() => () => {
    if (publicMapImage?.startsWith('blob:')) URL.revokeObjectURL(publicMapImage)
  }, [publicMapImage])

  useEffect(() => {
    let active = true
    void (async () => {
      try {
        const fetchedMap = preparationMode
          ? await (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId))
          : await api.getCombatMap(adventureId)
        // Story turns happen before a tactical combat situation is active.
        // The player still needs the reviewed map in that conversation, so
        // fall back to the prepared projection instead of rendering nothing.
        const nextMap = !preparationMode && !fetchedMap.mapId && api.getCombatMapPreparation
          ? { ...await api.getCombatMapPreparation(adventureId), status: 'story-map' }
          : fetchedMap
        if (!active) return
        setMap(nextMap)
        if (preparationMode) setLayoutSaved(false)
        const bounds = nextMap.layers?.find(layer => layer.type === 'GRID_BOUNDS')?.value?.split(',').map(Number)
        const nextGrid = nextMap.grid ?? { width: 20, height: 20 }
        if (bounds?.length === 6 && bounds.every(Number.isFinite)) {
          setAlignment({ mapId: nextMap.mapId ?? '', version: 0, imageRevision: '', cellSize: bounds[2] / nextGrid.width, originX: bounds[0], originY: bounds[1] })
        }
        try { const current = await (api.getMapGridAlignment?.(adventureId) ?? Promise.reject(new Error('unavailable'))); if (active) { setAlignment(current); setAlignmentAvailable(true); if (current.version > 0 && !preparationMode) setGridConfirmed(true); if (preparationMode) setLayoutSaved(layoutConfirmedForAlignment(nextMap, current)) } } catch { if (active) { setAlignmentAvailable(false); setLayoutSaved(false); setGridMessage('저장된 격자 정렬을 불러오지 못했습니다.') } }
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
  const usesSavedAlignment = alignmentAvailable && alignment.mapId === map?.mapId && alignment.cellSize > 0 && (!preparationMode || gridConfirmed)
  const previewGrid = (gridEditor && showGridEditor) || usesSavedAlignment
    ? { ...grid, ...alignment }
    : { width: grid.width, height: grid.height, cellSize: hasGridBounds ? Math.round(boundsWidth / Math.max(grid.width, 1)) : 30, originX: hasGridBounds ? gridBounds[0] : 0, originY: hasGridBounds ? gridBounds[1] : 0, imageWidth: imageWidth || mapImageSize.width, imageHeight: imageHeight || mapImageSize.height }
  const renderedGridWidth = hasGridBounds && !gridEditor && !usesSavedAlignment ? boundsWidth : previewGrid.width * previewGrid.cellSize
  const renderedGridHeight = hasGridBounds && !gridEditor && !usesSavedAlignment ? boundsHeight : previewGrid.height * previewGrid.cellSize
  const renderedImageWidth = imageWidth || mapImageSize.width
  const renderedImageHeight = imageHeight || mapImageSize.height
  // The crop is gameplay data, not merely an image-editor aid.  In the player
  // view, remove cells outside it altogether so an unplayable black canvas is
  // never mistaken for unexplored dungeon space.
  const playableWindow = !preparationMode ? playableGridWindow(map, previewGrid, grid) : null
  const displayedColumns = playableWindow?.width ?? previewGrid.width
  const displayedRows = playableWindow?.height ?? previewGrid.height
  const displayedGridWidth = playableWindow ? displayedColumns * previewGrid.cellSize : renderedGridWidth
  const displayedGridHeight = playableWindow ? displayedRows * previewGrid.cellSize : renderedGridHeight
  const backgroundPositionX = renderedImageWidth > renderedGridWidth ? `${(previewGrid.originX / (renderedImageWidth - renderedGridWidth)) * 100}%` : 'center'
  const backgroundPositionY = renderedImageHeight > renderedGridHeight ? `${(previewGrid.originY / (renderedImageHeight - renderedGridHeight)) * 100}%` : 'center'
  const preserveAlignmentForLayout = preparationMode && layoutEditing
  const mapStyle = {
    '--grid-columns': displayedColumns,
    '--grid-rows': displayedRows,
    '--map-aspect': `${Math.max(displayedGridWidth, 1)} / ${Math.max(displayedGridHeight, 1)}`,
    ...(mapImage ? { backgroundImage: `url(${mapImage})` } : {}),
    ...(playableWindow ? {
      '--map-background-size': `${(renderedImageWidth / Math.max(crop.width, 1)) * 100}% ${(renderedImageHeight / Math.max(crop.height, 1)) * 100}%`,
      '--map-background-position': `${(crop.x / Math.max(renderedImageWidth - crop.width, 1)) * 100}% ${(crop.y / Math.max(renderedImageHeight - crop.height, 1)) * 100}%`,
    } : hasGridBounds || gridEditor || usesSavedAlignment ? {
      '--map-background-size': `${(renderedImageWidth / Math.max(renderedGridWidth, 1)) * 100}% ${(renderedImageHeight / Math.max(renderedGridHeight, 1)) * 100}%`,
      '--map-background-position': `${gridEditor ? 'left top' : backgroundPositionX} ${gridEditor ? 'left top' : backgroundPositionY}`,
    } : {}),
    ...(!gridEditor && !preserveAlignmentForLayout && !hasGridBounds && !usesSavedAlignment && crop.width > 0 && crop.height > 0 ? {
      '--map-background-size': `${((imageWidth || mapImageSize.width) / crop.width) * 100}% ${((imageHeight || mapImageSize.height) / crop.height) * 100}%`,
      '--map-background-position': `${(crop.x / Math.max((imageWidth || mapImageSize.width) - crop.width, 1)) * 100}% ${(crop.y / Math.max((imageHeight || mapImageSize.height) - crop.height, 1)) * 100}%`,
    } : {}),
  } as CSSProperties
  const storyMap = map?.status === 'story-map'
  const hasVisibilityMetadata = !storyMap && Array.isArray(map?.current) && Array.isArray(map?.explored)
  // A combat map is stage-scoped.  The backend returns an empty projection for
  // event/town stages; keep the entire tactical panel out of the player UI in
  // that state instead of showing a permanent "no map" panel.
  if (!map || !map.mapId) return null
  async function applyCrop() {
    if (!api.updateCombatMapLayout || !map?.mapId || crop.width <= 0 || crop.height <= 0) return
    setLayoutSaving(true); setMessage('')
    const localCrop = `${Math.max(0, crop.x)},${Math.max(0, crop.y)},${crop.width},${crop.height}`
    try {
      await api.updateCombatMapLayout(adventureId, {
        commandId: globalThis.crypto.randomUUID(), expectedVersion: map.version ?? 0,
        obstacles: map.obstacles ?? [], doors: map.doors?.map(door => ({ x: door.x, y: door.y })) ?? [],
        boundaries: mapBoundaries, crop: localCrop,
      })
      const refreshed = await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId))
      setMap(refreshed); setCropConfirmed(true); setGridConfirmed(false); setCropEditorOpen(false); setLayoutDirty(false); setLayoutSaved(false)
      setMessage('여백 자르기를 적용했습니다. 이제 격자를 맞추세요.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '여백 자르기를 적용하지 못했습니다.')
    } finally { setLayoutSaving(false) }
  }
  async function saveLayout() {
    if (!api.updateCombatMapLayout || !map?.mapId) return
    setLayoutSaving(true); setMessage('')
    const localBoundaries = mapBoundaries
    const localCrop = crop.width > 0 && crop.height > 0 ? `${Math.max(0, crop.x)},${Math.max(0, crop.y)},${crop.width},${crop.height}` : undefined
    const save = (expectedVersion: number) => api.updateCombatMapLayout!(adventureId, {
      commandId: globalThis.crypto.randomUUID(), expectedVersion, obstacles: map?.obstacles ?? [],
      doors: map?.doors?.map(door => ({ x: door.x, y: door.y })) ?? [], boundaries: localBoundaries, crop: localCrop,
      alignmentVersion: alignment.version, imageRevision: alignment.imageRevision,
    })
    try {
      await save(map.version ?? 0)
      const refreshed = await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId))
      setMap(refreshed)
      const refreshedAlignment = await (api.getMapGridAlignment?.(adventureId) ?? Promise.reject(new Error('unavailable')))
      setAlignment(refreshedAlignment); setAlignmentAvailable(true); setMessage('벽·문·자르기 설정을 저장했습니다.'); setLayoutEditing(false); setLayoutDirty(false); setLayoutSaved(true); setLayoutBeforeEdit(null)
    } catch (error) {
      if (error instanceof Error && 'status' in error && (error as { status?: number }).status === 409) {
        let latest: CombatMapState | null = null
        try {
          latest = await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId))
          // AI redraft may have won the version race. Rebase the user's local
          // lines and crop onto the latest map, then retry once.
          await save(latest.version ?? 0)
          const refreshed = await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId))
          setMap(refreshed)
          const refreshedAlignment = await (api.getMapGridAlignment?.(adventureId) ?? Promise.reject(new Error('unavailable')))
          setAlignment(refreshedAlignment); setAlignmentAvailable(true); setMessage('최신 초안에 변경 내용을 다시 적용했습니다.'); setLayoutEditing(false); setLayoutDirty(false); setLayoutSaved(true); setLayoutBeforeEdit(null)
          return
        } catch {
          if (latest) {
            const layers = (latest.layers ?? []).filter(layer => !['MAP_BOUNDARIES', 'MAP_CROP'].includes(layer.type))
            const rebasedLayers = localBoundaries.length ? [...layers, { type: 'MAP_BOUNDARIES', value: localBoundaries.map(encodeBoundary).join(';'), visibility: 'PLAYER_VISIBLE' }] : layers
            if (localCrop) rebasedLayers.push({ type: 'MAP_CROP', value: localCrop, visibility: 'PLAYER_VISIBLE' })
            setMap({ ...latest, obstacles: [], doors: [], layers: rebasedLayers })
          }
          setLayoutSaved(false)
          setMessage('최신 초안과 충돌했습니다. 변경 내용을 확인한 뒤 다시 저장하세요.')
        }
      } else setMessage(error instanceof Error ? error.message : '맵 초안을 저장하지 못했습니다.')
    }
    finally { setLayoutSaving(false) }
  }
  const mapBoundaries = boundariesFrom(map)
  async function detectBoundaries() {
    if (!api.detectMapBoundaries || !map?.mapId) return
    setBoundaryDetecting(true); setMessage('')
    try {
      const proposal = await api.detectMapBoundaries(adventureId)
      if (proposal.alignmentVersion !== undefined && proposal.alignmentVersion !== alignment.version) {
        throw new Error('격자 정렬이 바뀌었습니다. 최신 격자를 불러온 뒤 다시 감지하세요.')
      }
      if (proposal.imageRevision !== undefined && proposal.imageRevision !== alignment.imageRevision) {
        throw new Error('지도 이미지가 바뀌었습니다. 최신 이미지를 불러온 뒤 다시 감지하세요.')
      }
      setLayoutBeforeEdit(map)
      setMap(currentMap => currentMap ? applyBoundaryProposal(currentMap, proposal) : currentMap)
      setLayoutEditing(true)
      setLayoutDirty(true)
      setLayoutSaved(false)
      setMessage('AI가 현재 격자와 지도 이미지를 기준으로 벽·문 초안을 만들었습니다. 결과를 확인하고 저장하세요.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'AI 벽·문 감지를 처리하지 못했습니다.')
    } finally { setBoundaryDetecting(false) }
  }
  function applyBoundaryStroke(stroke: BoundaryStroke) {
    const touched = boundariesInStroke(stroke)
    setMap(currentMap => {
      if (!currentMap) return currentMap
      const next = stroke.tool === 'ERASE'
        ? stroke.originalBoundaries.filter(existing => !touched.some(item => sameBoundarySide(existing, item)))
        : [...stroke.originalBoundaries.filter(existing => !touched.some(item => sameBoundarySide(existing, item))), ...touched.map(item => ({ ...item, kind: stroke.tool, open: false } as MapBoundary))]
      const layers = (currentMap.layers ?? []).filter(layer => layer.type !== 'MAP_BOUNDARIES')
      return { ...currentMap, layers: next.length ? [...layers, { type: 'MAP_BOUNDARIES', value: next.map(encodeBoundary).join(';'), visibility: 'PLAYER_VISIBLE' }] : layers }
    })
    setLayoutDirty(true); setLayoutSaved(false)
    const label = stroke.tool === 'ERASE' ? '지우는 중' : stroke.tool === 'DOOR' ? '문 그리는 중' : '벽 그리는 중'
    setMessage(`${label} · ${touched.length}개 선분`)
  }
  function restoreBoundaryStroke(stroke: BoundaryStroke) {
    setMap(currentMap => {
      if (!currentMap) return currentMap
      const layers = (currentMap.layers ?? []).filter(layer => layer.type !== 'MAP_BOUNDARIES')
      return { ...currentMap, layers: stroke.originalBoundaries.length ? [...layers, { type: 'MAP_BOUNDARIES', value: stroke.originalBoundaries.map(encodeBoundary).join(';'), visibility: 'PLAYER_VISIBLE' }] : layers }
    })
    setLayoutDirty(false); setLayoutSaved(false)
  }
  const tacticalMap = map.tokens ? (
    <div className="tactical-map-window">
      {!preparationMode && <button type="button" aria-pressed={locationMode} onClick={() => setLocationMode(current => !current)}>위치 선택</button>}
          <div aria-label="tactical-map" data-map-id={map.mapId} data-version={map.version ?? 0} className="tactical-map" style={mapStyle} onPointerDown={event => {
            if (!preparationMode) return
            const stroke = startBoundaryStroke(event, previewGrid.width, previewGrid.height)
            if (!stroke) return
            event.currentTarget.setPointerCapture?.(event.pointerId)
            const activeStroke = { ...stroke, tool: boundaryTool, originalBoundaries: mapBoundaries }
            boundaryStroke.current = activeStroke; setBoundaryPreview(activeStroke)
            applyBoundaryStroke(activeStroke)
          }} onPointerMove={event => {
            if (!boundaryStroke.current) return
            const next = extendBoundaryStroke(boundaryStroke.current, event, previewGrid.width, previewGrid.height)
            const activeStroke = { ...next, tool: boundaryStroke.current.tool, originalBoundaries: boundaryStroke.current.originalBoundaries }
            boundaryStroke.current = activeStroke; setBoundaryPreview(activeStroke)
            // Draw continuously, rather than waiting for pointer-up. The edit
            // remains local until save and the existing cancel action restores
            // the complete pre-edit map.
            applyBoundaryStroke(activeStroke)
          }} onPointerUp={event => {
            const stroke = boundaryStroke.current
            if (!stroke) return
            boundaryStroke.current = null; setBoundaryPreview(null); setMessage('선분 수정을 반영했습니다. 저장하려면 맵 초안 저장을 누르세요.')
            if (event.currentTarget.hasPointerCapture?.(event.pointerId)) event.currentTarget.releasePointerCapture?.(event.pointerId)
          }} onPointerCancel={() => { const stroke = boundaryStroke.current; if (stroke) restoreBoundaryStroke(stroke); boundaryStroke.current = null; setBoundaryPreview(null); setMessage('선분 드래그를 취소했습니다.') }}>
        {Array.from({ length: displayedColumns * displayedRows }, (_, index) => {
          const cell = { x: (playableWindow?.minX ?? 0) + index % displayedColumns, y: (playableWindow?.minY ?? 0) + Math.floor(index / displayedColumns) }
          const token = map.tokens?.find(item => item.x === cell.x && item.y === cell.y)
          const playable = isPlayableGridCell(map, previewGrid, cell)
          const blocked = !preparationMode && map.obstacles?.some(obstacle => obstacle.x === cell.x && obstacle.y === cell.y)
          const door = !preparationMode && map.doors?.find(item => item.x === cell.x && item.y === cell.y)
          const visible = playable && (preparationMode || storyMap || (map.current?.some(item => item.x === cell.x && item.y === cell.y)
            ?? (!hasVisibilityMetadata && token?.type === 'PLAYER')))
          const explored = playable && (map.explored?.some(item => item.x === cell.x && item.y === cell.y) ?? false)
          const draftLabel = door ? `${door.open ? '열린 문' : '닫힌 문'} ${cell.x},${cell.y}` : blocked ? `벽 ${cell.x},${cell.y}` : token ? `플레이어 시작 위치 ${cell.x},${cell.y}` : `빈 격자 ${cell.x},${cell.y}`
          return <button key={`${cell.x}-${cell.y}`} type="button" aria-label={preparationMode ? draftLabel : !playable ? '지도 밖 영역' : visible && token ? `${token.type} ${token.x},${token.y}` : visible ? `격자 ${cell.x},${cell.y}` : explored ? `탐험한 격자 ${cell.x},${cell.y}` : '미탐험 영역'} data-visibility={!playable ? 'outside' : visible ? 'current' : explored ? 'explored' : 'hidden'} data-token-type={visible && token ? token.type : undefined} data-last-seen={token?.lastSeen ? 'true' : 'false'} disabled={preparationMode || !playable || blocked || !visible} draggable={!preparationMode && token?.type === 'PLAYER'} onDragStart={() => { if (!preparationMode && token?.type === 'PLAYER') setSelectedToken(token.id) }} onClick={() => { if (!preparationMode && token?.type === 'PLAYER') setSelectedToken(token.id); else if (!preparationMode) chooseCell(cell) }} onDragOver={event => event.preventDefault()} onDrop={() => chooseCell(cell)}>
            {preparationMode ? door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '벽' : token ? '시작' : '' : visible && token ? `${token.type} (${token.x},${token.y})` : door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '장애물' : visible && !mapImage ? `${cell.x},${cell.y}` : explored ? '안개' : ''}
          </button>
          })}
          {mapBoundaries.map(boundary => <span key={`boundary-${boundary.x}-${boundary.y}-${boundary.orientation}`} aria-hidden="true" className={`map-boundary map-boundary-${boundary.kind.toLowerCase()}`} data-boundary={`${boundary.orientation}:${boundary.x}:${boundary.y}`} style={boundaryStyle(boundary, previewGrid.width, previewGrid.height)} />)}
          {preparationMode && boundaryPreview && <span aria-hidden="true" className={`map-boundary map-boundary-preview map-boundary-${boundaryTool.toLowerCase()}`} style={boundaryStrokeStyle(boundaryPreview, previewGrid.width, previewGrid.height)} />}
          </div>
      {!preparationMode && <aside aria-label="맵 범례" className="map-legend">{[
        ['PLAYER', '●', '플레이어 캐릭터'], ['FRIENDLY_NPC', '◆', '우호 NPC'], ['NEUTRAL_NPC', '◇', '중립 NPC'],
        ['ENEMY', '▲', '적대 몬스터'], ['BOSS', '★', '보스'], ['TRAP', '⚠', '발견된 함정'], ['OBJECT', '■', '상호작용 오브젝트'],
      ].filter(([type]) => map.tokens?.some(token => token.type === type &&
          (token.type === 'PLAYER' && !hasVisibilityMetadata || map.current?.some(cell => cell.x === token.x && cell.y === token.y))))
          .map(([type, icon, label]) => <span key={type} className={`legend-token legend-${type.toLowerCase()}`}><span aria-hidden="true">{icon}</span><span>{label}</span></span>)}</aside>}
      {!preparationMode && hasVisibilityMetadata && <aside aria-label="지도 공개 범례" className="map-visibility-legend">
        <span><i className="visibility-swatch visibility-hidden" aria-hidden="true" />아직 확인하지 않은 영역</span>
        <span><i className="visibility-swatch visibility-explored" aria-hidden="true" />전에 확인했지만 지금은 시야 밖인 영역</span>
      </aside>}
    </div>
  ) : null
  return (
    <section className={`adventure-tool map-panel${compact ? ' combat-map-panel' : ''}`} aria-labelledby="map-heading">
      <h2 id="map-heading">{compact ? '전장 지도' : '플레이어 전투 맵'}</h2>
      {!compact && <p>모험 ID: {adventureId}</p>}
      {!compact && <p role="status">{map ? `현재 맵 상태: ${map.status}` : '전투 맵을 불러오는 중…'}</p>}
      {preparationMode && mapImage && <section aria-label="맵 여백 자르기" className="map-crop-preparation">
        <h3>1. 맵 여백 자르기</h3>
        {cropEditorOpen ? <>
          <p>남길 영역을 드래그한 뒤 자르기 적용을 누르세요. 적용해야 다음 단계로 넘어갑니다.</p>
          <MapCropEditor image={mapImage} crop={crop} onChange={next => { setCrop(next); setLayoutDirty(true); setLayoutSaved(false); setCropConfirmed(false) }} />
          <button type="button" disabled={layoutSaving || crop.width <= 0 || crop.height <= 0} onClick={() => void applyCrop()}>자르기 적용</button>
        </> : <button type="button" onClick={() => { setCropEditorOpen(true); setCropConfirmed(false); setGridConfirmed(false); setLayoutDirty(true); setLayoutSaved(false) }}>자르기 다시 수정</button>}
      </section>}
      {showGridEditor && (!preparationMode || cropConfirmed) ? <section aria-label="맵 격자 맞추기" className="map-grid-editor">
        <h3>2. 맵 격자 맞추기</h3>
        <button type="button" onClick={() => setGridEditor(true)}>격자 맞추기</button>
        {gridEditor && <MapGridAlignmentEditor key={`${alignment.mapId}-${alignment.version}`} image={mapImage!} initial={alignment} crop={crop} gridWidth={grid.width} gridHeight={grid.height} onCancel={() => { setGridEditor(false); setGridMessage('이번 정렬 초안을 취소했습니다.') }} onApply={async value => {
          try {
            const saved = await api.applyMapGridAlignment!(adventureId, value)
            setAlignment(saved); setGridConfirmed(true); setLayoutSaved(false); setGridEditor(false); setGridMessage('격자 정렬을 저장했습니다. 이제 AI 초안을 생성하세요.'); setMap(await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId)))
          } catch (error) {
            if (error instanceof Error && 'status' in error && (error as { status?: number }).status === 409) {
              try { const latest = await api.getMapGridAlignment!(adventureId); setAlignment(latest); setAlignmentAvailable(true); setGridMessage('다른 정렬 저장이 먼저 반영됐습니다. 최신 정렬을 불러왔습니다. 다시 적용하세요.') } catch { /* keep the original error in the editor */ }
            }
            throw error
          }
        }} />}
        <p role="status">{gridMessage}</p>
      </section> : null}
      {preparationMode && <section className="map-preparation-editor" aria-label="맵 초안 검수"><h3>3. AI 초안 생성 및 검수</h3><p>{!cropConfirmed ? '먼저 1단계에서 여백 자르기를 적용하세요.' : !gridConfirmed ? '먼저 2단계에서 격자를 맞추고 적용하세요.' : '격자 적용 완료. 현재 자른 영역과 격자를 기준으로 AI 초안을 생성합니다.'}</p>{cropConfirmed && gridConfirmed && <><button type="button" disabled={boundaryDetecting || layoutSaving || layoutEditing} onClick={() => void detectBoundaries()}>{boundaryDetecting ? 'AI 벽·문 감지 중…' : 'AI 벽·문 감지'}</button><button type="button" onClick={() => { if (layoutEditing) { setLayoutEditing(false); return }; if (!layoutBeforeEdit) setLayoutBeforeEdit(map); setLayoutSaved(false); setLayoutEditing(true) }}>{layoutEditing ? '검수 닫기' : '벽·문 편집'}</button>{layoutEditing && <div className="map-layout-editor"><ol className="map-layout-guide"><li>선은 칸의 한 면에 붙어 표시됩니다.</li><li>벽 그리기·문 그리기·지우기 중 하나를 고르세요.</li><li>격자선 위를 누른 채 끌면 지나간 선분에 적용됩니다.</li></ol><div className="map-boundary-tools" role="group" aria-label="벽과 문 그리기 도구"><button type="button" aria-pressed={boundaryTool === 'WALL'} onClick={() => setBoundaryTool('WALL')}>벽 그리기</button><button type="button" aria-pressed={boundaryTool === 'DOOR'} onClick={() => setBoundaryTool('DOOR')}>문 그리기</button><button type="button" aria-pressed={boundaryTool === 'ERASE'} onClick={() => setBoundaryTool('ERASE')}>지우기</button></div>{tacticalMap}<p>칸은 이동하거나 선택되지 않습니다.</p><div className="map-layout-actions"><button type="button" disabled={layoutSaving} onClick={() => { boundaryStroke.current = null; setBoundaryPreview(null); const restored = layoutBeforeEdit; setMap(restored); setLayoutSaved(restored?.layers?.some(layer => layer.type === 'MAP_LAYOUT_CONFIRMED') ?? false); setLayoutDirty(false); setLayoutEditing(false); setLayoutBeforeEdit(null) }}>편집 취소</button><button type="button" disabled={layoutSaving} onClick={() => void saveLayout()}>{layoutSaving ? '저장 중…' : '맵 초안 저장'}</button></div></div>}</>}</section>}
      {!preparationMode && tacticalMap}
      {map?.tokens?.filter(token => token.type !== 'PLAYER' && !token.lastSeen && map.current?.some(cell => cell.x === token.x && cell.y === token.y)).map(token => <button key={`target-${token.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'TARGET', { x: token.x, y: token.y }, token.id)) }}>대상 선택: {token.type}</button>)}
      {map?.objects?.filter(object => map.current?.some(cell => cell.x === object.x && cell.y === object.y)).map(object => <button key={`object-${object.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'INTERACT', { x: object.x, y: object.y }, object.id)) }}>상호작용: {object.type}</button>)}
      {candidate && <div role="dialog" aria-label="맵 행동 확인"><p>{candidate.action === 'MOVE' && candidate.from && candidate.to ? `이동: (${candidate.from.x},${candidate.from.y}) → (${candidate.to.x},${candidate.to.y})` : `맵 행동: ${candidate.action}`}</p><button type="button" disabled={submitting} onClick={() => void confirm()}>확인</button><button type="button" disabled={submitting} onClick={() => { setCandidate(null); setSelectedToken(null) }}>취소</button></div>}
      <p role="status">{message}</p>
      {preparationMode && <button type="button" disabled={layoutSaving || layoutDirty || !gridConfirmed || !layoutSaved} onClick={onPreparationComplete}>맵 준비 완료, 모험 시작</button>}
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

function isPlayableGridCell(map: CombatMapState | null, grid: { originX?: number; originY?: number; cellSize?: number }, cell: { x: number; y: number }) {
  const crop = map?.layers?.find(layer => layer.type === 'MAP_CROP')?.value?.split(',').map(Number)
  if (!crop || crop.length !== 4 || !crop.every(Number.isFinite) || crop[2] <= 0 || crop[3] <= 0) return true
  const cellSize = grid.cellSize ?? 0
  if (!Number.isFinite(cellSize) || cellSize <= 0) return true
  const centerX = (grid.originX ?? 0) + (cell.x + .5) * cellSize
  const centerY = (grid.originY ?? 0) + (cell.y + .5) * cellSize
  return centerX >= crop[0] && centerX < crop[0] + crop[2] && centerY >= crop[1] && centerY < crop[1] + crop[3]
}

function playableGridWindow(map: CombatMapState | null, grid: { width: number; height: number; originX?: number; originY?: number; cellSize?: number }, fallback: { width: number; height: number }) {
  const playable = Array.from({ length: grid.width * grid.height }, (_, index) => ({ x: index % grid.width, y: Math.floor(index / grid.width) }))
    .filter(cell => isPlayableGridCell(map, grid, cell))
  if (playable.length === 0 || playable.length === grid.width * grid.height) return null
  const minX = Math.min(...playable.map(cell => cell.x))
  const maxX = Math.max(...playable.map(cell => cell.x))
  const minY = Math.min(...playable.map(cell => cell.y))
  const maxY = Math.max(...playable.map(cell => cell.y))
  // A malformed crop must not collapse the player map. Only trim a rectangular
  // region when every cell in that region is part of the playable map.
  const width = maxX - minX + 1
  const height = maxY - minY + 1
  if (playable.length !== width * height || width > fallback.width || height > fallback.height) return null
  return { minX, minY, width, height }
}

function boundariesFrom(map: CombatMapState | null): MapBoundary[] {
  const stored = map?.layers?.find(layer => layer.type === 'MAP_BOUNDARIES')?.value
  const fromLayer = stored ? stored.split(';').flatMap(value => {
    const [x, y, orientation, kind] = value.split(',')
    return Number.isInteger(Number(x)) && Number.isInteger(Number(y)) && (orientation === 'HORIZONTAL' || orientation === 'VERTICAL') && (kind === 'WALL' || kind === 'DOOR')
      ? [{ x: Number(x), y: Number(y), orientation, kind, open: value.split(',')[4] === 'true' } as MapBoundary] : []
  }) : []
  const legacy = [...(map?.obstacles ?? []).map(position => ({ ...position, orientation: 'HORIZONTAL' as const, kind: 'WALL' as const, open: false })), ...(map?.doors ?? []).map(position => ({ x: position.x, y: position.y, orientation: 'HORIZONTAL' as const, kind: 'DOOR' as const, open: position.open }))]
  return [...fromLayer, ...legacy.filter(item => !fromLayer.some(existing => existing.x === item.x && existing.y === item.y && existing.orientation === item.orientation))]
}

function applyBoundaryProposal(map: CombatMapState, proposal: MapBoundaryProposal): CombatMapState {
  const layers = (map.layers ?? []).filter(layer => !['MAP_BOUNDARIES', 'MAP_CROP', 'MAP_LAYOUT_CONFIRMED'].includes(layer.type))
  const nextLayers = [...layers]
  const boundaries = boundariesFrom(map)
  for (const boundary of proposal.boundaries) {
    if (!boundaries.some(existing => sameBoundarySide(existing, boundary))) boundaries.push(boundary)
  }
  if (boundaries.length) nextLayers.push({ type: 'MAP_BOUNDARIES', value: boundaries.map(encodeBoundary).join(';'), visibility: 'PLAYER_VISIBLE' })
  const existingCrop = map.layers?.find(layer => layer.type === 'MAP_CROP')?.value ?? ''
  const nextCrop = proposal.crop || existingCrop
  if (nextCrop) nextLayers.push({ type: 'MAP_CROP', value: nextCrop, visibility: 'PLAYER_VISIBLE' })
  const obstacles = [...(map.obstacles ?? [])]
  for (const obstacle of proposal.obstacles) if (!obstacles.some(value => value.x === obstacle.x && value.y === obstacle.y)) obstacles.push(obstacle)
  const doors = [...(map.doors ?? [])]
  for (const door of proposal.doors) if (!doors.some(value => value.x === door.x && value.y === door.y)) doors.push(door)
  return { ...map, version: proposal.mapVersion, obstacles, doors, layers: nextLayers }
}

function encodeBoundary(boundary: MapBoundary) { return `${boundary.x},${boundary.y},${boundary.orientation},${boundary.kind},${boundary.open}` }
function layoutConfirmedForAlignment(map: CombatMapState, current: { version: number }) {
  const marker = map.layers?.find(layer => layer.type === 'MAP_LAYOUT_CONFIRMED')
  return marker?.value === `USER|ALIGNMENT_VERSION=${current.version}`
}
type BoundaryGeometry = { orientation: MapBoundary['orientation']; fixed: number; from: number; to: number }
type BoundaryStroke = BoundaryGeometry & { tool: 'WALL' | 'DOOR' | 'ERASE'; originalBoundaries: MapBoundary[] }

function startBoundaryStroke(event: React.PointerEvent<HTMLDivElement>, width: number, height: number): BoundaryGeometry | null {
  const rect = event.currentTarget.getBoundingClientRect()
  const x = Math.max(0, Math.min(width, ((event.clientX - rect.left) / rect.width) * width))
  const y = Math.max(0, Math.min(height, ((event.clientY - rect.top) / rect.height) * height))
  const verticalDistance = Math.abs(x - Math.round(x))
  const horizontalDistance = Math.abs(y - Math.round(y))
  if (Math.min(verticalDistance, horizontalDistance) > .28) return null
  return verticalDistance <= horizontalDistance
    ? { orientation: 'VERTICAL', fixed: Math.round(x), from: Math.min(height - 1, Math.floor(y)), to: Math.min(height - 1, Math.floor(y)) }
    : { orientation: 'HORIZONTAL', fixed: Math.round(y), from: Math.min(width - 1, Math.floor(x)), to: Math.min(width - 1, Math.floor(x)) }
}

function extendBoundaryStroke(stroke: BoundaryGeometry, event: React.PointerEvent<HTMLDivElement>, width: number, height: number): BoundaryGeometry {
  const rect = event.currentTarget.getBoundingClientRect()
  const coordinate = stroke.orientation === 'HORIZONTAL'
    ? Math.max(0, Math.min(width - 1, Math.floor(((event.clientX - rect.left) / rect.width) * width)))
    : Math.max(0, Math.min(height - 1, Math.floor(((event.clientY - rect.top) / rect.height) * height)))
  return { ...stroke, to: coordinate }
}

export function boundariesInStroke(stroke: BoundaryGeometry): Array<Pick<MapBoundary, 'x' | 'y' | 'orientation'>> {
  return Array.from({ length: Math.abs(stroke.to - stroke.from) + 1 }, (_, index) => {
    const variable = Math.min(stroke.from, stroke.to) + index
    return stroke.orientation === 'HORIZONTAL'
      ? { x: variable, y: stroke.fixed, orientation: 'HORIZONTAL' as const }
      : { x: stroke.fixed, y: variable, orientation: 'VERTICAL' as const }
  })
}

function boundaryStyle(boundary: Pick<MapBoundary, 'x' | 'y' | 'orientation'>, width: number, height: number): CSSProperties {
  return boundary.orientation === 'HORIZONTAL'
    ? { left: `${(boundary.x / width) * 100}%`, top: boundary.y === height ? 'calc(100% - 5px)' : `${(boundary.y / height) * 100}%`, width: `${100 / width}%`, height: '5px' }
    : { left: boundary.x === width ? 'calc(100% - 5px)' : `${(boundary.x / width) * 100}%`, top: `${(boundary.y / height) * 100}%`, width: '5px', height: `${100 / height}%` }
}

function sameBoundarySide(left: Pick<MapBoundary, 'x' | 'y' | 'orientation'>, right: Pick<MapBoundary, 'x' | 'y' | 'orientation'>) {
  return left.x === right.x && left.y === right.y && left.orientation === right.orientation
}

function boundaryStrokeStyle(stroke: BoundaryGeometry, width: number, height: number): CSSProperties {
  const start = Math.min(stroke.from, stroke.to)
  const length = Math.abs(stroke.to - stroke.from) + 1
  return stroke.orientation === 'HORIZONTAL'
    ? { ...boundaryStyle({ x: start, y: stroke.fixed, orientation: 'HORIZONTAL' }, width, height), width: `${(length / width) * 100}%` }
    : { ...boundaryStyle({ x: stroke.fixed, y: start, orientation: 'VERTICAL' }, width, height), height: `${(length / height) * 100}%` }
}
