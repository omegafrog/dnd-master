import { useRef, useState, type CSSProperties, type PointerEvent } from 'react'
import { imagePointFromScreen, refineCellSize, type ImagePoint, type MapGridAlignmentDraft } from './mapGridAlignmentGeometry'

export type AlignmentToSave = MapGridAlignmentDraft & { commandId: string; expectedVersion: number; imageRevision: string }
type Drag =
  | { kind: 'grid-size'; anchor: ImagePoint; draft: MapGridAlignmentDraft }
  | { kind: 'size'; draft: MapGridAlignmentDraft }

export function MapGridAlignmentEditor({ image, initial, onApply, onCancel }: { image: string; initial: MapGridAlignmentDraft & { version: number; imageRevision: string }; onApply: (value: AlignmentToSave) => Promise<void>; onCancel: () => void }) {
  const [step, setStep] = useState(0)
  const [draft, setDraft] = useState<MapGridAlignmentDraft>(initial)
  const [zoom, setZoom] = useState(1)
  const [magnifierEnabled, setMagnifierEnabled] = useState(false)
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
    if (step !== 0) return
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    const anchor = point(event)
    drag.current = { kind: 'grid-size', anchor, draft: { ...draft } }
    setSizing(true)
    setMagnifier(magnifierEnabled ? anchor : null)
    changeDraft({ ...draft, originX: anchor.x, originY: anchor.y })
  }
  function beginSizeSizing(event: PointerEvent<HTMLButtonElement>) {
    event.preventDefault()
    event.stopPropagation()
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { kind: 'size', draft: { ...draft } }
    setMagnifier(magnifierEnabled ? point(event) : null)
  }
  function move(event: PointerEvent<HTMLDivElement>) {
    setMagnifier(magnifierEnabled ? point(event) : null)
    const current = drag.current
    if (!current) return
    const next = point(event)
    if (current.kind === 'grid-size') {
      try {
        changeDraft(refineCellSize(current.draft, { x: 0, y: 0 }, current.anchor, { x: 3, y: 3 }, next))
      } catch {
        changeDraft({ ...current.draft, originX: current.anchor.x, originY: current.anchor.y })
      }
      return
    }
    const target = step === 2 ? { x: 4, y: 4 } : { x: 1, y: 0 }
    try {
      changeDraft(refineCellSize(current.draft, { x: 0, y: 0 }, { x: current.draft.originX, y: current.draft.originY }, target, next))
    } catch { /* retain valid draft */ }
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
      await onApply({ ...draft, commandId: commandId.current, expectedVersion: initial.version, imageRevision: initial.imageRevision })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '격자 맞추기를 저장하지 못했습니다.')
    } finally { setSaving(false) }
  }

  const labels = ['3×3 격자 크기', '한 칸 크기', '미세 조정']
  const view = layout()
  const target = step === 2 ? { x: 4, y: 4 } : { x: 1, y: 0 }
  const toCanvas = (x: number, y: number) => ({ left: view.offsetX + x * view.scale * zoom, top: view.offsetY + y * view.scale * zoom })
  const origin = toCanvas(draft.originX, draft.originY)
  const cellPixels = draft.cellSize * view.scale * zoom

  return <section className="map-grid-alignment-editor" aria-label="맵 격자 맞추기">
    <p><strong>{labels[step]}</strong> — {step === 0 ? '지도에서 시작점 하나를 누른 채 세 칸 뒤 대각 교차점까지 끌어 3×3 격자 크기를 맞추세요.' : step === 1 ? '표시된 한 칸을 지도 한 칸에 맞추세요.' : '먼 교차점을 맞춰 누적 오차를 줄이세요.'}</p>
    <div className="map-grid-alignment-toolbar">
      <button type="button" onClick={() => setZoom(value => Math.min(4, value + .25))}>확대</button>
      <button type="button" onClick={() => setZoom(value => Math.max(.5, value - .25))}>축소</button>
      <button type="button" aria-pressed={magnifierEnabled} onClick={() => { setMagnifierEnabled(value => !value); setMagnifier(null) }}>{magnifierEnabled ? '확대경 끄기' : '확대경 켜기'}</button>
      <button type="button" onClick={() => setStep(value => Math.max(0, value - 1))} disabled={step === 0}>이전</button>
      <button type="button" onClick={() => setStep(value => Math.min(2, value + 1))} disabled={step === 2}>다음</button>
    </div>
    <div ref={canvas} className={`map-grid-alignment-canvas${sizing ? ' is-sizing' : ''}`} onPointerDown={beginGridSizing} onPointerMove={move} onPointerUp={finish} onPointerCancel={finish} onPointerLeave={() => !drag.current && setMagnifier(null)}>
      <img src={image} alt="공개된 지도 이미지" draggable={false} onLoad={event => setImageSize({ width: event.currentTarget.naturalWidth || 1, height: event.currentTarget.naturalHeight || 1 })} style={{ width: imageSize.width, height: imageSize.height, maxWidth: 'none', maxHeight: 'none', transform: `translate(${view.offsetX}px, ${view.offsetY}px) scale(${view.scale * zoom})`, transformOrigin: '0 0' }} />
      <div className="map-grid-alignment-grid" style={{ left: origin.left, top: origin.top, width: cellPixels * 3, height: cellPixels * 3, '--alignment-origin-x': '0px', '--alignment-origin-y': '0px', '--alignment-cell-size': `${cellPixels}px` } as CSSProperties} />
      <div aria-hidden="true" className="map-grid-reference" style={origin} />
      {step > 0 && <button type="button" aria-label={step === 2 ? '먼 교차점 조절' : '한 칸 크기 조절'} className="map-grid-size" style={toCanvas(draft.originX + draft.cellSize * target.x, draft.originY + draft.cellSize * target.y)} onPointerDown={beginSizeSizing}>↔</button>}
      {magnifier && <div className="map-grid-magnifier" aria-label="확대경" style={{ left: Math.min(Math.max(toCanvas(magnifier.x, magnifier.y).left + 20, 8), 220), top: Math.min(Math.max(toCanvas(magnifier.x, magnifier.y).top - 120, 8), 180), backgroundImage: `url(${image})`, backgroundPosition: `${-magnifier.x * 4 + 50}px ${-magnifier.y * 4 + 50}px` }} />}
    </div>
    <label>한 칸 크기<input aria-label="한 칸 크기" type="number" min="0.01" step="0.01" value={draft.cellSize} onChange={event => changeDraft({ ...draft, cellSize: Number(event.target.value) })} /></label>
    {error && <p role="alert">{error}</p>}
    <button type="button" disabled={saving || draft.cellSize <= 0} onClick={() => void apply()}>{saving ? '저장 중…' : error ? '다시 적용' : '적용'}</button>
    <button type="button" disabled={saving} onClick={onCancel}>취소</button>
  </section>
}
