import { useEffect, useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import { Button } from '../../components/ui/button'
import { Input } from '../../components/ui/input'
import { finiteGridOrigin, refineCellSize, type ImagePoint, type MapGridAlignmentDraft } from './mapGridAlignmentGeometry'

export type AlignmentToSave = MapGridAlignmentDraft & { mapId: string; commandId: string; expectedVersion: number; imageRevision: string }
const MAGNIFIER_SCALE = 2.5
type Drag = { kind: 'new' | 'origin' | 'size'; anchor: ImagePoint; draft: MapGridAlignmentDraft; start: ImagePoint; moved: boolean }
type PendingMeasurement = { anchor: ImagePoint; previous: MapGridAlignmentDraft }
type PanDrag = { startX: number; startY: number; initialX: number; initialY: number }
export type AlignmentCrop = { x: number; y: number; width: number; height: number }

export function MapGridAlignmentEditor({ image, initial, crop, grid, onApply, onCancel }: { image: string; initial: MapGridAlignmentDraft & { mapId: string; version: number; imageRevision: string }; crop?: AlignmentCrop; grid?: { width: number; height: number }; onApply: (value: AlignmentToSave) => Promise<void>; onCancel: () => void }) {
  const [draft, setDraft] = useState<MapGridAlignmentDraft>(initial)
  const [sizeInput, setSizeInput] = useState(String(Number(initial.cellSize.toFixed(4))))
  const [zoom, setZoom] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const [panMode, setPanMode] = useState(false)
  const [magnifier, setMagnifier] = useState<ImagePoint | null>(null)
  const [pending, setPending] = useState<PendingMeasurement | null>(null)
  const [showFullGrid, setShowFullGrid] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [imageSize, setImageSize] = useState({ width: 1, height: 1 })
  const canvas = useRef<HTMLDivElement>(null)
  const commandId = useRef<string | null>(null)
  const drag = useRef<Drag | null>(null)
  const panDrag = useRef<PanDrag | null>(null)
  const pendingRef = useRef<PendingMeasurement | null>(null)
  const displayCrop = crop && crop.width > 0 && crop.height > 0 ? crop : { x: 0, y: 0, width: imageSize.width, height: imageSize.height }
  const saveOrigin = grid
    ? imageSize.width > 1 && imageSize.height > 1
      ? finiteGridOrigin(draft, { x: initial.originX, y: initial.originY }, grid, imageSize)
      : null
    : { x: draft.originX, y: draft.originY }
  const gridCannotFit = !!grid && imageSize.width > 1 && imageSize.height > 1 && !saveOrigin

  const layout = () => {
    const element = canvas.current
    const rect = element?.getBoundingClientRect()
    const width = Math.max(1, element?.clientWidth || rect?.width || displayCrop.width)
    const height = Math.max(1, element?.clientHeight || (crop && crop.width > 0 && crop.height > 0 ? width * crop.height / crop.width : rect?.height || displayCrop.height))
    const scale = Math.min(width / displayCrop.width, height / displayCrop.height)
    return { scale, offsetX: (width - displayCrop.width * scale) / 2, offsetY: (height - displayCrop.height * scale) / 2 }
  }
  const changeDraft = (next: MapGridAlignmentDraft) => { commandId.current = null; setDraft(next); setSizeInput(String(Number(next.cellSize.toFixed(4)))) }
  function point(event: PointerEvent<HTMLElement>) {
    const rect = canvas.current?.getBoundingClientRect()
    const view = layout()
    const screenX = event.clientX - (rect?.left ?? 0) - (canvas.current?.clientLeft ?? 0) - view.offsetX - pan.x
    const screenY = event.clientY - (rect?.top ?? 0) - (canvas.current?.clientTop ?? 0) - view.offsetY - pan.y
    return { x: displayCrop.x + screenX / (view.scale * zoom), y: displayCrop.y + screenY / (view.scale * zoom) }
  }
  function beginPanning(event: PointerEvent<HTMLDivElement>) {
    event.preventDefault()
    canvas.current?.setPointerCapture(event.pointerId)
    panDrag.current = { startX: event.clientX, startY: event.clientY, initialX: pan.x, initialY: pan.y }
    setMagnifier(null)
  }
  function beginGridSizing(event: PointerEvent<HTMLElement>, forcedKind?: Drag['kind']) {
    event.preventDefault()
    canvas.current?.setPointerCapture(event.pointerId)
    const at = point(event)
    const origin = { x: draft.originX, y: draft.originY }
    const existing = pendingRef.current
    const kind: Drag['kind'] = forcedKind ?? (existing ? 'size' : 'new')
    const anchor = kind === 'size' && existing ? existing.anchor : kind === 'size' ? origin : at
    if (imageSize.width > 1 && (kind === 'new' || (kind === 'size' && existing)) && (at.x < displayCrop.x || at.y < displayCrop.y || at.x > displayCrop.x + displayCrop.width || at.y > displayCrop.y + displayCrop.height)) {
      setError('잘린 지도 영역 안쪽에서 시작점을 찍으세요.')
      return
    }
    drag.current = { kind, anchor, draft: { ...draft }, start: at, moved: false }
    setMagnifier(at)
    if (kind === 'new') {
      pendingRef.current = { anchor: at, previous: { ...draft } }
      setPending(pendingRef.current)
      changeDraft({ ...draft, originX: at.x, originY: at.y })
    }
  }
  function move(event: PointerEvent<HTMLDivElement>) {
    if (panDrag.current) {
      const current = panDrag.current
      setPan(clampPan({ x: current.initialX + event.clientX - current.startX, y: current.initialY + event.clientY - current.startY }, displayCrop, canvas.current, zoom))
      return
    }
    const current = drag.current
    if (!current) return
    const at = point(event)
    const moved = current.moved || Math.hypot(at.x - current.start.x, at.y - current.start.y) > 2 / (layout().scale * zoom)
    drag.current = { ...current, moved }
    setMagnifier(at)
    if (!moved) return
    try {
      if (current.kind === 'origin') changeDraft({ ...current.draft, originX: current.draft.originX + at.x - current.start.x, originY: current.draft.originY + at.y - current.start.y })
      else changeDraft(refineCellSize(current.draft, { x: 0, y: 0 }, current.anchor, { x: 3, y: 3 }, at))
    } catch { /* Keep the last valid preview while the pointer crosses an invalid size. */ }
  }
  function finish(event: PointerEvent<HTMLDivElement>) {
    const current = drag.current
    try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* cancelled pointer */ }
    if (current?.moved) {
      const at = point(event)
      try {
        if (current.kind === 'origin') changeDraft({ ...current.draft, originX: current.draft.originX + at.x - current.start.x, originY: current.draft.originY + at.y - current.start.y })
        else changeDraft(refineCellSize(current.draft, { x: 0, y: 0 }, current.anchor, { x: 3, y: 3 }, at))
      } catch { setError('세 칸 뒤의 교차점을 다시 선택하세요.') }
    }
    if (current && !current.moved && current.kind === 'size') {
      const at = point(event)
      try {
        changeDraft(refineCellSize(draft, { x: 0, y: 0 }, current.anchor, { x: 3, y: 3 }, at))
        pendingRef.current = null
        setPending(null)
      } catch { setError('세 칸 뒤의 교차점을 다시 선택하세요.') }
    } else if (current && !current.moved && current.kind === 'new') {
      // The first click remains pending until a second point is selected.
    } else if (current?.kind === 'new') {
      pendingRef.current = null
      setPending(null)
    } else if (current?.kind === 'size') {
      pendingRef.current = null
      setPending(null)
    }
    drag.current = null
    panDrag.current = null
    setMagnifier(null)
  }
  function cancelPointer(event: PointerEvent<HTMLDivElement>) {
    const current = drag.current
    const pan = panDrag.current
    try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* cancelled pointer */ }
    if (current) {
      if (current.kind === 'new') {
        changeDraft(current.draft)
        pendingRef.current = null
        setPending(null)
      } else changeDraft(current.draft)
    }
    if (pan) setPan({ x: pan.initialX, y: pan.initialY })
    drag.current = null
    panDrag.current = null
    setMagnifier(null)
  }
  function cancelPending() {
    if (!pendingRef.current) return
    changeDraft(pendingRef.current.previous)
    pendingRef.current = null
    setPending(null)
    drag.current = null
    setMagnifier(null)
  }
  useEffect(() => {
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape' && pendingRef.current) { event.preventDefault(); cancelPending() } }
    window.addEventListener('keydown', escape)
    return () => window.removeEventListener('keydown', escape)
  })
  function nudge(dx: number, dy: number, event?: { shiftKey: boolean }) {
    const distance = event?.shiftKey ? 10 : 1
    changeDraft({ ...draft, originX: draft.originX + dx * distance, originY: draft.originY + dy * distance })
  }
  function changeZoom(delta: number) {
    setZoom(current => {
      const next = Math.min(4, Math.max(.5, current + delta))
      setPan(previous => clampPan(previous, displayCrop, canvas.current, next))
      return next
    })
  }
  async function apply() {
    if (!saveOrigin) { setError('현재 지도 크기와 격자 칸 수로는 전체 격자를 맞출 수 없습니다. 칸 크기를 줄이거나 기준점을 조정하세요.'); return }
    setSaving(true)
    setError('')
    try {
      commandId.current ??= globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
      await onApply({ ...draft, originX: saveOrigin.x, originY: saveOrigin.y, mapId: initial.mapId, commandId: commandId.current, expectedVersion: initial.version, imageRevision: initial.imageRevision })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '격자 맞추기를 저장하지 못했습니다.')
    } finally { setSaving(false) }
  }

  const view = layout()
  const toCanvas = (x: number, y: number) => ({ left: view.offsetX + pan.x + (x - displayCrop.x) * view.scale * zoom, top: view.offsetY + pan.y + (y - displayCrop.y) * view.scale * zoom })
  const origin = toCanvas(draft.originX, draft.originY)
  const scaleEnd = toCanvas(draft.originX + draft.cellSize * 3, draft.originY + draft.cellSize * 3)
  const imageScale = view.scale * zoom
  const cellPixels = draft.cellSize * imageScale
  const cropViewport = { left: view.offsetX + pan.x, top: view.offsetY + pan.y, width: displayCrop.width * imageScale, height: displayCrop.height * imageScale }
  const canvasStyle: CSSProperties | undefined = crop && crop.width > 0 && crop.height > 0 ? { aspectRatio: `${displayCrop.width} / ${displayCrop.height}` } : undefined
  const magnifierSize = 100
  const magnifierAt = magnifier && toCanvas(magnifier.x, magnifier.y)
  const numericSize = Number(sizeInput)
  const validSize = Number.isFinite(numericSize) && numericSize > 0

  return <section className="map-grid-alignment-editor" aria-label="맵 격자 맞추기">
    <p className="map-grid-alignment-hint">3×3칸의 대각선 양 끝 교차점을 차례로 선택하세요.</p>
    <div className="map-grid-alignment-controls">
      <div className="map-grid-alignment-toolbar" aria-label="지도 보기 조절">
        <Button type="button" onClick={() => changeZoom(.25)}>확대</Button><span aria-live="polite">{Math.round(zoom * 100)}%</span>
        <Button type="button" onClick={() => changeZoom(-.25)}>축소</Button>
        <Button type="button" onClick={() => { setZoom(1); setPan({ x: 0, y: 0 }) }}>맞춤</Button>
        <Button type="button" aria-pressed={panMode} onClick={() => { setPanMode(true); setMagnifier(null) }}>지도 이동</Button>
        <Button type="button" aria-pressed={!panMode} onClick={() => setPanMode(false)}>격자 맞추기</Button>
        <Button type="button" aria-pressed={showFullGrid} onClick={() => setShowFullGrid(value => !value)}>전체 격자</Button>
      </div>
      <div className="map-grid-alignment-adjustments">
        <label>한 칸 크기 <span>(원본 px)</span><Input aria-label="한 칸 크기 (원본 이미지 픽셀)" type="text" inputMode="decimal" value={sizeInput} aria-invalid={!validSize} onChange={event => { const text = event.target.value; setSizeInput(text); const value = Number(text); if (Number.isFinite(value) && value > 0) { commandId.current = null; setDraft({ ...draft, cellSize: value }) } }} /></label>
        <Button type="button" aria-label="한 칸 크기 줄이기" onClick={() => changeDraft({ ...draft, cellSize: Math.max(.01, draft.cellSize - 1) })}>−</Button>
        <Button type="button" aria-label="한 칸 크기 늘리기" onClick={() => changeDraft({ ...draft, cellSize: draft.cellSize + 1 })}>+</Button>
        <span className="map-grid-adjustment-label">원점</span>
        <div className="map-grid-nudge" role="group" aria-label="원점 미세 조정 (원본 이미지 픽셀)">
          <Button type="button" size="icon" aria-label="원점 위로 이동" onClick={event => nudge(0, -1, event)}>↑</Button><Button type="button" size="icon" aria-label="원점 왼쪽으로 이동" onClick={event => nudge(-1, 0, event)}>←</Button><Button type="button" size="icon" aria-label="원점 아래로 이동" onClick={event => nudge(0, 1, event)}>↓</Button><Button type="button" size="icon" aria-label="원점 오른쪽으로 이동" onClick={event => nudge(1, 0, event)}>→</Button>
        </div>
        <Button type="button" onClick={() => { pendingRef.current = null; setPending(null); changeDraft(initial); setError('') }}>초기값으로</Button>
      </div>
    </div>
    <div ref={canvas} style={canvasStyle} className={`map-grid-alignment-canvas${pending ? ' is-sizing' : ''}${panMode ? ' is-panning' : ''}`} onPointerDown={event => panMode ? beginPanning(event) : beginGridSizing(event)} onPointerMove={move} onPointerUp={finish} onPointerCancel={cancelPointer} onPointerLeave={() => !drag.current && !panDrag.current && setMagnifier(null)}>
      <div className="map-grid-alignment-image-viewport" aria-label="자른 지도 미리보기" data-crop={`${displayCrop.x},${displayCrop.y},${displayCrop.width},${displayCrop.height}`} style={{ ...cropViewport, overflow: 'hidden' }}>
        <img src={image} alt="공개된 지도 이미지" draggable={false} onLoad={event => { const loadedSize = { width: event.currentTarget.naturalWidth || 1, height: event.currentTarget.naturalHeight || 1 }; setImageSize(loadedSize); setPan(current => clampPan(current, crop && crop.width > 0 && crop.height > 0 ? crop : loadedSize, canvas.current, zoom)) }} style={{ width: imageSize.width, height: imageSize.height, maxWidth: 'none', maxHeight: 'none', transform: `translate(${-displayCrop.x * imageScale}px, ${-displayCrop.y * imageScale}px) scale(${imageScale})`, transformOrigin: '0 0' }} />
      </div>
      {showFullGrid && <div className="map-grid-alignment-full-grid" aria-hidden="true" style={{ ...cropViewport, '--alignment-cell-size': `${cellPixels}px`, '--alignment-origin-x': `${origin.left - cropViewport.left}px`, '--alignment-origin-y': `${origin.top - cropViewport.top}px` } as CSSProperties} />}
      <div className="map-grid-alignment-grid" aria-hidden="true" style={{ left: origin.left, top: origin.top, width: cellPixels * 3, height: cellPixels * 3, '--alignment-origin-x': '0px', '--alignment-origin-y': '0px', '--alignment-cell-size': `${cellPixels}px` } as CSSProperties} />
      <button type="button" className="map-grid-alignment-handle" aria-label="기준점 이동" style={{ left: origin.left, top: origin.top }} onKeyDown={event => { if (event.key.startsWith('Arrow')) { event.preventDefault(); nudge(event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0, event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0, event) } }} onPointerDown={event => { event.stopPropagation(); beginGridSizing(event, 'origin') }} />
      <button type="button" className="map-grid-alignment-handle map-grid-alignment-size-handle" aria-label="세 칸 뒤 기준점 이동" style={{ left: scaleEnd.left, top: scaleEnd.top }} onKeyDown={event => { if (event.key.startsWith('Arrow')) { event.preventDefault(); const distance = (event.shiftKey ? 10 : 1) / 3; const nextSize = draft.cellSize + (event.key === 'ArrowRight' || event.key === 'ArrowDown' ? distance : -distance); if (nextSize > 0) changeDraft({ ...draft, cellSize: nextSize }) } }} onPointerDown={event => { event.stopPropagation(); beginGridSizing(event, 'size') }} />
      {pending && <span className="map-grid-alignment-pending" role="status">두 번째 기준점을 선택하세요</span>}
      {magnifier && magnifierAt && <div className="map-grid-magnifier" aria-label="확대경" style={{ left: Math.max(4, Math.min(magnifierAt.left + 18, (canvas.current?.clientWidth ?? 1) - magnifierSize - 4)), top: Math.max(4, Math.min(magnifierAt.top - magnifierSize - 12, (canvas.current?.clientHeight ?? 1) - magnifierSize - 4)) }}><img src={image} alt="" aria-hidden="true" style={{ left: '50%', top: '50%', width: imageSize.width * MAGNIFIER_SCALE, height: imageSize.height * MAGNIFIER_SCALE, transform: `translate(${-magnifier.x * MAGNIFIER_SCALE}px, ${-magnifier.y * MAGNIFIER_SCALE}px)` }} /></div>}
    </div>
      {error && <p role="alert">{error}</p>}
      {grid && (imageSize.width <= 1 || imageSize.height <= 1) && <p role="status">지도 크기를 확인하고 있습니다.</p>}
      {gridCannotFit && <p role="alert">현재 지도 크기와 격자 칸 수로는 전체 격자를 맞출 수 없습니다. 칸 크기를 줄이거나 기준점을 조정하세요.</p>}
    <div className="map-grid-alignment-actions"><Button type="button" disabled={saving || !validSize || !!pending || !saveOrigin} onClick={() => void apply()}>{saving ? '저장 중…' : error ? '다시 적용' : '적용'}</Button><Button type="button" disabled={saving} onClick={onCancel}>취소</Button></div>
  </section>
}

function clampPan(value: { x: number; y: number }, imageSize: { width: number; height: number }, canvas: HTMLDivElement | null, zoom: number) {
  const rect = canvas?.getBoundingClientRect()
  const width = Math.max(1, canvas?.clientWidth || rect?.width || imageSize.width)
  const height = Math.max(1, canvas?.clientHeight || rect?.height || imageSize.height)
  const scale = Math.min(width / imageSize.width, height / imageSize.height)
  const limitX = Math.max(0, (imageSize.width * scale * zoom - width) / 2)
  const limitY = Math.max(0, (imageSize.height * scale * zoom - height) / 2)
  return { x: Math.min(limitX, Math.max(-limitX, value.x)), y: Math.min(limitY, Math.max(-limitY, value.y)) }
}
