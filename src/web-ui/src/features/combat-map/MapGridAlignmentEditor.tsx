import { useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import { imagePointFromScreen, refineCellSize, type ImagePoint, type MapGridAlignmentDraft } from './mapGridAlignmentGeometry'

export type AlignmentToSave = MapGridAlignmentDraft & { mapId: string; commandId: string; expectedVersion: number; imageRevision: string }
const MAGNIFIER_SCALE = 2.5
type Drag =
  { anchor: ImagePoint; draft: MapGridAlignmentDraft }

export function MapGridAlignmentEditor({ image, initial, onApply, onCancel }: { image: string; initial: MapGridAlignmentDraft & { mapId: string; version: number; imageRevision: string }; onApply: (value: AlignmentToSave) => Promise<void>; onCancel: () => void }) {
  const [draft, setDraft] = useState<MapGridAlignmentDraft>(initial)
  const [zoom, setZoom] = useState(1)
  const [magnifier, setMagnifier] = useState<ImagePoint | null>(null)
  const [sizing, setSizing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [imageSize, setImageSize] = useState({ width: 1, height: 1 })
  const canvas = useRef<HTMLDivElement>(null)
  const commandId = useRef<string | null>(null)
  const drag = useRef<Drag | null>(null)

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
    const transformed = imagePointFromScreen({ x: event.clientX - (rect?.left ?? 0) - view.offsetX, y: event.clientY - (rect?.top ?? 0) - view.offsetY }, { zoom, panX: 0, panY: 0 })
    return { x: transformed.x / view.scale, y: transformed.y / view.scale }
  }
  function beginGridSizing(event: PointerEvent<HTMLDivElement>) {
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    const anchor = point(event)
    drag.current = { anchor, draft: { ...draft } }
    setSizing(true)
    setMagnifier(anchor)
    changeDraft({ ...draft, originX: anchor.x, originY: anchor.y })
  }
  function move(event: PointerEvent<HTMLDivElement>) {
    const current = drag.current
    if (!current) return
    const next = point(event)
    setMagnifier(next)
    try {
      changeDraft(refineCellSize(current.draft, { x: 0, y: 0 }, current.anchor, { x: 3, y: 3 }, next))
    } catch {
      changeDraft({ ...current.draft, originX: current.anchor.x, originY: current.anchor.y })
    }
  }
  function finish(event: PointerEvent<HTMLDivElement>) {
    try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* cancelled pointer */ }
    drag.current = null
    setSizing(false)
    setMagnifier(null)
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
  const toCanvas = (x: number, y: number) => ({ left: view.offsetX + x * view.scale * zoom, top: view.offsetY + y * view.scale * zoom })
  const origin = toCanvas(draft.originX, draft.originY)
  const cellPixels = draft.cellSize * view.scale * zoom

  return <section className="map-grid-alignment-editor" aria-label="맵 격자 맞추기">
    <p><strong>3×3 격자 맞추기</strong> — 지도에서 시작점 하나를 누른 채 세 칸 뒤 대각 교차점까지 끌어 격자를 맞추세요.</p>
    <div className="map-grid-alignment-toolbar">
      <button type="button" onClick={() => setZoom(value => Math.min(4, value + .25))}>확대</button>
      <button type="button" onClick={() => setZoom(value => Math.max(.5, value - .25))}>축소</button>
    </div>
    <div ref={canvas} className={`map-grid-alignment-canvas${sizing ? ' is-sizing' : ''}`} onPointerDown={beginGridSizing} onPointerMove={move} onPointerUp={finish} onPointerCancel={finish} onPointerLeave={() => !drag.current && setMagnifier(null)}>
      <img src={image} alt="공개된 지도 이미지" draggable={false} onLoad={event => setImageSize({ width: event.currentTarget.naturalWidth || 1, height: event.currentTarget.naturalHeight || 1 })} style={{ width: imageSize.width, height: imageSize.height, maxWidth: 'none', maxHeight: 'none', transform: `translate(${view.offsetX}px, ${view.offsetY}px) scale(${view.scale * zoom})`, transformOrigin: '0 0' }} />
      <div className="map-grid-alignment-grid" style={{ left: origin.left, top: origin.top, width: cellPixels * 3, height: cellPixels * 3, '--alignment-origin-x': '0px', '--alignment-origin-y': '0px', '--alignment-cell-size': `${cellPixels}px` } as CSSProperties} />
      {magnifier && <div className="map-grid-magnifier" aria-label="확대경" style={{ left: Math.min(Math.max(toCanvas(magnifier.x, magnifier.y).left + 20, 8), 220), top: Math.min(Math.max(toCanvas(magnifier.x, magnifier.y).top - 120, 8), 180) }}><img src={image} alt="" aria-hidden="true" style={{ width: imageSize.width * MAGNIFIER_SCALE, height: imageSize.height * MAGNIFIER_SCALE, transform: `translate(${50 - magnifier.x * MAGNIFIER_SCALE}px, ${50 - magnifier.y * MAGNIFIER_SCALE}px)` }} /></div>}
    </div>
    {error && <p role="alert">{error}</p>}
    <button type="button" disabled={saving || draft.cellSize <= 0} onClick={() => void apply()}>{saving ? '저장 중…' : error ? '다시 적용' : '적용'}</button>
    <button type="button" disabled={saving} onClick={onCancel}>취소</button>
  </section>
}
