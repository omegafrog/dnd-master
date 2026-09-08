import { useRef, useState, type CSSProperties, type PointerEvent } from 'react'

type Crop = { x: number; y: number; width: number; height: number }
const MAGNIFIER_SCALE = 2.5

export function MapCropEditor({ image, crop, onChange }: { image: string; crop: Crop; onChange: (crop: Crop) => void }) {
  const [imageSize, setImageSize] = useState({ width: 1, height: 1 })
  const [magnifier, setMagnifier] = useState<{ x: number; y: number } | null>(null)
  const canvas = useRef<HTMLDivElement>(null)
  const anchor = useRef<{ x: number; y: number } | null>(null)

  const view = () => {
    const rect = canvas.current?.getBoundingClientRect()
    const width = Math.max(1, rect?.width ?? imageSize.width)
    const height = Math.max(1, rect?.height ?? imageSize.height)
    const scale = Math.min(width / imageSize.width, height / imageSize.height)
    return { scale, offsetX: (width - imageSize.width * scale) / 2, offsetY: (height - imageSize.height * scale) / 2 }
  }
  const point = (event: PointerEvent<HTMLDivElement>) => {
    const rect = canvas.current?.getBoundingClientRect()
    const layout = view()
    return {
      x: Math.min(imageSize.width, Math.max(0, (event.clientX - (rect?.left ?? 0) - layout.offsetX) / layout.scale)),
      y: Math.min(imageSize.height, Math.max(0, (event.clientY - (rect?.top ?? 0) - layout.offsetY) / layout.scale)),
    }
  }
  const update = (end: { x: number; y: number }) => {
    if (!anchor.current) return
    const x = Math.min(anchor.current.x, end.x)
    const y = Math.min(anchor.current.y, end.y)
    onChange({ x: Math.round(x), y: Math.round(y), width: Math.max(1, Math.round(Math.abs(end.x - anchor.current.x))), height: Math.max(1, Math.round(Math.abs(end.y - anchor.current.y))) })
  }
  const layout = view()
  const cropStyle = { left: layout.offsetX + crop.x * layout.scale, top: layout.offsetY + crop.y * layout.scale, width: crop.width * layout.scale, height: crop.height * layout.scale } as CSSProperties
  const magnifierPosition = magnifier && { left: Math.min(Math.max(layout.offsetX + magnifier.x * layout.scale + 20, 8), 220), top: Math.min(Math.max(layout.offsetY + magnifier.y * layout.scale - 120, 8), 180) }

  return <section className="map-crop-editor" aria-label="지도 자르기">
    <p>남길 지도의 왼쪽 위 모서리에서 오른쪽 아래 모서리까지 끌어 자를 영역을 정하세요.</p>
    <div ref={canvas} className="map-crop-canvas" aria-label="맵 자르기" onPointerDown={event => { event.preventDefault(); event.currentTarget.setPointerCapture(event.pointerId); anchor.current = point(event); setMagnifier(anchor.current); update(anchor.current) }} onPointerMove={event => { const next = point(event); if (anchor.current) setMagnifier(next); update(next) }} onPointerUp={event => { update(point(event)); anchor.current = null; setMagnifier(null); try { event.currentTarget.releasePointerCapture(event.pointerId) } catch { /* cancelled pointer */ } }} onPointerCancel={() => { anchor.current = null; setMagnifier(null) }}>
      <img src={image} alt="자르기 대상 지도" draggable={false} onLoad={event => setImageSize({ width: event.currentTarget.naturalWidth || 1, height: event.currentTarget.naturalHeight || 1 })} style={{ width: imageSize.width, height: imageSize.height, transform: `translate(${layout.offsetX}px, ${layout.offsetY}px) scale(${layout.scale})`, transformOrigin: '0 0' }} />
      <div className="map-crop-selection" aria-hidden="true" style={cropStyle} />
      {magnifier && <div className="map-crop-magnifier" aria-label="확대경" style={magnifierPosition ?? undefined}><img src={image} alt="" aria-hidden="true" style={{ width: imageSize.width * MAGNIFIER_SCALE, height: imageSize.height * MAGNIFIER_SCALE, transform: `translate(${50 - magnifier.x * MAGNIFIER_SCALE}px, ${50 - magnifier.y * MAGNIFIER_SCALE}px)` }} /></div>}
    </div>
    <button type="button" onClick={() => onChange({ x: 0, y: 0, width: imageSize.width, height: imageSize.height })}>전체 지도</button>
  </section>
}
