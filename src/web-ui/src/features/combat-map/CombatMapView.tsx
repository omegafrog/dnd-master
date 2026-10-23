import { useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState, CombatTokenPlacement, CombatTokenTransition, ExplorationCycle, ExplorationGroup, MapBoundary, MapBoundaryProposal, MapMovementResult } from '../saved-adventures/AdventurePlayApi'
import { actionCandidate, moveCandidate, type GridCell, type MapInteractionCandidate } from './MapInteractionCandidate'
import { MapGridAlignmentEditor } from './MapGridAlignmentEditor'
import { MapCropEditor } from './MapCropEditor'
import { tokenVisualCatalog } from './TokenVisualCatalog'
import { screenPointToGrid } from './mapScreenToGrid'
import { diagnosticEvent } from '../../shared/developmentDiagnostics'
import type { CombatAimPreview } from '../combat/CombatApi'

export type CombatAimMapInteraction = {
  definitionId: string
  preview?: CombatAimPreview
  onHover: (cell: { x: number; y: number }, mapVersion: number, tokenId: string) => void
  onSelect: (cell: { x: number; y: number }, mapVersion: number, tokenId: string) => void
}

type PendingMovement = { mapId: string; tokenId: string; turnId?: string; commandId?: string; cancelCommandId?: string; result: MapMovementResult }
type PendingMovementCommand = { candidate: MapInteractionCandidate; turnId: string; commandId: string; expectedVersion: number }
type MovementStroke = { base: MapInteractionCandidate; cells: GridCell[] }

function pendingMovementKey(adventureId: string) { return `dnd-master:movement-operation:${adventureId}` }
function pendingMovementCommandKey(adventureId: string) { return `dnd-master:movement-command:${adventureId}` }

function readPendingMovement(adventureId: string): PendingMovement | null {
  try {
    const value = window.localStorage.getItem(pendingMovementKey(adventureId))
    return value ? JSON.parse(value) as PendingMovement : null
  } catch { return null }
}

function readPendingMovementCommand(adventureId: string): PendingMovementCommand | null {
  try {
    const value = window.localStorage.getItem(pendingMovementCommandKey(adventureId))
    return value ? JSON.parse(value) as PendingMovementCommand : null
  } catch { return null }
}

function movementStatusMessage(status: MapMovementResult['status']) {
  return status === 'RETRY_REQUIRED' ? '이동 처리를 다시 시도할 수 있습니다.' : '이동 처리를 이어갈 수 있습니다.'
}

function createMapCommandIdentity() {
  const value = globalThis.crypto && 'randomUUID' in globalThis.crypto ? globalThis.crypto.randomUUID() : `${Date.now()}-${Math.random()}`
  return { turnId: value, commandId: value }
}

function logMapZoom(previous: number, zoom: number, outcome: 'changed' | 'reset') {
  const environment = (import.meta as ImportMeta & { readonly env: { readonly DEV: boolean; readonly VITE_ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED?: string } }).env
  if (environment.DEV && environment.VITE_ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED === 'true') {
    diagnosticEvent('combat_map_zoom', { outcome, previous, zoom })
  }
}

