import { useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import { refineCellSize, type ImagePoint, type MapGridAlignmentDraft } from './mapGridAlignmentGeometry'

export type AlignmentToSave = MapGridAlignmentDraft & { mapId: string; commandId: string; expectedVersion: number; imageRevision: string }
const MAGNIFIER_SCALE = 2.5
type Drag =
  { anchor: ImagePoint; draft: MapGridAlignmentDraft }
type PanDrag = { startX: number; startY: number; initialX: number; initialY: number }

export function MapGridAlignmentEditor({ image, initial, gridWidth = 20, gridHeight = 20, onApply, onCancel }: { image: string; initial: MapGridAlignmentDraft & { mapId: string; version: number; imageRevision: string }; gridWidth?: number; gridHeight?: number; onApply: (value: AlignmentToSave) => Promise<void>; onCancel: () => void }) {
  const [draft, setDraft] = useState<MapGridAlignmentDraft>(initial)
  const [zoom, setZoom] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const [panMode, setPanMode] = useState(false)
  const [magnifier, setMagnifier] = useState<ImagePoint | null>(null)
  const [sizing, setSizing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [imageSize, setImageSize] = useState({ width: 1, height: 1 })
  const canvas = useRef<HTMLDivElement>(null)
  const commandId = useRef<string | null>(null)
  const drag = useRef<Drag | null>(null)
  const panDrag = useRef<PanDrag | null>(null)

  const layout = () => {
    const rect = canvas.current?.getBoundingClientRect()
    const width = Math.max(1, rect?.width ?? imageSize.width)
    const height = Math.max(1, rect?.height ?? imageSize.height)
    const scale = Math.min(width / imageSize.width, height / imageSize.height)
    return { scale, offsetX: (width - imageSize.width * scale) / 2, offsetY: (height - imageSize.height * scale) / 2 }
  }
  const changeDraft = (next: MapGridAlignmentDraft) => { commandId.current = null; setDraft(next) }
  function point(event: PointerEvent<HTMLElement>) {
    const rect = canvas.current?.getBoundingClientRect()
    const view = layout()
    const screenX = event.clientX - (rect?.left ?? 0) - view.offsetX - pan.x
    const screenY = event.clientY - (rect?.top ?? 0) - view.offsetY - pan.y
    return { x: screenX / (view.scale * zoom), y: screenY / (view.scale * zoom) }
  }
  function beginPanning(event: PointerEvent<HTMLDivElement>) {
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    panDrag.current = { startX: event.clientX, startY: event.clientY, initialX: pan.x, initialY: pan.y }
    setMagnifier(null)
  }
  function beginGridSizing(event: PointerEvent<HTMLDivElement>) {
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    const anchor = point(event)
    if (imageSize.width > 1 && (anchor.x < 0 || anchor.y < 0 || anchor.x > imageSize.width || anchor.y > imageSize.height)) {
      setError('지도 이미지 안쪽에서 시작점을 찍으세요.')
      return
    }
    const maxCellSize = maxCellThatFits(anchor, imageSize, gridWidth, gridHeight)
    if (maxCellSize <= 0) {
      setError('전체 격자가 들어갈 수 있는 지도 안쪽에서 시작점을 찍으세요.')
      return
    }
    const startingDraft = { ...draft, originX: anchor.x, originY: anchor.y, cellSize: Math.min(draft.cellSize, maxCellSize) }
    drag.current = { anchor, draft: { ...draft } }
    setSizing(true)
    setMagnifier(anchor)
    changeDraft(startingDraft)
  }
  function move(event: PointerEvent<HTMLDivElement>) {
    if (panDrag.current) {
      const current = panDrag.current
      setPan(clampPan({ x: current.initialX + event.clientX - current.startX, y: current.initialY + event.clientY - current.startY }, imageSize, canvas.current, zoom))
      return
    }
    const current = drag.current
    if (!current) return
    const next = point(event)
    setMagnifier(next)
    try {
      const refined = refineCellSize(current.draft, { x: 0, y: 0 }, current.anchor, { x: 3, y: 3 }, next)
      const maxCellSize = maxCellThatFits(current.anchor, imageSize, gridWidth, gridHeight)
      changeDraft({ ...refined, cellSize: Math.min(refined.cellSize, maxCellSize) })
    } catch {
      changeDraft({ ...current.draft, originX: current.anchor.x, originY: current.anchor.y })
    }
  }
  function finish(event: PointerEvent<HTMLDivElement>) {
    try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* cancelled pointer */ }
    drag.current = null
    panDrag.current = null
    setSizing(false)
    setMagnifier(null)
  }
  function changeZoom(delta: number) {
    setZoom(current => {
      const next = Math.min(4, Math.max(.5, current + delta))
      setPan(previous => clampPan(previous, imageSize, canvas.current, next))
      return next
    })
  }
  async function apply() {
    setSaving(true)
    setError('')
    try {
      commandId.current ??= globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
      await onApply({ ...draft, mapId: initial.mapId, commandId: commandId.current, expectedVersion: initial.version, imageRevision: initial.imageRevision })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '격자 맞추기를 저장하지 못했습니다.')
    } finally { setSaving(false) }
  }

  const view = layout()
  const toCanvas = (x: number, y: number) => ({ left: view.offsetX + pan.x + x * view.scale * zoom, top: view.offsetY + pan.y + y * view.scale * zoom })
  const origin = toCanvas(draft.originX, draft.originY)
  const cellPixels = draft.cellSize * view.scale * zoom

  return <section className="map-grid-alignment-editor" aria-label="맵 격자 맞추기">
    <p><strong>3×3 격자 맞추기</strong> — 지도에서 시작점 하나를 누른 채 세 칸 뒤 대각 교차점까지 끌어 격자를 맞추세요.</p>
    <div className="map-grid-alignment-toolbar">
      <button type="button" onClick={() => changeZoom(.25)}>확대</button>
      <button type="button" onClick={() => changeZoom(-.25)}>축소</button>
      <button type="button" aria-pressed={panMode} onClick={() => { setPanMode(true); setMagnifier(null) }}>지도 이동</button>
      <button type="button" aria-pressed={!panMode} onClick={() => setPanMode(false)}>격자 맞추기</button>
    </div>
    <div ref={canvas} className={`map-grid-alignment-canvas${sizing ? ' is-sizing' : ''}${panMode ? ' is-panning' : ''}`} onPointerDown={event => panMode ? beginPanning(event) : beginGridSizing(event)} onPointerMove={move} onPointerUp={finish} onPointerCancel={finish} onPointerLeave={() => !drag.current && !panDrag.current && setMagnifier(null)}>
      <img src={image} alt="공개된 지도 이미지" draggable={false} onLoad={event => { const loadedSize = { width: event.currentTarget.naturalWidth || 1, height: event.currentTarget.naturalHeight || 1 }; setImageSize(loadedSize); setPan(current => clampPan(current, loadedSize, canvas.current, zoom)) }} style={{ width: imageSize.width, height: imageSize.height, maxWidth: 'none', maxHeight: 'none', transform: `translate(${view.offsetX + pan.x}px, ${view.offsetY + pan.y}px) scale(${view.scale * zoom})`, transformOrigin: '0 0' }} />
      <div className="map-grid-alignment-grid" style={{ left: origin.left, top: origin.top, width: cellPixels * 3, height: cellPixels * 3, '--alignment-origin-x': '0px', '--alignment-origin-y': '0px', '--alignment-cell-size': `${cellPixels}px` } as CSSProperties} />
      {magnifier && <div className="map-grid-magnifier" aria-label="확대경" style={{ left: Math.min(Math.max(toCanvas(magnifier.x, magnifier.y).left + 20, 8), 220), top: Math.min(Math.max(toCanvas(magnifier.x, magnifier.y).top - 120, 8), 180) }}><img src={image} alt="" aria-hidden="true" style={{ width: imageSize.width * MAGNIFIER_SCALE, height: imageSize.height * MAGNIFIER_SCALE, transform: `translate(${50 - magnifier.x * MAGNIFIER_SCALE}px, ${50 - magnifier.y * MAGNIFIER_SCALE}px)` }} /></div>}
    </div>
    {error && <p role="alert">{error}</p>}
    <button type="button" disabled={saving || draft.cellSize <= 0} onClick={() => void apply()}>{saving ? '저장 중…' : error ? '다시 적용' : '적용'}</button>
    <button type="button" disabled={saving} onClick={onCancel}>취소</button>
  </section>
}

function clampPan(value: { x: number; y: number }, imageSize: { width: number; height: number }, canvas: HTMLDivElement | null, zoom: number) {
  const rect = canvas?.getBoundingClientRect()
  const width = Math.max(1, rect?.width ?? imageSize.width)
  const height = Math.max(1, rect?.height ?? imageSize.height)
  const scale = Math.min(width / imageSize.width, height / imageSize.height)
  const limitX = Math.max(0, (imageSize.width * scale * zoom - width) / 2)
  const limitY = Math.max(0, (imageSize.height * scale * zoom - height) / 2)
  return { x: Math.min(limitX, Math.max(-limitX, value.x)), y: Math.min(limitY, Math.max(-limitY, value.y)) }
}

function maxCellThatFits(anchor: ImagePoint, imageSize: { width: number; height: number }, gridWidth: number, gridHeight: number) {
  if (imageSize.width <= 1 || imageSize.height <= 1) return Number.POSITIVE_INFINITY
  return Math.min((imageSize.width - anchor.x) / Math.max(1, gridWidth), (imageSize.height - anchor.y) / Math.max(1, gridHeight))
}
