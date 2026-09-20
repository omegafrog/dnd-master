import { useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState, MapBoundary, MapBoundaryProposal, MapMovementResult } from '../saved-adventures/AdventurePlayApi'
import { actionCandidate, moveCandidate, type MapInteractionCandidate } from './MapInteractionCandidate'
import { MapGridAlignmentEditor } from './MapGridAlignmentEditor'
import { MapCropEditor } from './MapCropEditor'
import { tokenVisualCatalog } from './TokenVisualCatalog'

type PendingMovement = { mapId: string; tokenId: string; turnId?: string; commandId?: string; cancelCommandId?: string; result: MapMovementResult }
type PendingMovementCommand = { candidate: MapInteractionCandidate; turnId: string; commandId: string; expectedVersion: number }

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

export function CombatMapView({ adventureId, api, refreshToken = 0, compact = false, preparationMode = false, onPreparationComplete }: { adventureId: string; api: AdventurePlayApi; refreshToken?: number; compact?: boolean; preparationMode?: boolean; onPreparationComplete?: () => void | Promise<void> }) {
  const [map, setMap] = useState<CombatMapState | null>(null)
  const [publicMapImage, setPublicMapImage] = useState<string | null>(null)
  const [selectedToken, setSelectedToken] = useState<string | null>(null)
  const [naturalMovementText, setNaturalMovementText] = useState('')
  const [candidate, setCandidate] = useState<MapInteractionCandidate | null>(() => readPendingMovementCommand(adventureId)?.candidate ?? null)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [waypointMode, setWaypointMode] = useState(false)
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
  const [selectedPlayerStart, setSelectedPlayerStart] = useState<{ x: number; y: number } | null>(null)
  const boundaryStroke = useRef<BoundaryStroke | null>(null)
  const previewSequence = useRef(0)
  const [boundaryPreview, setBoundaryPreview] = useState<BoundaryStroke | null>(null)
  const [pendingMovement, setPendingMovement] = useState<PendingMovement | null>(() => readPendingMovement(adventureId))
  const [replayedMovement, setReplayedMovement] = useState<MapMovementResult | null>(null)

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
        // The player map is owned by the current tactical situation. A
        // prepared draft is never substituted into the runtime view before
        // that situation activates its map relation.
        const nextMap = fetchedMap
        if (!active) return
        setMap(nextMap)
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
                fingerprint: pending.fingerprint, waypoints: pending.waypoints, sourceText: pending.sourceText, pendingTurnId: pending.pendingTurnId })
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
        try { const current = await (api.getMapGridAlignment?.(adventureId) ?? Promise.reject(new Error('unavailable'))); if (active) { setAlignment(current); setAlignmentAvailable(true); if (current.version > 0 && !preparationMode) setGridConfirmed(true); if (preparationMode) setLayoutSaved(layoutConfirmedForAlignment(nextMap, current)) } } catch { if (active) { setAlignmentAvailable(false); setLayoutSaved(false); setGridMessage('저장된 격자 정렬을 불러오지 못했습니다.') } }
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
      } catch {
        if (active) { setMap(null); setPublicMapImage(null) }
      }
    })()
    return () => { active = false }
  }, [adventureId, api, preparationMode, refreshToken])

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

  async function previewMovement(tokenId: string, destination: { x: number; y: number }, waypoints: { x: number; y: number }[], base: MapInteractionCandidate, sourceMap = map, retryStale = true) {
    if (!sourceMap?.mapId) return
    const sequence = previewSequence.current + 1
    previewSequence.current = sequence
    setPreviewing(true)
    try {
      if (!api.previewMapMovement) throw new Error('서버 이동 경로 미리보기를 사용할 수 없습니다.')
      const preview = await api.previewMapMovement(adventureId, { mapId: sourceMap.mapId, mapVersion: sourceMap.version ?? 0, tokenId, destination, waypoints,
        pendingTurnId: base.pendingTurnId, sourceText: base.sourceText })
      if (sequence !== previewSequence.current) return
      setCandidate(current => current === base || (current?.tokenId === tokenId && current?.action === 'MOVE')
        ? { ...base, mapVersion: preview.baseMapVersion, path: preview.orderedPositions, distance: preview.distance, fingerprint: preview.fingerprint, waypoints }
        : current)
      setMessage(`이동 경로를 미리 보았습니다. 거리: ${preview.distance}`)
    } catch (error) {
      if (sequence !== previewSequence.current) return
      const status = error && typeof error === 'object' && 'status' in error ? (error as { status?: unknown }).status : undefined
      if (status === 409 && retryStale && base.to && api.previewMapMovement) {
        try {
          const refreshed = await api.getCombatMap(adventureId)
          setMap(refreshed)
          await previewMovement(tokenId, destination, waypoints, { ...base, mapVersion: refreshed.version ?? 0 }, refreshed, false)
          setMessage('지도 상태가 바뀌었습니다. 최신 이동 경로를 다시 확인했습니다. 다시 확인해주세요.')
          return
        } catch { /* preserve the original request error */ }
      }
      setCandidate(current => current === base ? null : current)
      setMessage(error instanceof Error ? error.message : '이동 경로를 미리 보지 못했습니다.')
    } finally { if (sequence === previewSequence.current) setPreviewing(false) }
  }

  async function confirm() {
    if (!candidate) return
    if (submitting) return
    if (candidate.action === 'MOVE' && candidate.mapVersion !== (map?.version ?? candidate.mapVersion)) {
      if (candidate.to) await previewMovement(candidate.tokenId, candidate.to, candidate.waypoints ?? [], candidate)
      setMessage('지도 상태가 바뀌었습니다. 최신 이동 경로를 다시 확인해주세요.')
      return
    }
    setSubmitting(true)
    if (candidate.action === 'MOVE' && candidate.pendingTurnId && api.confirmNaturalLanguageMovement) {
      try {
        const command = createMapCommandIdentity()
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
      } else {
        setMap(refreshed)
        clearPendingMovementCommand(adventureId)
        setCandidate(null); setSelectedToken(null); setMessage('맵 행동을 GM 턴으로 전송했습니다.')
      }
    } catch (error) {
      // The runtime can commit the map command before the HTTP request sees
      // a concurrent-version response. Reconcile that response with the
      // authoritative map before showing an error to the player.
      const status = error && typeof error === 'object' && 'status' in error ? (error as { status?: unknown }).status : undefined
      if (status === 409 && candidate.action === 'MOVE' && candidate.to && api.previewMapMovement) {
        try {
          const refreshed = await api.getCombatMap(adventureId)
          setMap(refreshed)
          await previewMovement(candidate.tokenId, candidate.to, candidate.waypoints ?? [], { ...candidate, mapVersion: refreshed.version ?? 0 }, refreshed)
          setMessage('지도 상태가 바뀌었습니다. 최신 이동 경로를 다시 확인하고 확인해주세요.')
          return
        } catch { /* preserve the original request error */ }
      }
      if (status === 409 && candidate.action === 'MOVE' && candidate.to) {
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
      setCandidate({ mapId: map.mapId, mapVersion: result.baseMapVersion ?? map.version ?? 0, tokenId: token.id, action: 'MOVE', from: { x: token.x, y: token.y }, to: result.destination, path: result.path, distance: result.distance, fingerprint: result.fingerprint, waypoints: [], sourceText: naturalMovementText.trim(), pendingTurnId: result.pendingTurnId })
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
  const hasVisibilityMetadata = Array.isArray(map?.current) && Array.isArray(map?.explored)
  const placementRequired = map?.layers?.some(layer => layer.type === 'MAP_PLACEMENT_STATUS' && layer.value === 'PLACEMENT_REQUIRED') ?? false
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
  const tacticalMap = map.tokens ? (
    <div className="tactical-map-window">
      {!preparationMode && <button type="button" aria-pressed={locationMode} onClick={() => setLocationMode(current => !current)}>위치 선택</button>}
          <div aria-label="tactical-map" data-map-id={map.mapId} data-version={map.version ?? 0} className="tactical-map" style={mapStyle} onPointerDown={event => {
            if (!preparationMode || !layoutEditing) return
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
          // A prepared map may contain an AI-generated or legacy player-start
          // suggestion. It is not authoritative and must never be shown as a
          // coordinate the user is expected to confirm.
          const token = (preparationMode ? map.tokens?.filter(item => item.type !== 'PLAYER') : map.tokens)?.find(item => item.x === cell.x && item.y === cell.y)
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
          const draftLabel = door ? `${door.open ? '열린 문' : '닫힌 문'} ${cell.x},${cell.y}` : blocked ? `벽 ${cell.x},${cell.y}` : token ? `플레이어 시작 위치 ${cell.x},${cell.y}` : `빈 격자 ${cell.x},${cell.y}`
          return <button key={`${cell.x}-${cell.y}`} type="button" aria-label={preparationMode ? draftLabel : !playable ? '지도 밖 영역' : displayToken ? `${displayToken.type} ${displayToken.x},${displayToken.y}` : visible ? `격자 ${cell.x},${cell.y}` : explored ? `탐험한 격자 ${cell.x},${cell.y}` : '미탐험 영역'} data-visibility={!playable ? 'outside' : visible ? 'current' : explored ? 'explored' : 'hidden'} data-token-type={displayToken?.type} data-token-faction={tokenVisual?.faction} data-token-status={tokenVisual?.primaryStatus} data-token-asset={tokenVisual?.assetPath} data-token-frame={tokenVisual?.framePath} data-movement-preview={movementPreview ? 'true' : undefined} data-ghost-token={ghostDestination ? 'true' : undefined} data-start-selected={preparationMode && selectedPlayerStart?.x === cell.x && selectedPlayerStart?.y === cell.y ? 'true' : undefined} data-last-seen={displayToken?.lastSeen ? 'true' : 'false'} className={tokenVisual?.classes.join(' ')} disabled={!playable || startBlocked || (preparationMode && layoutEditing) || (!preparationMode && !visible && !explored)} draggable={!preparationMode && displayToken?.type === 'PLAYER'} onDragStart={() => { if (!preparationMode && displayToken?.type === 'PLAYER') setSelectedToken(displayToken.id) }} onClick={() => { if (preparationMode && !layoutEditing) setSelectedPlayerStart(cell); else if (!preparationMode && displayToken?.type === 'PLAYER') setSelectedToken(displayToken.id); else if (!preparationMode) void chooseCell(cell) }} onDragOver={event => event.preventDefault()} onDrop={() => { void chooseCell(cell) }}>
            {preparationMode ? door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '벽' : token ? '시작' : '' : ghostDestination ? '유령 토큰' : tokenVisual && displayToken ? <span className="token-visual" aria-hidden="true"><span className="token-visual-frame" style={{ backgroundImage: `url(${tokenVisual.framePath})` }} /><img src={tokenVisual.assetPath} alt="" /><span className="token-visual-label">{`${displayToken.type} (${displayToken.x},${displayToken.y})`}</span></span> : door ? (door.open ? '열린 문' : '닫힌 문') : blocked ? '장애물' : visible && !mapImage ? `${cell.x},${cell.y}` : explored ? '안개' : ''}
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
      {preparationMode && <section aria-label="맵 여백 자르기" className="map-crop-preparation">
        <h3>1. 맵 여백 자르기</h3>
        {!mapImage ? <p role="status">지도 이미지를 불러오는 중입니다. 이미지가 준비되면 남길 영역을 지정할 수 있습니다.</p> : cropEditorOpen ? <>
          <p>남길 영역을 드래그한 뒤 자르기 적용을 누르세요. 적용해야 다음 단계로 넘어갑니다.</p>
          <MapCropEditor image={mapImage} crop={crop} onChange={next => { setCrop(next); setLayoutDirty(true); setLayoutSaved(false); setCropConfirmed(false) }} />
          <button type="button" disabled={layoutSaving || crop.width <= 0 || crop.height <= 0} onClick={() => void applyCrop()}>자르기 적용</button>
        </> : <button type="button" onClick={() => { setCropEditorOpen(true); setCropConfirmed(false); setGridConfirmed(false); setLayoutDirty(true); setLayoutSaved(false) }}>자르기 다시 수정</button>}
      </section>}
      {preparationMode && <section aria-label="맵 격자 맞추기" className="map-grid-editor">
        <h3>2. 맵 격자 맞추기</h3>
        {!cropConfirmed ? <p>1단계에서 여백 자르기를 적용하면 격자를 맞출 수 있습니다.</p> : !showGridEditor ? <p role="status">지도 이미지와 격자 정보를 불러오는 중입니다.</p> : <>
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
        </>}
        <p role="status">{gridMessage}</p>
      </section>}
      {preparationMode && <section className="map-start-preparation" aria-label="플레이어 시작 위치"><h3>시작 위치 확인</h3>{placementRequired && <p role="alert">자동 판정으로 시작 위치를 찾지 못했습니다. 지도에서 직접 선택해주세요.</p>}<p>모험 중 맵에 진입할 때 행동·서술과 지도 이미지를 바탕으로 시작 위치를 자동 판정합니다. 아래 선택은 자동 판정에 실패했을 때 사용할 수 있는 수동 대안입니다.</p>{map.playerStartCandidates?.length ? <ul>{map.playerStartCandidates.map((candidate, index) => <li key={`${candidate.x}-${candidate.y}-${index}`}><button type="button" aria-pressed={selectedPlayerStart?.x === candidate.x && selectedPlayerStart?.y === candidate.y} onClick={() => setSelectedPlayerStart({ x: candidate.x, y: candidate.y })}>({candidate.x},{candidate.y}) 선택</button><span>신뢰도 {Math.round(candidate.confidence * 100)}% · {candidate.evidence.join(', ') || '근거 없음'}</span></li>)}</ul> : <p>아직 모험 중 진입 서술이 없어 자동 시작 위치 후보가 없습니다.</p>}<p role="status">{selectedPlayerStart ? `수동 대안으로 선택한 시작 칸: (${selectedPlayerStart.x},${selectedPlayerStart.y})` : '자동 판정 대기 중입니다.'}</p></section>}
      {preparationMode && <section className="map-preparation-editor" aria-label="맵 초안 검수"><h3>3. AI 초안 생성 및 검수</h3><p>{!cropConfirmed ? '먼저 1단계에서 여백 자르기를 적용하세요.' : !gridConfirmed ? '먼저 2단계에서 격자를 맞추고 적용하세요.' : '격자 적용 완료. 현재 자른 영역과 격자를 기준으로 AI 초안을 생성합니다.'}</p>{cropConfirmed && gridConfirmed && <><button type="button" disabled={boundaryDetecting || layoutSaving || layoutEditing} onClick={() => void detectBoundaries()}>{boundaryDetecting ? 'AI 벽·문 감지 중…' : 'AI 벽·문 감지'}</button><button type="button" onClick={() => { if (layoutEditing) { setLayoutEditing(false); return }; if (!layoutBeforeEdit) setLayoutBeforeEdit(map); setLayoutSaved(false); setLayoutEditing(true) }}>{layoutEditing ? '검수 닫기' : '벽·문 편집'}</button>{layoutEditing && <div className="map-layout-editor"><ol className="map-layout-guide"><li>선은 칸의 한 면에 붙어 표시됩니다.</li><li>벽 그리기·문 그리기·지우기 중 하나를 고르세요.</li><li>격자선 위를 누른 채 끌면 지나간 선분에 적용됩니다.</li></ol><div className="map-boundary-tools" role="group" aria-label="벽과 문 그리기 도구"><button type="button" aria-pressed={boundaryTool === 'WALL'} onClick={() => setBoundaryTool('WALL')}>벽 그리기</button><button type="button" aria-pressed={boundaryTool === 'DOOR'} onClick={() => setBoundaryTool('DOOR')}>문 그리기</button><button type="button" aria-pressed={boundaryTool === 'ERASE'} onClick={() => setBoundaryTool('ERASE')}>지우기</button></div>{tacticalMap}<p>칸은 이동하거나 선택되지 않습니다.</p><div className="map-layout-actions"><button type="button" disabled={layoutSaving} onClick={() => { boundaryStroke.current = null; setBoundaryPreview(null); const restored = layoutBeforeEdit; setMap(restored); setLayoutSaved(restored?.layers?.some(layer => layer.type === 'MAP_LAYOUT_CONFIRMED') ?? false); setLayoutDirty(false); setLayoutEditing(false); setLayoutBeforeEdit(null) }}>편집 취소</button><button type="button" disabled={layoutSaving} onClick={() => void saveLayout()}>{layoutSaving ? '저장 중…' : '맵 초안 저장'}</button></div></div>}</>}</section>}
      {!layoutEditing && tacticalMap}
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
      {preparationMode && <button type="button" disabled={preparationStarting || layoutSaving || layoutDirty || !gridConfirmed || !layoutSaved || !onPreparationComplete} aria-busy={preparationStarting} onClick={() => void completePreparation()}>{preparationStarting ? '모험 시작 요청 중…' : '맵 준비 완료, 모험 시작'}</button>}
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

function boundaryStrokeStyle(stroke: BoundaryGeometry, width: number, height: number): CSSProperties {
  const start = Math.min(stroke.from, stroke.to)
  const length = Math.abs(stroke.to - stroke.from) + 1
  return stroke.orientation === 'HORIZONTAL'
    ? { ...boundaryStyle({ x: start, y: stroke.fixed, orientation: 'HORIZONTAL' }, width, height), width: `${(length / width) * 100}%` }
    : { ...boundaryStyle({ x: stroke.fixed, y: start, orientation: 'VERTICAL' }, width, height), height: `${(length / height) * 100}%` }
}