export function CombatMapView({ adventureId, api, refreshToken = 0, compact = false, preparationMode = false, movementRemaining, onPreparationComplete, onPreparationLoadError, aimInteraction }: { adventureId: string; api: AdventurePlayApi; refreshToken?: number; compact?: boolean; preparationMode?: boolean; movementRemaining?: number; onPreparationComplete?: () => void | Promise<void>; onPreparationLoadError?: () => void; aimInteraction?: CombatAimMapInteraction }) {
  const [map, setMap] = useState<CombatMapState | null>(null)
  const [explorationGroups, setExplorationGroups] = useState<ExplorationGroup[]>([])
  const [explorationCycle, setExplorationCycle] = useState<ExplorationCycle | null>(null)
  const [explorationVersion, setExplorationVersion] = useState<number | null>(null)
  const [tokenTransition, setTokenTransition] = useState<CombatTokenTransition | null>(null)
  const [placementTargetId, setPlacementTargetId] = useState<string | null>(null)
  const [manualPlacements, setManualPlacements] = useState<Record<string, { x: number; y: number }>>({})
  const [explorationBusy, setExplorationBusy] = useState(false)
  const [awaitingRuntimeTurnCommit, setAwaitingRuntimeTurnCommit] = useState(false)
  const previousRefreshToken = useRef(refreshToken)
  const runtimeTurnExpectedVersion = useRef<number | null>(null)
  const [splitMemberIds, setSplitMemberIds] = useState<Record<string, string>>({})
  const [publicMapImage, setPublicMapImage] = useState<string | null>(null)
  const [selectedToken, setSelectedToken] = useState<string | null>(null)
  const [naturalMovementText, setNaturalMovementText] = useState('')
  const [candidate, setCandidate] = useState<MapInteractionCandidate | null>(() => readPendingMovementCommand(adventureId)?.candidate ?? null)
  const [message, setMessage] = useState('')
  const movementStroke = useRef<MovementStroke | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [waypointMode, setWaypointMode] = useState(false)
  const [movementHoverEnabled, setMovementHoverEnabled] = useState(false)
  const [mapZoom, setMapZoom] = useState(1)
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
  const [preparationStarting, setPreparationStarting] = useState(false)
  const [mapSetupChoice, setMapSetupChoice] = useState<'review' | 'edit' | 'use'>('review')
  const [selectedPlayerStart, setSelectedPlayerStart] = useState<{ x: number; y: number } | null>(null)
  const boundaryStroke = useRef<BoundaryStroke | null>(null)
  const previewSequence = useRef(0)
  const reportedGroupTokenProjection = useRef<string | null>(null)
  const [boundaryPreview, setBoundaryPreview] = useState<BoundaryStroke | null>(null)
  const [pendingMovement, setPendingMovement] = useState<PendingMovement | null>(() => readPendingMovement(adventureId))
  const [replayedMovement, setReplayedMovement] = useState<MapMovementResult | null>(null)

  useEffect(() => () => {
    if (publicMapImage?.startsWith('blob:')) URL.revokeObjectURL(publicMapImage)
  }, [publicMapImage])

  useEffect(() => {
    let active = true
    const started = performance.now()
    if (preparationMode) diagnosticEvent('combat_map_preparation_load', { outcome: 'started', adventureId })
    void (async () => {
      try {
        const fetchedMap = preparationMode
          ? await (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId))
          : await api.getCombatMap(adventureId)
        // The player map is owned by the current tactical situation. A
        // prepared draft is never substituted into the runtime view before
        // that situation activates its map relation.
        const nextMap = fetchedMap
        if (!active) return
        setMap(nextMap)
        if (preparationMode) diagnosticEvent('combat_map_preparation_load', { outcome: 'completed', adventureId, mapId: nextMap.mapId ?? '', elapsedMs: Math.round(performance.now() - started) })
        const savedMovement = readPendingMovement(adventureId)
        let serverMovement: PendingMovement | null = null
        let terminalMovement: MapMovementResult | null = null
        const playerTokenId = nextMap.tokens?.find(token => token.type === 'PLAYER')?.id
        if (!preparationMode && api.getPendingMapMovement) {
          try {
            const pending = await api.getPendingMapMovement(adventureId)
            if (pending) {
              const currentToken = nextMap.tokens?.find(token => token.id === pending.tokenId)
              const path = pending.path?.length ? pending.path : (currentToken && pending.destination ? [{ x: currentToken.x, y: currentToken.y }, pending.destination] : [])
              setCandidate({ mapId: pending.mapId, mapVersion: pending.mapVersion, tokenId: pending.tokenId,
                action: 'MOVE', from: path[0], to: path[path.length - 1], path, distance: pending.distance,
                fingerprint: pending.fingerprint, waypoints: pending.waypoints, sourceText: pending.sourceText, pendingTurnId: pending.pendingTurnId,
                commandId: pending.confirmationCommandId, terminal: pending.terminal })
            }
          } catch {
            // local candidate state remains a best-effort fallback.
          }
        }
        if (!preparationMode && nextMap.mapId && playerTokenId && api.latestMovementOperation) {
          try {
            const result = await api.latestMovementOperation(adventureId, nextMap.mapId)
            if (result && (result.status === 'RETRY_REQUIRED' || result.status === 'CHECK_REQUIRED')) {
              serverMovement = { mapId: nextMap.mapId, tokenId: playerTokenId,
                cancelCommandId: savedMovement?.result.operationId === result.operationId
                  ? savedMovement?.cancelCommandId : createMapCommandIdentity().commandId, result }
            } else if (result && (result.status === 'COMMITTED' || result.status === 'INTERRUPTED' || result.status === 'CANCELLED')) {
              terminalMovement = result
            }
          } catch {
            // The map remains usable; a later explicit recovery action can query by operation ID.
          }
        }
        const restoredMovement = serverMovement ?? savedMovement
        if (restoredMovement) {
          setPendingMovement(restoredMovement)
          setMessage(`${movementStatusMessage(restoredMovement.result.status)} 작업 번호: ${restoredMovement.result.operationId ?? '없음'}`)
        }
        if (terminalMovement && nextMap.mapId && playerTokenId) {
          const first = terminalMovement.traversedPath[0] ?? terminalMovement.requestedPath[0]
          const replayStart = first ? { ...nextMap, tokens: nextMap.tokens?.map(token => token.id === playerTokenId
            ? { ...token, x: first.x, y: first.y } : token) } : nextMap
          await applyMovementResult(terminalMovement, nextMap.mapId, playerTokenId, undefined, undefined, replayStart, nextMap)
        }
        setSelectedPlayerStart(nextMap.playerStartCandidates?.find(candidate => candidate.source === 'USER_CONFIRMED') ?? null)
        if (preparationMode) setLayoutSaved(false)
        const bounds = nextMap.layers?.find(layer => layer.type === 'GRID_BOUNDS')?.value?.split(',').map(Number)
        const nextGrid = nextMap.grid ?? { width: 20, height: 20 }
        if (bounds?.length === 6 && bounds.every(Number.isFinite)) {
          setAlignment({ mapId: nextMap.mapId ?? '', version: 0, imageRevision: '', cellSize: bounds[2] / nextGrid.width, originX: bounds[0], originY: bounds[1] })
        }
        try {
          let image: string | null = null
          if (preparationMode) {
            // A newly prepared map has no public image view yet. If the
            // owner-only source endpoint is temporarily unavailable, use the
            // already published image instead of leaving the crop step empty.
            if (api.getCombatMapPreparationImage) {
              try { image = await api.getCombatMapPreparationImage(adventureId) }
              catch { image = null }
            }
            if (!image && api.getPublicMapImage) {
              try { image = await api.getPublicMapImage(adventureId) }
              catch { image = null }
            }
          } else if (api.getPublicMapImage) {
            try { image = await api.getPublicMapImage(adventureId) }
            catch { image = null }
          }
          // The reviewed map can be shown before a combat image view is
          // materialized. In that case the alignment response has no image
          // identifier yet, but the preparation image is still authoritative.
          if (!image && !preparationMode && api.getCombatMapPreparationImage) {
            image = await api.getCombatMapPreparationImage(adventureId)
          }
          if (!active) {
            if (image?.startsWith('blob:')) URL.revokeObjectURL(image)
            return
          }
          setPublicMapImage(image)
        } catch {
          if (active) { setPublicMapImage(null); setGridMessage('공개된 지도 이미지를 불러오지 못했습니다.') }
        }
        try { const current = await (api.getMapGridAlignment?.(adventureId) ?? Promise.reject(new Error('unavailable'))); if (active) { setAlignment(current); setAlignmentAvailable(true); if (current.version > 0) setGridConfirmed(true); if (preparationMode) setLayoutSaved(layoutConfirmedForAlignment(nextMap, current)); diagnosticEvent('combat_map_alignment_restore', { outcome: current.version > 0 ? 'restored' : 'not_saved', version: current.version, gridConfirmed: current.version > 0 }) } } catch { if (active) { setAlignmentAvailable(false); setLayoutSaved(false); setGridMessage('저장된 격자 정렬을 불러오지 못했습니다.'); diagnosticEvent('combat_map_alignment_restore', { outcome: 'failed', version: null, gridConfirmed: false }) } }
      } catch (error) {
        if (active) {
          setMap(null)
          setPublicMapImage(null)
          if (preparationMode) {
            diagnosticEvent('combat_map_preparation_load', { outcome: 'failed', adventureId, failureClass: error instanceof Error ? error.name : 'UnknownError', elapsedMs: Math.round(performance.now() - started), recoveryAction: 'offer_manual_resume' })
            onPreparationLoadError?.()
          }
        }
      }
    })()
    return () => { active = false }
  // Loading is keyed by external inputs. Movement application deliberately
  // stays inside this request lifecycle and must not retrigger the load.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adventureId, api, onPreparationLoadError, preparationMode, refreshToken])

  useEffect(() => {
    if (preparationMode || !api.getExplorationGroups) return
    const committedTurnRefresh = previousRefreshToken.current !== refreshToken
    previousRefreshToken.current = refreshToken
    let active = true
    void api.getExplorationGroups(adventureId).then(result => {
      if (active) {
        setExplorationGroups(result.groups)
        setExplorationVersion(result.version)
        setExplorationCycle(result.cycle ?? null)
        setTokenTransition(result.tokenTransition ?? null)
        if (result.tokenTransition?.status === 'PLACEMENT_REQUIRED') {
          setManualPlacements(Object.fromEntries(result.tokenTransition.placements.map(({ targetId, x, y }) => [targetId, { x, y }])))
        } else {
          setManualPlacements({})
          setPlacementTargetId(null)
        }
        if (committedTurnRefresh && runtimeTurnExpectedVersion.current !== null) {
          const expectedVersion = runtimeTurnExpectedVersion.current
          if (result.version > expectedVersion) {
            runtimeTurnExpectedVersion.current = null
            setAwaitingRuntimeTurnCommit(false)
            diagnosticEvent('exploration_cycle_runtime_turn_wait', { outcome: 'completed', expectedVersion, actualVersion: result.version })
          } else {
            diagnosticEvent('exploration_cycle_runtime_turn_wait', { outcome: 'waiting', expectedVersion, actualVersion: result.version })
          }
        }
      }
    }).catch(error => {
      if (active) {
        setMessage(error instanceof Error ? error.message : '탐험 그룹을 불러오지 못했습니다.')
        if (committedTurnRefresh && runtimeTurnExpectedVersion.current !== null) {
          diagnosticEvent('exploration_cycle_runtime_turn_wait', {
            outcome: 'failed', expectedVersion: runtimeTurnExpectedVersion.current,
            failureClass: error instanceof Error ? error.name : 'UnknownError', recoveryAction: 'keep_cycle_controls_disabled',
          })
        }
      }
    })
    return () => { active = false }
  }, [adventureId, api, preparationMode, refreshToken])

  async function saveGroups(next: ExplorationGroup[]) {
    if (!api.saveExplorationGroups || explorationVersion === null || explorationBusy) return
    const started = performance.now()
    setExplorationBusy(true)
    diagnosticEvent('exploration_groups_save', { outcome: 'started', groupCount: next.length, memberCount: next.reduce((sum, group) => sum + group.members.length, 0) })
    try {
      const saved = await api.saveExplorationGroups(adventureId, explorationVersion, next.map(group => ({
        id: group.id,
        members: group.members.map(({ characterSheetId, formation }) => ({ characterSheetId, formation })),
      })))
      setExplorationGroups(saved.groups)
      setExplorationVersion(saved.version)
      setMap(await api.getCombatMap(adventureId))
      setMessage('탐험 그룹과 대열을 저장했습니다.')
      diagnosticEvent('exploration_groups_save', { outcome: 'completed', groupCount: saved.groups.length, elapsedMs: Math.round(performance.now() - started) })
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '탐험 그룹을 저장하지 못했습니다.')
      diagnosticEvent('exploration_groups_save', { outcome: 'failed', groupCount: next.length, failureClass: error instanceof Error ? error.name : 'UnknownError', elapsedMs: Math.round(performance.now() - started) })
    } finally { setExplorationBusy(false) }
  }

  async function updateExplorationCycle(action: () => Promise<{ version: number; groups: ExplorationGroup[]; cycle?: ExplorationCycle | null }>) {
    if (explorationBusy || submitting || awaitingRuntimeTurnCommit) return
    const started = performance.now()
    setExplorationBusy(true)
    diagnosticEvent('exploration_cycle_ui', { outcome: 'started', phase: explorationCycle?.phase ?? 'NOT_STARTED', elapsedSeconds: explorationCycle?.elapsedSeconds ?? 0 })
    try {
      const updated = await action()
      setExplorationVersion(updated.version)
      setExplorationGroups(updated.groups)
      setExplorationCycle(updated.cycle ?? null)
      setTokenTransition(updated.tokenTransition ?? null)
      setMap(await api.getCombatMap(adventureId))
      diagnosticEvent('exploration_cycle_ui', { outcome: 'completed', phase: updated.cycle?.phase ?? 'NOT_STARTED', elapsedSeconds: updated.cycle?.elapsedSeconds ?? 0, elapsedMs: Math.round(performance.now() - started) })
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '탐험 진행을 저장하지 못했습니다.')
      diagnosticEvent('exploration_cycle_ui', { outcome: 'failed', phase: explorationCycle?.phase ?? 'NOT_STARTED', elapsedSeconds: explorationCycle?.elapsedSeconds ?? 0, failureClass: error instanceof Error ? error.name : 'UnknownError', elapsedMs: Math.round(performance.now() - started) })
    } finally { setExplorationBusy(false) }
  }

  const currentExplorationGroupId = explorationCycle?.phase === 'GROUP_TURNS' && !explorationCycle.pendingMovementEvent
    ? explorationCycle.groupOrder.find(id => !explorationCycle.completedGroups.includes(id)) ?? null
    : null
  const currentExplorationGroupTokenId = explorationGroups.find(group => group.id === currentExplorationGroupId)?.mapTokenId ?? null
  const placementTargets = tokenTransition?.direction === 'ENTER'
    ? explorationGroups.flatMap(group => group.members.map(member => ({ id: member.characterSheetId, label: `캐릭터 ${member.characterSheetId.slice(0, 8)}` })))
    : tokenTransition?.direction === 'EXIT'
      ? explorationGroups.map(group => ({ id: group.id, label: `그룹 ${group.id.slice(0, 8)}` }))
      : []
  const placementRequired = tokenTransition?.status === 'PLACEMENT_REQUIRED' && placementTargets.length > 0
  const completeManualPlacement = placementTargets.length > 0
    && placementTargets.every(target => manualPlacements[target.id] !== undefined)
    && new Set(placementTargets.map(target => {
      const cell = manualPlacements[target.id]
      return cell ? `${cell.x},${cell.y}` : ''
    })).size === placementTargets.length

  async function submitManualPlacement() {
    if (!api.submitCombatTokenPlacement || explorationVersion === null || !placementRequired || !completeManualPlacement || explorationBusy) return
    const placements: CombatTokenPlacement[] = placementTargets.map(({ id }) => ({ targetId: id, ...manualPlacements[id] }))
    const started = performance.now()
    setExplorationBusy(true)
    diagnosticEvent('combat_token_manual_placement_ui', { outcome: 'started', adventureId, direction: tokenTransition?.direction, targetCount: placements.length })
    try {
      const updated = await api.submitCombatTokenPlacement(adventureId, explorationVersion, placements)
      setExplorationGroups(updated.groups)
      setExplorationVersion(updated.version)
      setExplorationCycle(updated.cycle ?? null)
      setTokenTransition(updated.tokenTransition ?? null)
      if (updated.tokenTransition?.status !== 'PLACEMENT_REQUIRED') setManualPlacements({})
      setMap(await api.getCombatMap(adventureId))
      setMessage(updated.tokenTransition?.status === 'PLACEMENT_REQUIRED' ? '전체 배치를 확인하지 못했습니다. 칸을 다시 선택해 제출하세요.' : '전체 배치를 저장했습니다.')
      diagnosticEvent('combat_token_manual_placement_ui', { outcome: updated.tokenTransition?.status === 'PLACEMENT_REQUIRED' ? 'retry_required' : 'completed', direction: tokenTransition?.direction, targetCount: placements.length, elapsedMs: Math.round(performance.now() - started), recoveryAction: updated.tokenTransition?.status === 'PLACEMENT_REQUIRED' ? 'review_all_positions_and_retry' : 'resume_combat' })
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '파티 배치를 저장하지 못했습니다.')
      diagnosticEvent('combat_token_manual_placement_ui', { outcome: 'failed', direction: tokenTransition?.direction, targetCount: placements.length, failureClass: error instanceof Error ? error.name : 'UnknownError', elapsedMs: Math.round(performance.now() - started), recoveryAction: 'retry_same_pending_transition' })
    } finally { setExplorationBusy(false) }
  }

  useEffect(() => {
    if (!map || !currentExplorationGroupId || !currentExplorationGroupTokenId) return
    const activeToken = map.tokens?.find(token => token.id === currentExplorationGroupTokenId)
    if (!activeToken) return
    const coLocatedPlayerTokenCount = map.tokens?.filter(token => token.type === 'PLAYER'
      && token.x === activeToken.x && token.y === activeToken.y).length ?? 0
    if (coLocatedPlayerTokenCount < 2) return
    const projectionKey = `${map.mapId}:${map.version}:${currentExplorationGroupId}:${currentExplorationGroupTokenId}`
    if (reportedGroupTokenProjection.current === projectionKey) return
    reportedGroupTokenProjection.current = projectionKey
    diagnosticEvent('exploration_group_active_token_projection', {
      outcome: 'started', groupId: currentExplorationGroupId, tokenId: currentExplorationGroupTokenId,
      coLocatedPlayerTokenCount,
    })
    diagnosticEvent('exploration_group_active_token_projection', {
      outcome: 'completed', groupId: currentExplorationGroupId, tokenId: currentExplorationGroupTokenId,
      coLocatedPlayerTokenCount, result: 'active_group_token_selected',
    })
  }, [currentExplorationGroupId, currentExplorationGroupTokenId, map])

  async function chooseCell(cell: { x: number; y: number }) {
    if (!map || !selectedToken) return
    const token = map.tokens?.find(item => item.id === selectedToken)
    if (!token || (token.x === cell.x && token.y === cell.y)) {
      if (locationMode && selectedToken && map.mapId) {
        setCandidate(actionCandidate(map.mapId, map.version ?? 0, selectedToken, 'LOCATION', cell))
      }
      return
    }
    const current = candidate?.action === 'MOVE' && candidate.tokenId === token.id ? candidate : null
    if (current && waypointMode) {
      const waypoints = [...(current.waypoints ?? []), cell]
      if (current.to) await previewMovement(token.id, current.to, waypoints, current)
      return
    }
    const next = moveCandidate(map.mapId ?? '', map.version ?? 0, token.id, { x: token.x, y: token.y }, cell)
    setCandidate(next)
    await previewMovement(token.id, cell, [], next)
  }

  function startMovementStroke(tokenId: string, from: GridCell) {
    if (!map?.mapId || preparationMode || aimInteraction) return
    const base = moveCandidate(map.mapId, map.version ?? 0, tokenId, from, from)
    movementStroke.current = { base, cells: [] }
    setSelectedToken(tokenId)
    setCandidate(base)
    diagnosticEvent('combat_map_movement_stroke', { outcome: 'started', mapId: map.mapId, tokenId, fromX: from.x, fromY: from.y })
  }

  function extendMovementStroke(cell: GridCell) {
    const stroke = movementStroke.current
    if (!stroke || !map || !isPlayableGridCell(map, previewGrid, cell)) return
    const known = !hasVisibilityMetadata || map.current?.some(item => item.x === cell.x && item.y === cell.y)
      || map.explored?.some(item => item.x === cell.x && item.y === cell.y)
    const blocked = map.obstacles?.some(item => item.x === cell.x && item.y === cell.y)
      || map.doors?.some(item => item.x === cell.x && item.y === cell.y && !item.open)
      || map.tokens?.some(item => item.id !== stroke.base.tokenId && item.x === cell.x && item.y === cell.y)
    if (!known || blocked) return
    const cells = appendMovementCell(stroke.cells, cell)
    if (cells === stroke.cells) return
    stroke.cells = cells
    const destination = cells[cells.length - 1]
    setCandidate({ ...stroke.base, to: destination, path: [stroke.base.from!, ...cells], waypoints: cells.slice(0, -1) })
  }

  function finishMovementStroke() {
    const stroke = movementStroke.current
    if (!stroke) return
    movementStroke.current = null
    const destination = stroke.cells[stroke.cells.length - 1]
    if (!destination || !stroke.base.from) {
      setCandidate(null)
      diagnosticEvent('combat_map_movement_stroke', { outcome: 'cancelled', mapId: stroke.base.mapId, tokenId: stroke.base.tokenId, pathCells: 0 })
      return
    }
    const base = moveCandidate(stroke.base.mapId, stroke.base.mapVersion, stroke.base.tokenId, stroke.base.from, destination)
    const waypoints = stroke.cells.slice(0, -1)
    setCandidate({ ...base, path: [base.from!, ...stroke.cells], waypoints })
    diagnosticEvent('combat_map_movement_stroke', { outcome: 'completed', mapId: base.mapId, tokenId: base.tokenId, pathCells: stroke.cells.length, waypoints: waypoints.length, toX: destination.x, toY: destination.y })
    void previewMovement(base.tokenId, destination, waypoints, base)
  }

  async function previewMovement(tokenId: string, destination: { x: number; y: number }, waypoints: { x: number; y: number }[], base: MapInteractionCandidate, sourceMap = map, retryStale = true) {
    if (!sourceMap?.mapId) return
    const startedAt = performance.now()
    const sequence = previewSequence.current + 1
    previewSequence.current = sequence
    setPreviewing(true)
    diagnosticEvent('combat_map_movement_preview', { outcome: 'started', mapId: sourceMap.mapId, tokenId, mapVersion: sourceMap.version ?? 0, toX: destination.x, toY: destination.y, waypoints: waypoints.length })
    try {
      if (!api.previewMapMovement) throw new Error('서버 이동 경로 미리보기를 사용할 수 없습니다.')
      const preview = await api.previewMapMovement(adventureId, { mapId: sourceMap.mapId, mapVersion: sourceMap.version ?? 0, tokenId, destination, waypoints,
        pendingTurnId: base.pendingTurnId, sourceText: base.sourceText })
      if (sequence !== previewSequence.current) return
      setCandidate(current => current === base || (current?.tokenId === tokenId && current?.action === 'MOVE')
        ? { ...base, mapVersion: preview.baseMapVersion, path: preview.orderedPositions, distance: preview.distance, fingerprint: preview.fingerprint, waypoints }
        : current)
      setMessage(`이동 경로를 미리 보았습니다. 거리: ${preview.distance}`)
      diagnosticEvent('combat_map_movement_preview', { outcome: 'completed', mapId: sourceMap.mapId, tokenId, pathCells: preview.orderedPositions.length, distance: preview.distance, elapsedMs: Math.round(performance.now() - startedAt) })
    } catch (error) {
      if (sequence !== previewSequence.current) return
      const status = error && typeof error === 'object' && 'status' in error ? (error as { status?: unknown }).status : undefined
      const code = error && typeof error === 'object' && 'code' in error ? (error as { code?: unknown }).code : undefined
      if (status === 409 && code === 'STALE_MOVEMENT_PROPOSAL' && retryStale && base.to && api.previewMapMovement) {
        try {
          diagnosticEvent('combat_map_movement_preview', { outcome: 'retry_started', mapId: sourceMap.mapId, tokenId, expectedVersion: sourceMap.version ?? 0, failureStatus: 409, failureCode: code, recoveryAction: 'refresh_and_retry' })
          const refreshed = await api.getCombatMap(adventureId)
          setMap(refreshed)
          await previewMovement(tokenId, destination, waypoints, { ...base, mapVersion: refreshed.version ?? 0 }, refreshed, false)
          setMessage('지도 상태가 바뀌었습니다. 최신 이동 경로를 다시 확인했습니다. 다시 확인해주세요.')
          return
        } catch { /* preserve the original request error */ }
      }
      setCandidate(current => current === base || (current?.action === 'MOVE' && current.mapId === base.mapId
        && current.tokenId === tokenId && current.to?.x === destination.x && current.to.y === destination.y) ? null : current)
      diagnosticEvent('combat_map_movement_preview', { outcome: 'failed', mapId: sourceMap.mapId, tokenId, toX: destination.x, toY: destination.y, waypoints: waypoints.length, failureClass: error instanceof Error ? error.name : 'UnknownError', failureStatus: typeof status === 'number' ? status : null, failureCode: typeof code === 'string' ? code : null, recoveryAction: 'show_error', elapsedMs: Math.round(performance.now() - startedAt) })
      setMessage(code === 'EXPLORATION_MOVEMENT_BUDGET_EXCEEDED'
        ? '이번 탐험 차례에 남은 이동량을 초과합니다.'
        : error instanceof Error ? error.message : '이동 경로를 미리 보지 못했습니다.')
    } finally { if (sequence === previewSequence.current) setPreviewing(false) }
  }

  async function confirm() {
    if (!candidate) return
    if (submitting) return
    if (candidate.action === 'MOVE' && movementRemaining !== undefined && candidate.distance !== undefined && candidate.distance > movementRemaining) {
      setMessage(`이동 경로 비용 ${candidate.distance}ft가 남은 이동량 ${movementRemaining}ft를 초과합니다.`)
      return
    }
    if (candidate.action === 'MOVE' && !candidate.terminal && candidate.mapVersion !== (map?.version ?? candidate.mapVersion)) {
      if (candidate.to) await previewMovement(candidate.tokenId, candidate.to, candidate.waypoints ?? [], candidate)
      setMessage('지도 상태가 바뀌었습니다. 최신 이동 경로를 다시 확인해주세요.')
      return
    }
    setSubmitting(true)
    if (candidate.action === 'MOVE' && candidate.pendingTurnId && api.confirmNaturalLanguageMovement) {
      try {
        const command = candidate.commandId
          ? { turnId: candidate.commandId, commandId: candidate.commandId }
          : createMapCommandIdentity()
        const result = await api.confirmNaturalLanguageMovement(adventureId, { pendingTurnId: candidate.pendingTurnId, commandId: command.commandId,
          tokenId: candidate.tokenId, mapVersion: candidate.mapVersion })
        const refreshed = await api.getCombatMap(adventureId)
        await applyMovementResult(result, candidate.mapId, candidate.tokenId, undefined, command.commandId, map, refreshed)
      } catch (error) { setMessage(error instanceof Error ? error.message : '이동 확인을 처리하지 못했습니다.') }
      finally { setSubmitting(false) }
      return
    }
    const command = candidate.commandId
      ? { turnId: candidate.commandId, commandId: candidate.commandId }
      : createMapCommandIdentity()
    const confirmedCandidate = { ...candidate, commandId: command.commandId }
    setCandidate(confirmedCandidate)
    try { window.localStorage.setItem(pendingMovementCommandKey(adventureId), JSON.stringify({
      candidate: confirmedCandidate, turnId: command.turnId, commandId: command.commandId,
      expectedVersion: map?.sessionVersion ?? map?.version ?? 0,
    } satisfies PendingMovementCommand)) } catch { /* replay identity remains in memory */ }
    try {
      if (confirmedCandidate.action === 'INTERACT' && confirmedCandidate.location && api.interactSpatial) {
        const spatial = await api.interactSpatial(adventureId, {
          mapId: confirmedCandidate.mapId, tokenId: confirmedCandidate.tokenId,
          x: confirmedCandidate.location.x, y: confirmedCandidate.location.y,
          expectedVersion: confirmedCandidate.mapVersion, commandId: command.commandId,
        })
        setMap(await api.getCombatMap(adventureId))
        clearPendingMovementCommand(adventureId)
        setCandidate(null); setSelectedToken(null)
        setMessage(spatial.publicEvents.length ? `공개된 결과: ${spatial.publicEvents.join(', ')}` : '상호작용을 처리했습니다.')
        return
      }
      if (!api.submitMapAction) throw new Error('맵 행동 API를 사용할 수 없습니다.')
      const expectedVersion = map?.sessionVersion ?? map?.version ?? 0
      runtimeTurnExpectedVersion.current = expectedVersion
      setAwaitingRuntimeTurnCommit(true)
      diagnosticEvent('exploration_cycle_runtime_turn_wait', { outcome: 'started', expectedVersion })
      const turn = await api.submitMapAction(adventureId, {
        mapId: confirmedCandidate.mapId, mapVersion: confirmedCandidate.mapVersion, tokenId: confirmedCandidate.tokenId,
        action: confirmedCandidate.action, path: confirmedCandidate.action === 'MOVE' ? (confirmedCandidate.path ?? (confirmedCandidate.from && confirmedCandidate.to ? gridPath(confirmedCandidate.from, confirmedCandidate.to) : undefined)) : undefined,
        targetId: confirmedCandidate.targetId, location: confirmedCandidate.location ?? confirmedCandidate.to,
        waypoints: confirmedCandidate.action === 'MOVE' ? (confirmedCandidate.waypoints ?? []) : undefined,
        fingerprint: confirmedCandidate.action === 'MOVE' ? confirmedCandidate.fingerprint : undefined,
      }, command, map?.sessionVersion ?? map?.version ?? 0)
      const refreshed = await api.getCombatMap(adventureId)
      if (candidate.action === 'MOVE' && turn.movementResult) {
        await applyMovementResult(turn.movementResult, confirmedCandidate.mapId, confirmedCandidate.tokenId, turn.turnId, command.commandId, map, refreshed)
        if (turn.movementResult.status !== 'RETRY_REQUIRED' && turn.movementResult.status !== 'CHECK_REQUIRED') {
          runtimeTurnExpectedVersion.current = null
          setAwaitingRuntimeTurnCommit(false)
          diagnosticEvent('exploration_cycle_runtime_turn_wait', { outcome: 'completed', expectedVersion, actualVersion: turn.version })
        }
      } else {
        setMap(refreshed)
        clearPendingMovementCommand(adventureId)
        setCandidate(null); setSelectedToken(null); setMessage('맵 행동을 GM 턴으로 전송했습니다.')
      }
    } catch (error) {
      if (runtimeTurnExpectedVersion.current !== null) {
        diagnosticEvent('exploration_cycle_runtime_turn_wait', {
          outcome: 'failed', expectedVersion: runtimeTurnExpectedVersion.current,
          failureClass: error instanceof Error ? error.name : 'UnknownError', recoveryAction: 'wait_for_commit_refresh_or_reload',
        })
      }
      // The runtime can commit the map command before the HTTP request sees
      // a concurrent-version response. Reconcile that response with the
      // authoritative map before showing an error to the player.
      const status = error && typeof error === 'object' && 'status' in error ? (error as { status?: unknown }).status : undefined
      const code = error && typeof error === 'object' && 'code' in error ? (error as { code?: unknown }).code : undefined
      if (status === 409 && code === 'STALE_MOVEMENT_PROPOSAL' && candidate.action === 'MOVE' && candidate.to && api.previewMapMovement) {
        try {
          const refreshed = await api.getCombatMap(adventureId)
          const moved = refreshed.tokens?.some(token => token.id === candidate.tokenId && token.x === candidate.to?.x && token.y === candidate.to?.y)
          setMap(refreshed)
          if (moved) {
            setCandidate(null); setSelectedToken(null); setMessage('맵 이동이 반영되었습니다.')
            diagnosticEvent('combat_map_stale_movement', { outcome: 'committed_map_reconciled', mapId: candidate.mapId, tokenId: candidate.tokenId, toX: candidate.to.x, toY: candidate.to.y, mapVersion: refreshed.version ?? 0 })
            return
          }
          await previewMovement(candidate.tokenId, candidate.to, candidate.waypoints ?? [], { ...candidate, mapVersion: refreshed.version ?? 0 }, refreshed)
          setMessage('지도 상태가 바뀌었습니다. 최신 이동 경로를 다시 확인하고 확인해주세요.')
          return
        } catch { /* preserve the original request error */ }
      }
      if (status === 409 && code === 'STALE_MOVEMENT_PROPOSAL' && candidate.action === 'MOVE' && candidate.to) {
        try {
          const refreshed = await api.getCombatMap(adventureId)
          const moved = refreshed.tokens?.some(token => token.id === candidate.tokenId && token.x === candidate.to?.x && token.y === candidate.to?.y)
          if (moved) {
            setMap(refreshed); setCandidate(null); setSelectedToken(null); setMessage('맵 이동이 반영되었습니다.')
            return
          }
        } catch { /* preserve the original request error */ }
      }
      setMessage(error instanceof Error ? error.message : '맵 행동을 처리하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  async function previewNaturalMovement(event: FormEvent) {
    event.preventDefault()
    const token = map?.tokens?.find(item => item.type === 'PLAYER')
    if (!map?.mapId || !token || !naturalMovementText.trim() || !api.previewNaturalLanguageMovement) return
    setPreviewing(true)
    try {
      const result = await api.previewNaturalLanguageMovement(adventureId, { mapId: map.mapId, mapVersion: map.version ?? 0, tokenId: token.id, sourceText: naturalMovementText.trim() })
      if (result.status !== 'RESOLVED' || !result.destination) { setCandidate(null); setMessage(result.playerMessage || '목적지를 다시 설명하거나 지도에서 선택해주세요.'); return }
      setSelectedToken(token.id)
      setCandidate({ mapId: map.mapId, mapVersion: result.baseMapVersion ?? map.version ?? 0, tokenId: token.id, action: 'MOVE', from: { x: token.x, y: token.y }, to: result.destination, path: result.path, distance: result.distance, fingerprint: result.fingerprint, waypoints: [], sourceText: naturalMovementText.trim(), pendingTurnId: result.pendingTurnId, commandId: result.confirmationCommandId })
      setMessage('자연어 목적지의 이동 경로를 미리 보았습니다. 확인 전에는 지도 상태가 바뀌지 않습니다.')
    } catch (error) { setMessage(error instanceof Error ? error.message : '자연어 목적지를 해석하지 못했습니다.') }
    finally { setPreviewing(false) }
  }

  async function applyMovementResult(result: MapMovementResult, mapId: string, tokenId: string,
    turnId: string | undefined, commandId: string | undefined, before: CombatMapState | null, refreshed: CombatMapState) {
    if (result.status === 'RETRY_REQUIRED' || result.status === 'CHECK_REQUIRED') {
      const pending = {
        mapId, tokenId, turnId, commandId,
        cancelCommandId: pendingMovement?.result.operationId === result.operationId
          ? pendingMovement?.cancelCommandId ?? createMapCommandIdentity().commandId
          : createMapCommandIdentity().commandId,
        result,
      }
      setMap(refreshed)
      setPendingMovement(pending)
      setCandidate(null); setSelectedToken(null); setWaypointMode(false)
      try { window.localStorage.setItem(pendingMovementKey(adventureId), JSON.stringify(pending)) } catch { /* reconnect is best effort */ }
      setMessage(`${movementStatusMessage(result.status)} 작업 번호: ${result.operationId ?? '없음'}`)
      return
    }
    if (before && result.traversedPath.length > 1) {
      await animateCommittedMovement(setMap, before, refreshed, tokenId, result.traversedPath)
    } else setMap(refreshed)
    setPendingMovement(null)
    if (result.status === 'COMMITTED' || result.status === 'INTERRUPTED' || result.status === 'CANCELLED') setReplayedMovement(result)
    try { window.localStorage.removeItem(pendingMovementKey(adventureId)) } catch { /* storage is optional */ }
    clearPendingMovementCommand(adventureId)
    setCandidate(null); setSelectedToken(null); setMessage(result.status === 'INTERRUPTED' ? '이동이 중단되었습니다.' : '맵 행동을 GM 턴으로 전송했습니다.')
    if (api.getExplorationGroups) {
      const refreshStarted = performance.now()
      diagnosticEvent('exploration_cycle_after_map_movement', {
        outcome: 'started', status: result.status, publicEventCount: result.publicEvents.length,
      })
      try {
        const updated = await api.getExplorationGroups(adventureId)
        setExplorationGroups(updated.groups)
        setExplorationVersion(updated.version)
        setExplorationCycle(updated.cycle ?? null)
        diagnosticEvent('exploration_cycle_after_map_movement', {
          outcome: 'completed', status: result.status, publicEventCount: result.publicEvents.length,
          groupCount: updated.groups.length, cyclePhase: updated.cycle?.phase ?? 'NOT_STARTED',
          pendingMovementEvent: Boolean(updated.cycle?.pendingMovementEvent),
          elapsedMs: Math.round(performance.now() - refreshStarted),
        })
      } catch (error) {
        diagnosticEvent('exploration_cycle_after_map_movement', {
          outcome: 'failed', status: result.status, publicEventCount: result.publicEvents.length,
          groupCount: explorationGroups.length, cyclePhase: explorationCycle?.phase ?? 'NOT_STARTED',
          failureClass: error instanceof Error ? error.name : 'UnknownError',
          elapsedMs: Math.round(performance.now() - refreshStarted), recoveryAction: 'reload_exploration_state',
        })
        throw error
      }
    }
  }

  async function recoverMovement(resume: boolean, check?: { success: boolean }) {
    if (!pendingMovement?.result.operationId) return
    const operationId = pendingMovement.result.operationId
    const operationApi = resume ? api.resumeMovementOperation : api.movementOperation
    if (!operationApi) {
      setMessage('저장된 이동 상태를 확인할 수 없습니다.')
      return
    }
    try {
      const pendingCheck = pendingMovement.result.pendingCheck
      const submission = resume && check && pendingCheck ? {
        commandId: pendingCheck.checkId,
        operationId: pendingCheck.operationId,
        checkId: pendingCheck.checkId,
        success: check.success,
        ownerPlayerId: pendingCheck.ownerPlayerId,
        actor: pendingCheck.actor,
      } : undefined
      const result = submission
        ? await operationApi(adventureId, pendingMovement.mapId, operationId, submission)
        : await operationApi(adventureId, pendingMovement.mapId, operationId)
      const refreshed = await api.getCombatMap(adventureId)
      let effectiveResult = result
      if (resume && pendingMovement.turnId && api.resumeRuntimeTurn) {
        const runtime = await api.resumeRuntimeTurn(adventureId, pendingMovement.turnId, pendingMovement.commandId ?? pendingMovement.turnId)
        effectiveResult = runtime.movementResult ?? result
      }
      await applyMovementResult(effectiveResult, pendingMovement.mapId, pendingMovement.tokenId, pendingMovement.turnId,
        pendingMovement.commandId, map, refreshed)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '저장된 이동 상태를 확인하지 못했습니다.')
    }
  }

  async function submitPendingRoll() {
    const pendingCheck = pendingMovement?.result.pendingCheck
    if (!pendingMovement || !pendingCheck || !api.rollSpatialCheck) return
    try {
      const result = await api.rollSpatialCheck(adventureId,
        map?.sessionVersion ?? map?.version ?? pendingMovement.result.version, {
          mapId: pendingMovement.mapId, operationId: pendingCheck.operationId, checkId: pendingCheck.checkId,
          ownerPlayerId: pendingCheck.ownerPlayerId, actor: pendingCheck.actor,
        })
      let effectiveResult = result
      if (result.status !== 'CHECK_REQUIRED' && pendingMovement.turnId && api.resumeRuntimeTurn) {
        const runtime = await api.resumeRuntimeTurn(adventureId, pendingMovement.turnId, pendingMovement.commandId ?? pendingMovement.turnId)
        effectiveResult = runtime.movementResult ?? result
      }
      const refreshed = await api.getCombatMap(adventureId)
      await applyMovementResult(effectiveResult, pendingMovement.mapId, pendingMovement.tokenId, pendingMovement.turnId,
        pendingMovement.commandId, map, refreshed)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '주사위 결과를 제출하지 못했습니다.')
    }
  }

  async function observeCurrentCell() {
    if (!api.observeSpatial || !map?.mapId) return
    const player = map.tokens?.find(token => token.type === 'PLAYER')
    if (!player) return
    const commandId = createMapCommandIdentity().commandId
    try {
      const result = await api.observeSpatial(adventureId, {
        mapId: map.mapId, tokenId: player.id, x: player.x, y: player.y,
        expectedVersion: map.version ?? 0, commandId,
      })
      if (result.pendingCheck && result.operationId) {
        const pendingResult: MapMovementResult = {
          version: result.mapVersion, operationId: result.operationId, status: 'CHECK_REQUIRED',
          requestedPath: [{ x: player.x, y: player.y }], traversedPath: [{ x: player.x, y: player.y }],
          finalPosition: { x: player.x, y: player.y }, publicEvents: [], pendingCheck: result.pendingCheck,
        }
        const pending = { mapId: result.mapId, tokenId: player.id, result: pendingResult }
        setPendingMovement(pending)
        try { window.localStorage.setItem(pendingMovementKey(adventureId), JSON.stringify(pending)) } catch { /* storage is optional */ }
        setMessage('관찰 판정 확인 필요')
        return
      }
      setMessage(result.publicEvents.length ? `공개된 결과: ${result.publicEvents.join(', ')}` : '특이한 점을 찾지 못했습니다.')
      setMap(await api.getCombatMap(adventureId))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '주변을 살피지 못했습니다.')
    }
  }

  const grid = map?.grid ?? { width: 0, height: 0 }
  const mapImage = publicMapImage
  useEffect(() => {
    const raw = map?.layers?.find(layer => layer.type === 'MAP_CROP')?.value?.split(',').map(Number)
    if (raw?.length === 4 && raw.every(Number.isFinite) && raw[2] > 0 && raw[3] > 0) {
      setCrop({ x: raw[0], y: raw[1], width: raw[2], height: raw[3] })
      if (preparationMode) { setCropConfirmed(true); setCropEditorOpen(false); diagnosticEvent('combat_map_crop_restore', { outcome: 'restored', width: raw[2], height: raw[3] }) }
    } else if (mapImageSize.width > 1 && mapImageSize.height > 1) {
      setCrop({ x: 0, y: 0, width: mapImageSize.width, height: mapImageSize.height })
      if (preparationMode) { setCropConfirmed(false); setCropEditorOpen(true); diagnosticEvent('combat_map_crop_restore', { outcome: 'missing_or_invalid', imageWidth: mapImageSize.width, imageHeight: mapImageSize.height }) }
    }
  }, [map, mapImageSize, preparationMode])
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
    '--map-zoom': mapZoom,
    '--grid-columns': displayedColumns,
    '--grid-rows': displayedRows,
    '--map-aspect': `${Math.max(displayedGridWidth, 1)} / ${Math.max(displayedGridHeight, 1)}`,
    ...(mapImage ? { backgroundImage: `url(${mapImage})` } : {}),
    ...(playableWindow ? {
      '--map-background-size': `${(renderedImageWidth / Math.max(displayedGridWidth, 1)) * 100}% ${(renderedImageHeight / Math.max(displayedGridHeight, 1)) * 100}%`,
      '--map-background-position': `${((previewGrid.originX + playableWindow.minX * previewGrid.cellSize) / Math.max(renderedImageWidth - displayedGridWidth, 1)) * 100}% ${((previewGrid.originY + playableWindow.minY * previewGrid.cellSize) / Math.max(renderedImageHeight - displayedGridHeight, 1)) * 100}%`,
    } : hasGridBounds || gridEditor || usesSavedAlignment ? {
      '--map-background-size': `${(renderedImageWidth / Math.max(renderedGridWidth, 1)) * 100}% ${(renderedImageHeight / Math.max(renderedGridHeight, 1)) * 100}%`,
      '--map-background-position': `${gridEditor ? 'left top' : backgroundPositionX} ${gridEditor ? 'left top' : backgroundPositionY}`,
    } : {}),
    ...(!gridEditor && !preserveAlignmentForLayout && !hasGridBounds && !usesSavedAlignment && crop.width > 0 && crop.height > 0 ? {
      '--map-background-size': `${((imageWidth || mapImageSize.width) / crop.width) * 100}% ${((imageHeight || mapImageSize.height) / crop.height) * 100}%`,
      '--map-background-position': `${(crop.x / Math.max((imageWidth || mapImageSize.width) - crop.width, 1)) * 100}% ${(crop.y / Math.max((imageHeight || mapImageSize.height) - crop.height, 1)) * 100}%`,
    } : {}),
  } as CSSProperties
  const hasVisibilityMetadata = Array.isArray(map?.current) && Array.isArray(map?.explored)
  const mapPreparationPlacementRequired = map?.layers?.some(layer => layer.type === 'MAP_PLACEMENT_STATUS' && layer.value === 'PLACEMENT_REQUIRED') ?? false
  const mapSetupReviewRequired = preparationMode && (map?.layers?.some(layer => layer.type === 'GRID_SOURCE' && layer.value === 'FALLBACK') === true || !gridConfirmed || !layoutSaved)
  const showMapSetupChoice = mapSetupReviewRequired && mapSetupChoice === 'review'
  const showMapSetupEditors = mapSetupReviewRequired && mapSetupChoice === 'edit'
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
        boundaries: mapBoundaries, crop: localCrop, playerStart: selectedPlayerStart ?? undefined,
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
      playerStart: selectedPlayerStart ?? undefined,
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
    const label = stroke.tool === 'ERASE' ? '지웠습니다' : stroke.tool === 'DOOR' ? '문을 놓았습니다' : '벽을 놓았습니다'
    setMessage(`${label} · ${touched.length}개 선분. 맵 초안 저장을 눌러 확정하세요.`)
  }
  async function completePreparation() {
    if (!onPreparationComplete || preparationStarting) return
    setPreparationStarting(true)
    setMessage('맵 준비가 완료되었습니다. 모험을 시작하는 중입니다…')
    try {
      await onPreparationComplete()
      setMessage('모험 시작 요청이 완료되었습니다. 화면을 여는 중입니다…')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '모험을 시작하지 못했습니다.')
    } finally {
      setPreparationStarting(false)
    }
  }
  async function useCurrentMapSetup() {
    if (!map?.mapId || !api.calibrateMapGrid || !api.applyMapGridAlignment || !api.getMapGridAlignment || !api.updateCombatMapLayout
        || !alignmentAvailable || alignment.mapId !== map.mapId || mapImageSize.width < 1 || mapImageSize.height < 1) {
      setMessage('현재 지도 설정을 확인할 수 없습니다. 다시 설정을 선택하세요.')
      return
    }
    setLayoutSaving(true)
    setMessage('저장된 격자와 벽 위치를 현재 모험에서 사용할 설정으로 확인하는 중입니다…')
    diagnosticEvent('combat_map_existing_setup', { outcome: 'confirmation_started', gridWidth: grid.width, gridHeight: grid.height, boundaries: mapBoundaries.length })
    try {
      await api.calibrateMapGrid(adventureId, {
        mapId: map.mapId, expectedVersion: map.version ?? 0, width: grid.width, height: grid.height,
        cellSize: Math.max(1, Math.floor(previewGrid.cellSize)), originX: Math.max(0, Math.floor(previewGrid.originX)),
        originY: Math.max(0, Math.floor(previewGrid.originY)), imageWidth: mapImageSize.width, imageHeight: mapImageSize.height,
      })
      const aligned = await api.applyMapGridAlignment(adventureId, {
        mapId: alignment.mapId, commandId: globalThis.crypto.randomUUID(), expectedVersion: alignment.version,
        imageRevision: alignment.imageRevision, originX: previewGrid.originX, originY: previewGrid.originY, cellSize: previewGrid.cellSize,
      })
      const calibrated = await (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId))
      await api.updateCombatMapLayout(adventureId, {
        commandId: globalThis.crypto.randomUUID(), expectedVersion: calibrated.version ?? 0,
        obstacles: calibrated.obstacles ?? [], doors: calibrated.doors?.map(door => ({ x: door.x, y: door.y })) ?? [],
        boundaries: boundariesFrom(calibrated), crop: calibrated.layers?.find(layer => layer.type === 'MAP_CROP')?.value,
        alignmentVersion: aligned.version, imageRevision: aligned.imageRevision,
      })
      const [savedMap, savedAlignment] = await Promise.all([
        api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId),
        api.getMapGridAlignment(adventureId),
      ])
      setMap(savedMap); setAlignment(savedAlignment); setAlignmentAvailable(true)
      setGridConfirmed(true); setCropConfirmed(true); setLayoutSaved(true); setLayoutDirty(false); setMapSetupChoice('use')
      setMessage('현재 지도와 격자·벽 설정을 확인했습니다. 시작 위치를 확인한 뒤 진행하세요.')
      diagnosticEvent('combat_map_existing_setup', { outcome: 'confirmed', gridWidth: grid.width, gridHeight: grid.height, boundaries: mapBoundaries.length })
    } catch (error) {
      try {
        const [latestMap, latestAlignment] = await Promise.all([
          api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId),
          api.getMapGridAlignment(adventureId),
        ])
        setMap(latestMap); setAlignment(latestAlignment); setAlignmentAvailable(true)
        setGridConfirmed(latestAlignment.version > 0)
        setLayoutSaved(layoutConfirmedForAlignment(latestMap, latestAlignment))
      } catch { /* keep the saved-map choice available with the original failure */ }
      const failure = error instanceof Error ? error.name : 'UnknownError'
      setMessage(error instanceof Error ? error.message : '현재 지도 설정을 확인하지 못했습니다.')
      diagnosticEvent('combat_map_existing_setup', { outcome: 'failed', gridWidth: grid.width, gridHeight: grid.height, boundaries: mapBoundaries.length, failure })
    } finally {
      setLayoutSaving(false)
    }
  }
  const tacticalMap = map.tokens ? (
    <div className="tactical-map-window">
      {(!preparationMode || layoutEditing) && <div className="combat-map-zoom" role="group" aria-label="지도 확대/축소">
        <button type="button" aria-label="지도 축소" disabled={mapZoom <= .75} onClick={() => setMapZoom(current => {
          const next = Math.max(.75, Math.round((current - .25) * 100) / 100)
          logMapZoom(current, next, 'changed')
          return next
        })}>−</button>
        <output aria-label="지도 배율">{Math.round(mapZoom * 100)}%</output>
        <button type="button" aria-label="지도 확대" disabled={mapZoom >= 2} onClick={() => setMapZoom(current => {
          const next = Math.min(2, Math.round((current + .25) * 100) / 100)
          logMapZoom(current, next, 'changed')
          return next
        })}>+</button>
        <button type="button" aria-label="지도 배율 초기화" disabled={mapZoom === 1} onClick={() => {
          logMapZoom(mapZoom, 1, 'reset')
          setMapZoom(1)
        }}>초기화</button>
      </div>}
      {!preparationMode && <button type="button" aria-pressed={movementHoverEnabled} onClick={() => setMovementHoverEnabled(current => !current)} disabled={!selectedToken}>이동 미리 보기</button>}
      {!preparationMode && <button type="button" aria-pressed={locationMode} onClick={() => setLocationMode(current => !current)}>위치 선택</button>}
          <div aria-label="tactical-map" data-map-id={map.mapId} data-version={map.version ?? 0} data-map-zoom={mapZoom} className="tactical-map" style={mapStyle} onPointerDown={event => {
            if (!preparationMode || !layoutEditing) return
            const stroke = startBoundaryStroke(event, previewGrid.width, previewGrid.height)
            if (!stroke) return
            event.currentTarget.setPointerCapture?.(event.pointerId)
            const activeStroke = { ...stroke, tool: boundaryTool, originalBoundaries: mapBoundaries }
            boundaryStroke.current = activeStroke; setBoundaryPreview(activeStroke)
            diagnosticEvent('combat_map_boundary_stroke', { outcome: 'started', mapId: map.mapId, tool: boundaryTool, segments: 1 })
            setMessage(`${boundaryTool === 'DOOR' ? '문' : boundaryTool === 'ERASE' ? '지우기' : '벽'} 미리 보기 · 1개 선분`)
          }} onPointerMove={event => {
            if (movementStroke.current) {
              const cellElement = (event.target as HTMLElement).closest<HTMLButtonElement>('button[data-grid-x][data-grid-y]')
              if (cellElement) extendMovementStroke({ x: Number(cellElement.dataset.gridX), y: Number(cellElement.dataset.gridY) })
              return
            }
            if (!boundaryStroke.current) return
            const next = extendBoundaryStroke(boundaryStroke.current, event, previewGrid.width, previewGrid.height)
            const activeStroke = { ...next, tool: boundaryStroke.current.tool, originalBoundaries: boundaryStroke.current.originalBoundaries }
            boundaryStroke.current = activeStroke; setBoundaryPreview(activeStroke)
            const segments = boundariesInStroke(activeStroke).length
            setMessage(`${activeStroke.tool === 'DOOR' ? '문' : activeStroke.tool === 'ERASE' ? '지우기' : '벽'} 미리 보기 · ${segments}개 선분`)
          }} onPointerUp={event => {
            if (movementStroke.current) { finishMovementStroke(); return }
            const stroke = boundaryStroke.current
            if (!stroke) return
            applyBoundaryStroke(stroke)
            diagnosticEvent('combat_map_boundary_stroke', { outcome: 'placed_locally', mapId: map.mapId, tool: stroke.tool, segments: boundariesInStroke(stroke).length })
            boundaryStroke.current = null; setBoundaryPreview(null)
            if (event.currentTarget.hasPointerCapture?.(event.pointerId)) event.currentTarget.releasePointerCapture?.(event.pointerId)
          }} onPointerCancel={() => { const movement = movementStroke.current; if (movement) { movementStroke.current = null; setCandidate(null); diagnosticEvent('combat_map_movement_stroke', { outcome: 'cancelled', mapId: movement.base.mapId, tokenId: movement.base.tokenId, pathCells: movement.cells.length }) } const stroke = boundaryStroke.current; if (stroke) diagnosticEvent('combat_map_boundary_stroke', { outcome: 'cancelled', mapId: map.mapId, tool: stroke.tool, segments: boundariesInStroke(stroke).length }); boundaryStroke.current = null; setBoundaryPreview(null); if (stroke) setMessage('선분 드래그를 취소했습니다.') }}>
        {Array.from({ length: displayedColumns * displayedRows }, (_, index) => {
          const cell = { x: (playableWindow?.minX ?? 0) + index % displayedColumns, y: (playableWindow?.minY ?? 0) + Math.floor(index / displayedColumns) }
          // A prepared map may contain an AI-generated or legacy player-start
          // suggestion. It is not authoritative and must never be shown as a
          // coordinate the user is expected to confirm.
          const tokensAtCell = (preparationMode ? map.tokens?.filter(item => item.type !== 'PLAYER') : map.tokens)
            ?.filter(item => item.x === cell.x && item.y === cell.y) ?? []
          const token = !preparationMode && currentExplorationGroupTokenId
            ? tokensAtCell.find(item => item.id === currentExplorationGroupTokenId) ?? tokensAtCell[0]
            : tokensAtCell[0]
          const playable = isPlayableGridCell(map, previewGrid, cell)
          const blocked = !preparationMode && (map.obstacles?.some(obstacle => obstacle.x === cell.x && obstacle.y === cell.y)
            || map.doors?.some(item => item.x === cell.x && item.y === cell.y && !item.open))
          const startBlocked = map.obstacles?.some(obstacle => obstacle.x === cell.x && obstacle.y === cell.y)
            || map.doors?.some(item => item.x === cell.x && item.y === cell.y && !item.open)
          const door = !preparationMode ? map.doors?.find(item => item.x === cell.x && item.y === cell.y) : undefined
          const visible = playable && (preparationMode || (map.current?.some(item => item.x === cell.x && item.y === cell.y)
            ?? (!hasVisibilityMetadata && token?.type === 'PLAYER')))
          const explored = playable && (map.explored?.some(item => item.x === cell.x && item.y === cell.y) ?? false)
          const displayToken = token && (visible || (token.lastSeen && explored)) ? token : undefined
          const tokenVisual = displayToken ? tokenVisualCatalog.resolve(displayToken, { selectedTokenId: selectedToken, currentTurnTokenId: map.currentTurnTokenId }) : undefined
          const movementPreview = !preparationMode && candidate?.action === 'MOVE' && candidate.path?.some(position => position.x === cell.x && position.y === cell.y)
          const ghostDestination = movementPreview && candidate?.to?.x === cell.x && candidate.to.y === cell.y
          const aimPath = !preparationMode && (visible || explored) && aimInteraction?.preview?.path.some(position => position.x === cell.x && position.y === cell.y)
          const aimEffect = !preparationMode && (visible || explored) && aimInteraction?.preview?.effectCells.some(position => position.x === cell.x && position.y === cell.y)
          const aimActor = (selectedToken ? map.tokens?.find(item => item.id === selectedToken && item.type === 'PLAYER') : undefined)
            ?? map.tokens?.find(item => item.type === 'PLAYER')
          const aimCell = (event: { currentTarget: HTMLButtonElement; clientX: number; clientY: number }) => {
            const rect = event.currentTarget.parentElement?.getBoundingClientRect()
            if (!rect || !aimInteraction || !aimActor || !map.mapId) return
            if (!Number.isFinite(event.clientX) || !Number.isFinite(event.clientY) || rect.width <= 0 || rect.height <= 0) {
              return { target: cell, tokenId: aimActor.id }
            }
            const point = screenPointToGrid(event.clientX, event.clientY, rect, displayedColumns, displayedRows)
            const target = { x: (playableWindow?.minX ?? 0) + Math.floor(point.x), y: (playableWindow?.minY ?? 0) + Math.floor(point.y) }
            return { target, tokenId: aimActor.id }
          }
          const draftLabel = door ? `${door.open ? '열린 문' : '닫힌 문'} ${cell.x},${cell.y}` : blocked ? `벽 ${cell.x},${cell.y}` : token ? `플레이어 시작 위치 ${cell.x},${cell.y}` : `빈 격자 ${cell.x},${cell.y}`
          return <button key={`${cell.x}-${cell.y}`} type="button" aria-label={placementRequired && placementTargetId ? `배치 ${cell.x},${cell.y}` : preparationMode ? draftLabel : !playable ? '지도 밖 영역' : displayToken ? `${displayToken.type} ${displayToken.x},${displayToken.y}` : visible ? `격자 ${cell.x},${cell.y}` : explored ? `탐험한 격자 ${cell.x},${cell.y}` : '미탐험 영역'} data-grid-x={cell.x} data-grid-y={cell.y} data-visibility={!playable ? 'outside' : visible ? 'current' : explored ? 'explored' : 'hidden'} data-token-id={displayToken?.id} data-token-type={displayToken?.type} data-token-faction={tokenVisual?.faction} data-token-status={tokenVisual?.primaryStatus} data-token-asset={tokenVisual?.assetPath} data-token-frame={tokenVisual?.framePath} data-movement-preview={movementPreview ? 'true' : undefined} data-aim-path={aimPath ? 'true' : undefined} data-aim-effect={aimEffect ? 'true' : undefined} data-ghost-token={ghostDestination ? 'true' : undefined} data-start-selected={preparationMode && selectedPlayerStart?.x === cell.x && selectedPlayerStart?.y === cell.y ? 'true' : undefined} data-placement-selected={placementRequired && placementTargetId && manualPlacements[placementTargetId]?.x === cell.x && manualPlacements[placementTargetId]?.y === cell.y ? 'true' : undefined} data-last-seen={displayToken?.lastSeen ? 'true' : 'false'} className={tokenVisual?.classes.join(' ')} disabled={!playable || startBlocked || (placementRequired && displayToken !== undefined && displayToken.type !== 'PLAYER') || (preparationMode && layoutEditing) || (!preparationMode && !visible && !explored)} onPointerDown={event => { if (!placementRequired && !aimInteraction && !preparationMode && displayToken?.type === 'PLAYER' && event.button !== 2) { event.preventDefault(); startMovementStroke(displayToken.id, cell) } }} onPointerEnter={event => { if (placementRequired) return; if (aimInteraction) { const aim = aimCell(event); if (aim) aimInteraction.onHover(aim.target, map.version ?? 0, aim.tokenId) } else if (movementStroke.current && !displayToken && (visible || explored)) extendMovementStroke(cell); else if (movementHoverEnabled && !preparationMode && selectedToken && displayToken?.type !== 'PLAYER' && (visible || explored)) void chooseCell(cell) }} onFocus={() => { if (!placementRequired && aimInteraction && aimActor && map.mapId) aimInteraction.onHover(cell, map.version ?? 0, aimActor.id) }} onClick={() => { if (placementRequired) { if (placementTargetId) setManualPlacements(current => ({ ...current, [placementTargetId]: { x: cell.x, y: cell.y } })) } else if (aimInteraction) { if (aimActor && map.mapId) aimInteraction.onSelect(cell, map.version ?? 0, aimActor.id) } else if (preparationMode && !layoutEditing) setSelectedPlayerStart(cell); else if (!preparationMode && displayToken?.type === 'PLAYER') setSelectedToken(displayToken.id); else if (!preparationMode && (locationMode || waypointMode)) void chooseCell(cell) }}>
            {preparationMode ? door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '벽' : token ? '시작' : '' : ghostDestination ? '유령 토큰' : tokenVisual && displayToken ? <span className="token-visual" aria-hidden="true"><span className="token-visual-frame" style={{ backgroundImage: `url(${tokenVisual.framePath})` }} /><img src={tokenVisual.assetPath} alt="" /><span className="token-visual-label">{`${displayToken.type} (${displayToken.x},${displayToken.y})${displayToken.elevationFeet ? ` · 지면에서 높이 ${displayToken.elevationFeet} 피트` : ''}`}</span></span> : door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '장애물' : visible && !mapImage ? `${cell.x},${cell.y}` : explored ? '안개' : ''}
          </button>
          })}
          {mapBoundaries.filter(boundary => (preparationMode || !mapImage || boundary.kind === 'DOOR') && (!playableWindow || (boundary.orientation === 'HORIZONTAL'
            ? boundary.y >= playableWindow.minY && boundary.y <= playableWindow.minY + playableWindow.height && boundary.x >= playableWindow.minX && boundary.x < playableWindow.minX + playableWindow.width
            : boundary.x >= playableWindow.minX && boundary.x <= playableWindow.minX + playableWindow.width && boundary.y >= playableWindow.minY && boundary.y < playableWindow.minY + playableWindow.height)))
            .map(boundary => {
              const displayed = playableWindow ? { ...boundary, x: boundary.x - playableWindow.minX, y: boundary.y - playableWindow.minY } : boundary
              return <span key={`boundary-${boundary.x}-${boundary.y}-${boundary.orientation}`} aria-hidden="true" className={`map-boundary map-boundary-${boundary.kind.toLowerCase()}`} data-boundary={`${boundary.orientation}:${boundary.x}:${boundary.y}`} style={boundaryStyle(displayed, displayedColumns, displayedRows)} />
            })}
          {preparationMode && boundaryPreview && boundariesInStroke(boundaryPreview).map((segment, index) => <span key={`boundary-preview-${index}`} aria-hidden="true" className={`map-boundary map-boundary-preview map-boundary-${boundaryTool.toLowerCase()}`} style={boundaryStyle(segment, previewGrid.width, previewGrid.height)} />)}
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
      {showMapSetupChoice && tacticalMap}
      {showMapSetupChoice && <section aria-label="저장된 지도 설정 선택" className="map-setup-choice">
        <h3>저장된 지도 설정</h3>
        <p>현재 지도: 격자 {grid.width}열 × {grid.height}행 · 저장된 벽·문 {mapBoundaries.length}개</p>
        <p>격자 출처: {map.layers?.find(layer => layer.type === 'GRID_SOURCE')?.value ?? '기록 없음'}</p>
        <p>아래 지도에서 저장된 격자와 벽 위치를 확인하고 선택하세요. 현재 설정을 쓰면 이 모험의 지도 설정으로 확정합니다.</p>
        <button type="button" disabled={layoutSaving || !alignmentAvailable} onClick={() => void useCurrentMapSetup()}>
          {layoutSaving ? '현재 설정 확인 중…' : '현재 격자와 벽 사용'}
        </button>
        <button type="button" disabled={layoutSaving} onClick={() => { setMapSetupChoice('edit'); diagnosticEvent('combat_map_existing_setup', { outcome: 'reconfigure_selected', gridWidth: grid.width, gridHeight: grid.height, boundaries: mapBoundaries.length }) }}>
          격자·벽 다시 설정
        </button>
        {!alignmentAvailable && <p role="status">저장된 격자 정보를 읽을 수 없어 현재 설정을 확정할 수 없습니다. 다시 설정을 선택하세요.</p>}
      </section>}
      {showMapSetupEditors && <section aria-label="맵 여백 자르기" className="map-crop-preparation">
        <h3>1. 맵 여백 자르기</h3>
        {!mapImage ? <p role="status">지도 이미지를 불러오는 중입니다. 이미지가 준비되면 남길 영역을 지정할 수 있습니다.</p> : cropEditorOpen ? <>
          <p>남길 영역을 드래그한 뒤 자르기 적용을 누르세요. 적용해야 다음 단계로 넘어갑니다.</p>
          <MapCropEditor image={mapImage} crop={crop} onChange={next => { setCrop(next); setLayoutDirty(true); setLayoutSaved(false); setCropConfirmed(false) }} />
          <button type="button" disabled={layoutSaving || crop.width <= 0 || crop.height <= 0} onClick={() => void applyCrop()}>자르기 적용</button>
        </> : <button type="button" onClick={() => { setCropEditorOpen(true); setCropConfirmed(false); setGridConfirmed(false); setLayoutDirty(true); setLayoutSaved(false) }}>자르기 다시 수정</button>}
      </section>}
      {showMapSetupEditors && <section aria-label="맵 격자 맞추기" className="map-grid-editor">
        <h3>2. 맵 격자 맞추기</h3>
        {!cropConfirmed ? <p>1단계에서 여백 자르기를 적용하면 격자를 맞출 수 있습니다.</p> : !showGridEditor ? <p role="status">지도 이미지와 격자 정보를 불러오는 중입니다.</p> : <>
          <button type="button" onClick={() => setGridEditor(true)}>격자 맞추기</button>
          {gridEditor && <MapGridAlignmentEditor key={`${alignment.mapId}-${alignment.version}`} image={mapImage!} initial={alignment} crop={crop} grid={map.grid} onAnalyze={api.analyzeMapGrid ? area => api.analyzeMapGrid!(adventureId, area) : undefined} onCancel={() => { setGridEditor(false); setGridMessage('이번 정렬 초안을 취소했습니다.') }} onApply={async value => {
          try {
            if (value.width !== map.grid?.width || value.height !== map.grid?.height) {
              if (!api.calibrateMapGrid || !map.mapId) throw new Error('격자 수를 저장할 수 없습니다. 지도를 다시 불러오세요.')
              try {
                await api.calibrateMapGrid(adventureId, { mapId: map.mapId, expectedVersion: map.version ?? 0,
                  width: value.width, height: value.height, cellSize: Math.max(1, Math.floor(value.cellSize)),
                  originX: Math.max(0, Math.floor(value.originX)), originY: Math.max(0, Math.floor(value.originY)),
                  imageWidth: mapImageSize.width, imageHeight: mapImageSize.height })
              } catch (error) {
                setMap(await (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)))
                throw error
              }
              setMap(await (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)))
            }
            const alignmentRequest = { mapId: value.mapId, commandId: value.commandId, expectedVersion: value.expectedVersion,
              imageRevision: value.imageRevision, originX: value.originX, originY: value.originY, cellSize: value.cellSize }
            const saved = await api.applyMapGridAlignment!(adventureId, alignmentRequest)
            setAlignment(saved); setGridConfirmed(true); setLayoutSaved(false); setGridEditor(false); setGridMessage('격자 정렬을 저장했습니다. 이제 AI 초안을 생성하세요.'); setMap(await (preparationMode ? (api.getCombatMapPreparation?.(adventureId) ?? api.getCombatMap(adventureId)) : api.getCombatMap(adventureId)))
          } catch (error) {
            if (error instanceof Error && 'status' in error && (error as { status?: number }).status === 409) {
              try { const latest = await api.getMapGridAlignment!(adventureId); setAlignment(latest); setAlignmentAvailable(true); setGridMessage('다른 정렬 저장이 먼저 반영됐습니다. 최신 정렬을 불러왔습니다. 다시 적용하세요.') } catch { /* keep the original error in the editor */ }
            }
            throw error
          }
          }} />}
        </>}
        <p role="status">{gridMessage}</p>
      </section>}
      {!showMapSetupChoice && preparationMode && <section className="map-start-preparation" aria-label="플레이어 시작 위치"><h3>시작 위치 확인</h3>{mapPreparationPlacementRequired && <p role="alert">자동 판정으로 시작 위치를 찾지 못했습니다. 지도에서 직접 선택해주세요.</p>}<p>모험 중 맵에 진입할 때 행동·서술과 지도 이미지를 바탕으로 시작 위치를 자동 판정합니다. 아래 선택은 자동 판정에 실패했을 때 사용할 수 있는 수동 대안입니다.</p>{map.playerStartCandidates?.length ? <ul>{map.playerStartCandidates.map((candidate, index) => <li key={`${candidate.x}-${candidate.y}-${index}`}><button type="button" aria-pressed={selectedPlayerStart?.x === candidate.x && selectedPlayerStart?.y === candidate.y} onClick={() => setSelectedPlayerStart({ x: candidate.x, y: candidate.y })}>({candidate.x},{candidate.y}) 선택</button><span>신뢰도 {Math.round(candidate.confidence * 100)}% · {candidate.evidence.join(', ') || '근거 없음'}</span></li>)}</ul> : <p>아직 모험 중 진입 서술이 없어 자동 시작 위치 후보가 없습니다.</p>}<p role="status">{selectedPlayerStart ? `수동 대안으로 선택한 시작 칸: (${selectedPlayerStart.x},${selectedPlayerStart.y})` : '자동 판정 대기 중입니다.'}</p></section>}
      {showMapSetupEditors && <section className="map-preparation-editor" aria-label="맵 초안 검수"><h3>3. AI 초안 생성 및 검수</h3><p>{!cropConfirmed ? '먼저 1단계에서 여백 자르기를 적용하세요.' : !gridConfirmed ? '먼저 2단계에서 격자를 맞추고 적용하세요.' : '격자 적용 완료. 현재 자른 영역과 격자를 기준으로 AI 초안을 생성합니다.'}</p>{cropConfirmed && gridConfirmed && <><button type="button" disabled={boundaryDetecting || layoutSaving || layoutEditing} onClick={() => void detectBoundaries()}>{boundaryDetecting ? 'AI 벽·문 감지 중…' : 'AI 벽·문 감지'}</button><button type="button" onClick={() => { if (layoutEditing) { setLayoutEditing(false); return }; if (!layoutBeforeEdit) setLayoutBeforeEdit(map); setLayoutSaved(false); setLayoutEditing(true) }}>{layoutEditing ? '검수 닫기' : '벽·문 편집'}</button>{layoutEditing && <div className="map-layout-editor"><ol className="map-layout-guide"><li>선은 칸의 한 면에 붙어 표시됩니다.</li><li>벽 그리기·문 그리기·지우기 중 하나를 고르세요.</li><li>격자선 위를 누른 채 끌면 지나간 선분에 적용됩니다.</li></ol><div className="map-boundary-tools" role="group" aria-label="벽과 문 그리기 도구"><button type="button" aria-pressed={boundaryTool === 'WALL'} onClick={() => setBoundaryTool('WALL')}>벽 그리기</button><button type="button" aria-pressed={boundaryTool === 'DOOR'} onClick={() => setBoundaryTool('DOOR')}>문 그리기</button><button type="button" aria-pressed={boundaryTool === 'ERASE'} onClick={() => setBoundaryTool('ERASE')}>지우기</button></div>{tacticalMap}<p>칸은 이동하거나 선택되지 않습니다.</p><div className="map-layout-actions"><button type="button" disabled={layoutSaving} onClick={() => { boundaryStroke.current = null; setBoundaryPreview(null); const restored = layoutBeforeEdit; setMap(restored); setLayoutSaved(restored?.layers?.some(layer => layer.type === 'MAP_LAYOUT_CONFIRMED') ?? false); setLayoutDirty(false); setLayoutEditing(false); setLayoutBeforeEdit(null) }}>편집 취소</button><button type="button" disabled={layoutSaving} onClick={() => void saveLayout()}>{layoutSaving ? '저장 중…' : '맵 초안 저장'}</button></div></div>}</>}</section>}
      {!layoutEditing && !showMapSetupChoice && tacticalMap}
      {!preparationMode && placementRequired && api.submitCombatTokenPlacement && explorationVersion !== null && <section className="exploration-groups" aria-label="전투 파티 배치">
        <h3>{tokenTransition?.direction === 'EXIT' ? '탐험 그룹 복원 위치' : '전투 파티 배치'}</h3>
        <p role="alert">전원을 배치할 수 있는 칸을 찾지 못했습니다. 각 대상의 위치를 선택한 뒤 전체 배치를 제출하세요. 지도 밖이나 공개되지 않은 칸은 선택할 수 없습니다.</p>
        <ol>{placementTargets.map(target => <li key={target.id}>
          <button type="button" aria-pressed={placementTargetId === target.id} onClick={() => setPlacementTargetId(target.id)}>{target.label} 위치 선택</button>
          <span>{manualPlacements[target.id] ? `선택한 칸 (${manualPlacements[target.id].x},${manualPlacements[target.id].y})` : '위치 미선택'}</span>
        </li>)}</ol>
        <p>현재 지도에서 공개된 칸을 눌러 선택 대상의 위치를 지정하세요.</p>
        <button type="button" disabled={!completeManualPlacement || explorationBusy || submitting} onClick={() => void submitManualPlacement()}>{explorationBusy ? '전체 배치 확인 중…' : '전체 배치 제출'}</button>
      </section>}
      {!preparationMode && explorationGroups.length > 0 && api.saveExplorationGroups && <section className="exploration-groups" aria-label="탐험 그룹 관리">
        <h3>탐험 그룹과 대열</h3>
        {api.startExplorationCycle && api.finishExplorationGroupTurn && api.resolveExplorationMovementEvent && api.advanceExplorationEnvironment && explorationVersion !== null && <section aria-label="탐험 순환">
          {!explorationCycle ? <button type="button" disabled={explorationBusy || submitting || awaitingRuntimeTurnCommit} onClick={() => {
            const commandId = createMapCommandIdentity().commandId
            void updateExplorationCycle(() => api.startExplorationCycle!(adventureId, explorationVersion, commandId))
          }}>탐험 순환 시작</button> : <>
            <p role="status">탐험 순환 {explorationCycle.cycleNumber ?? ''} · 지난 시간 {explorationCycle.elapsedSeconds}초 · {explorationCycle.phase === 'GROUP_TURNS' ? '그룹 이동' : `환경 진행: ${explorationCycle.environmentPhase}`}</p>
            {explorationCycle.pendingMovementEvent && <div role="alert" aria-label="이동 중단 사건">
              <p>이동 중단: {explorationCycle.pendingMovementEvent.cause} · 위치 ({explorationCycle.pendingMovementEvent.finalPosition.x},{explorationCycle.pendingMovementEvent.finalPosition.y})</p>
              {explorationCycle.pendingMovementEvent.publicEvents.length > 0 && <p>공개된 결과: {explorationCycle.pendingMovementEvent.publicEvents.join(', ')}</p>}
              <button type="button" disabled={explorationBusy || submitting || awaitingRuntimeTurnCommit} onClick={() => void updateExplorationCycle(() => api.resolveExplorationMovementEvent!(adventureId, explorationCycle.pendingMovementEvent!.id, explorationVersion))}>사건을 해결하고 다음 그룹으로</button>
            </div>}
            {currentExplorationGroupId && <div>
              <p>현재 이동 차례: {explorationGroups.find(group => group.id === currentExplorationGroupId)?.members.map(member => member.characterSheetId.slice(0, 8)).join(', ') ?? '그룹'}</p>
              <button type="button" disabled={explorationBusy || submitting || awaitingRuntimeTurnCommit} onClick={() => {
                const commandId = createMapCommandIdentity().commandId
                void updateExplorationCycle(() => api.finishExplorationGroupTurn!(adventureId, currentExplorationGroupId, explorationVersion, commandId))
              }}>현재 그룹 차례 완료</button>
            </div>}
            {explorationCycle.phase === 'ENVIRONMENT' && !explorationCycle.pendingMovementEvent && <div>
              {explorationCycle.pendingEnvironmentProposal?.narration && <p>{explorationCycle.pendingEnvironmentProposal.narration}</p>}
              {explorationCycle.pendingEnvironmentProposal?.encounterRequested && !explorationCycle.pendingEnvironmentProposal.combatLinked
                ? <p role="alert">이 상황을 어떻게 처리할지 모험 대화에 입력해주세요. 다음 GM 응답에서 전투를 시작하거나 전투 없이 상황을 해결합니다. 해결되면 환경 진행을 이어갈 수 있습니다.</p>
                : explorationCycle.pendingEnvironmentProposal?.playerResponseRequired
                  ? <p role="alert">적이 먼저 여러분을 발견했습니다. 위 서술에 이어 모험 대화에 응답해주세요. 응답이 반영된 뒤 환경 진행을 이어갈 수 있습니다.</p>
                : <button type="button" disabled={explorationBusy || submitting || awaitingRuntimeTurnCommit} onClick={() => {
                  void updateExplorationCycle(() => api.advanceExplorationEnvironment!(adventureId, explorationVersion, explorationCycle.currentPhaseCommandId))
                }}>환경 단계 진행</button>}
            </div>}
          </>}
        </section>}
        {explorationGroups.map(group => {
          const token = map?.tokens?.find(item => item.id === group.mapTokenId)
          return <fieldset key={group.id}>
            <legend>그룹 · 이동 속도 {group.speed}피트{token ? ` · 현재 위치 (${token.x},${token.y})` : ''}</legend>
            {group.members.map(member => <label key={member.characterSheetId}>
              모험가 {member.characterSheetId.slice(0, 8)}
              <select aria-label={`대열 ${member.characterSheetId}`} value={member.formation} disabled={explorationBusy}
                onChange={event => void saveGroups(explorationGroups.map(candidate => candidate.id !== group.id ? candidate : {
                  ...candidate, members: candidate.members.map(current => current.characterSheetId !== member.characterSheetId
                    ? current : { ...current, formation: event.target.value as typeof current.formation }),
                }))}>
                <option value="FRONT">전열</option><option value="MIDDLE">중열</option><option value="REAR">후열</option>
              </select>
            </label>)}
            {group.members.length > 1 && <label>분리할 모험가
              <select aria-label={`분리할 모험가 ${group.id}`} value={splitMemberIds[group.id] ?? group.members.at(-1)!.characterSheetId}
                disabled={explorationBusy} onChange={event => setSplitMemberIds(current => ({ ...current, [group.id]: event.target.value }))}>
                {group.members.map(member => <option key={member.characterSheetId} value={member.characterSheetId}>모험가 {member.characterSheetId.slice(0, 8)}</option>)}
              </select>
              <button type="button" disabled={explorationBusy} onClick={() => {
              const moved = group.members.find(member => member.characterSheetId === (splitMemberIds[group.id] ?? group.members.at(-1)!.characterSheetId))!
              const identity = createMapCommandIdentity().commandId
              void saveGroups([...explorationGroups.filter(candidate => candidate.id !== group.id),
                { ...group, members: group.members.filter(member => member.characterSheetId !== moved.characterSheetId) },
                { id: identity, mapTokenId: identity, speed: moved.speed, members: [moved] }])
            }}>현재 위치에서 분리</button>
            </label>}
          </fieldset>
        })}
        {explorationGroups.flatMap((target, index) => explorationGroups.slice(index + 1).map(source => {
          const targetToken = map?.tokens?.find(item => item.id === target.mapTokenId)
          const sourceToken = map?.tokens?.find(item => item.id === source.mapTokenId)
          const coLocated = targetToken && sourceToken && targetToken.x === sourceToken.x && targetToken.y === sourceToken.y
          return <button key={`${target.id}-${source.id}`} type="button" disabled={explorationBusy || !coLocated}
            title={coLocated ? '선택한 두 그룹을 명시적으로 합칩니다.' : '같은 칸에 있는 그룹만 합칠 수 있습니다.'}
            onClick={() => void saveGroups([...explorationGroups.filter(group => group.id !== target.id && group.id !== source.id),
              { ...target, members: [...target.members, ...source.members] }])}>그룹 합류</button>
        }))}
      </section>}
      {!preparationMode && api.previewNaturalLanguageMovement && <form aria-label="자연어 이동" onSubmit={previewNaturalMovement}><label htmlFor="natural-movement-text">어디로 이동할까요?</label><input id="natural-movement-text" value={naturalMovementText} onChange={event => setNaturalMovementText(event.target.value)} placeholder="예: 열린 문 쪽으로 이동" /><button type="submit" disabled={previewing || !naturalMovementText.trim()}>목적지 미리보기</button></form>}
      {map?.tokens?.find(token => token.type === 'PLAYER' && map.current?.some(cell => cell.x === token.x && cell.y === token.y)) && api.observeSpatial && <button type="button" onClick={() => void observeCurrentCell()}>주변 살피기</button>}
      {map?.tokens?.filter(token => token.type !== 'PLAYER' && !token.lastSeen && map.current?.some(cell => cell.x === token.x && cell.y === token.y)).map(token => <button key={`target-${token.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'TARGET', { x: token.x, y: token.y }, token.id)) }}>대상 선택: {token.type}</button>)}
      {map?.objects?.filter(object => map.current?.some(cell => cell.x === object.x && cell.y === object.y)).map(object => <button key={`object-${object.id}`} type="button" onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'INTERACT', { x: object.x, y: object.y }, object.id)) }}>상호작용: {object.type}</button>)}
      {map?.spatialFeatures?.filter(feature => feature.interactable).flatMap(feature => {
        const cell = feature.cells.find(position => map.current?.some(current => current.x === position.x && current.y === position.y))
        if (!cell) return []
        return [<button key={`spatial-feature-${feature.id}`} type="button" data-spatial-feature={feature.id} onClick={() => { const player = map.tokens?.find(item => item.type === 'PLAYER'); if (player) setCandidate(actionCandidate(map.mapId ?? '', map.version ?? 0, player.id, 'INTERACT', cell, feature.id)) }}>상호작용: {feature.type}</button>]
      })}
      {candidate && <div role="dialog" aria-label="맵 행동 확인"><p>{candidate.action === 'MOVE' && candidate.from && candidate.to ? `이동: (${candidate.from.x},${candidate.from.y}) → (${candidate.to.x},${candidate.to.y})` : `맵 행동: ${candidate.action}`}</p>{candidate.action === 'MOVE' && <><p>경로 칸: {candidate.path?.length ?? 0} · 거리: {candidate.distance ?? 0}</p><button type="button" disabled={submitting || previewing} onClick={() => setWaypointMode(current => !current)}>{waypointMode ? '경유 지점 조정 끝내기' : '경유 지점 추가'}</button>{waypointMode && <p>지도에서 경유할 칸을 눌러 경로를 조정하세요.</p>}</>}<button type="button" disabled={submitting || previewing} onClick={() => void confirm()}>확인</button><button type="button" disabled={submitting || previewing} onClick={() => { previewSequence.current += 1; void api.clearPendingMapMovement?.(adventureId); setCandidate(null); setSelectedToken(null); setWaypointMode(false) }}>취소</button></div>}
      <p role="status">{message}</p>
      {replayedMovement && <section aria-label="최근 이동 결과" role="status">
        <p>{replayedMovement.status === 'INTERRUPTED' ? '이동이 중단되었습니다.' : '이동이 완료되었습니다.'}</p>
        {replayedMovement.interruptionReason && <p>중단 사유: {replayedMovement.interruptionReason}</p>}
        {replayedMovement.followUp && <p>후속 진행: {replayedMovement.followUp.kind === 'CONTINUATION' ? '모험 진행 판단 대기' : replayedMovement.followUp.kind}</p>}
        {replayedMovement.publicEvents.length > 0 && <p>공개된 결과: {replayedMovement.publicEvents.join(', ')}</p>}
      </section>}
      {pendingMovement && (pendingMovement.result.status === 'RETRY_REQUIRED' || pendingMovement.result.status === 'CHECK_REQUIRED') && <section aria-label="저장된 이동 상태" role="status">
        <p>{pendingMovement.result.status === 'RETRY_REQUIRED' ? '이동 재시도 필요' : '이동 판정 확인 필요'}</p>
        <p>작업 번호: {pendingMovement.result.operationId ?? '없음'}</p>
        {pendingMovement.result.pendingCheck && <>
          <p>{pendingMovement.result.pendingCheck.label} · {pendingMovement.result.pendingCheck.diceExpression}</p>
          <form className="movement-check-actions" aria-label="판정 굴리기" onSubmit={event => { event.preventDefault(); void submitPendingRoll() }}>
            <button type="submit" disabled={!api.rollSpatialCheck}>주사위 굴리기</button>
          </form>
        </>}
        <button type="button" onClick={() => void recoverMovement(false)}>이동 상태 다시 확인</button>
        {!pendingMovement.result.pendingCheck && <button type="button" onClick={() => void recoverMovement(true)}>이동 재개</button>}
        {pendingMovement.result.operationId && api.cancelMovementOperation && <button type="button" onClick={async () => {
          try {
            const cancelCommandId = pendingMovement.cancelCommandId ?? createMapCommandIdentity().commandId
            if (!pendingMovement.cancelCommandId) {
              const withCancelCommand = { ...pendingMovement, cancelCommandId }
              setPendingMovement(withCancelCommand)
              try { window.localStorage.setItem(pendingMovementKey(adventureId), JSON.stringify(withCancelCommand)) } catch { /* storage is optional */ }
            }
            const result = await api.cancelMovementOperation?.(adventureId, pendingMovement.mapId, pendingMovement.result.operationId!, cancelCommandId)
            const refreshed = await api.getCombatMap(adventureId)
            if (result) await applyMovementResult(result, pendingMovement.mapId, pendingMovement.tokenId, pendingMovement.turnId, pendingMovement.commandId, map, refreshed)
          } catch (error) { setMessage(error instanceof Error ? error.message : '이동을 취소하지 못했습니다.') }
        }}>이동 취소</button>}
      </section>}
      {preparationMode && !showMapSetupChoice && <button type="button" disabled={preparationStarting || layoutSaving || layoutDirty || !gridConfirmed || !layoutSaved || !onPreparationComplete} aria-busy={preparationStarting} onClick={() => void completePreparation()}>{preparationStarting ? '모험 시작 요청 중…' : '맵 준비 완료, 모험 시작'}</button>}
    </section>
  )
}

function clearPendingMovementCommand(adventureId: string) {
  try { window.localStorage.removeItem(pendingMovementCommandKey(adventureId)) } catch { /* storage is optional */ }
}

/**
 * The server remains authoritative.  These short frames only reveal the already
 * committed path in its fixed order, so a player never sees the final token or
 * fog state jump ahead of the traversed cells.
 */
// Exported for deterministic path-animation tests.
// eslint-disable-next-line react-refresh/only-export-components
export async function animateCommittedMovement(
  apply: (next: CombatMapState | null | ((current: CombatMapState | null) => CombatMapState | null)) => void,
  before: CombatMapState, committed: CombatMapState, tokenId: string, traversedPath: Array<{ x: number; y: number }>,
) {
  if (traversedPath.length < 2) { apply(committed); return }
  apply(before)
  for (const cell of traversedPath.slice(1)) {
    const committedToken = committed.tokens?.find(token => token.id === tokenId)
    if (!committedToken) break
    const visible = committed.current?.some(position => position.x === cell.x && position.y === cell.y)
    const explored = committed.explored?.some(position => position.x === cell.x && position.y === cell.y)
    apply(current => {
      if (!current) return current
      const currentCells = current.current ?? []
      const exploredCells = current.explored ?? []
      return {
        ...current,
        tokens: current.tokens?.map(token => token.id === tokenId ? { ...token, x: cell.x, y: cell.y } : token),
        current: visible && !currentCells.some(position => position.x === cell.x && position.y === cell.y)
          ? [...currentCells, cell] : currentCells,
        explored: explored && !exploredCells.some(position => position.x === cell.x && position.y === cell.y)
          ? [...exploredCells, cell] : exploredCells,
      }
    })
    await new Promise<void>(resolve => window.setTimeout(resolve, 120))
  }
  apply(committed)
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

function appendMovementCell(path: GridCell[], cell: GridCell) {
  const last = path[path.length - 1]
  if (last?.x === cell.x && last.y === cell.y) return path
  const previous = path[path.length - 2]
  return previous?.x === cell.x && previous.y === cell.y ? path.slice(0, -1) : [...path, cell]
}

function startBoundaryStroke(event: React.PointerEvent<HTMLDivElement>, width: number, height: number): BoundaryGeometry | null {
  const rect = event.currentTarget.getBoundingClientRect()
  const { x, y } = screenPointToGrid(event.clientX, event.clientY, rect, width, height)
  const verticalDistance = Math.abs(x - Math.round(x))
  const horizontalDistance = Math.abs(y - Math.round(y))
  if (Math.min(verticalDistance, horizontalDistance) > .28) return null
  return verticalDistance <= horizontalDistance
    ? { orientation: 'VERTICAL', fixed: Math.round(x), from: Math.min(height - 1, Math.floor(y)), to: Math.min(height - 1, Math.floor(y)) }
    : { orientation: 'HORIZONTAL', fixed: Math.round(y), from: Math.min(width - 1, Math.floor(x)), to: Math.min(width - 1, Math.floor(x)) }
}

function extendBoundaryStroke(stroke: BoundaryGeometry, event: React.PointerEvent<HTMLDivElement>, width: number, height: number): BoundaryGeometry {
  const rect = event.currentTarget.getBoundingClientRect()
  const point = screenPointToGrid(event.clientX, event.clientY, rect, width, height)
  const coordinate = stroke.orientation === 'HORIZONTAL'
    ? Math.max(0, Math.min(width - 1, Math.floor(point.x)))
    : Math.max(0, Math.min(height - 1, Math.floor(point.y)))
  return { ...stroke, to: coordinate }
}

// This helper is exported for the interaction-flow tests; keep the component
// module's fast-refresh warning scoped to this intentional non-component export.
// eslint-disable-next-line react-refresh/only-export-components
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
