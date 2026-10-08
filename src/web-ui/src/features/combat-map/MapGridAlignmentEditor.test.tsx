import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { MapGridAlignmentEditor } from './MapGridAlignmentEditor'

const initial = { mapId: 'map-1', version: 3, imageRevision: 'image-v1', originX: 12.25, originY: 8.5, cellSize: 31.75 }

it('shows only the map area saved by the previous crop step', () => {
  render(<MapGridAlignmentEditor image="/public.png" crop={{ x: 100, y: 50, width: 300, height: 200 }} initial={initial} onApply={vi.fn()} onCancel={() => {}} />)

  const image = screen.getByAltText('공개된 지도 이미지')
  const viewport = image.parentElement!
  const canvas = viewport.closest('.map-grid-alignment-canvas')!
  expect(canvas).toHaveStyle({ aspectRatio: '300 / 200' })
  expect(viewport).toHaveClass('map-grid-alignment-image-viewport')
  expect(viewport).toHaveStyle({ width: '300px', height: '200px', overflow: 'hidden' })
  expect(viewport).toHaveAttribute('data-crop', '100,50,300,200')
})

it('keeps the 3×3 alignment draft local until explicit apply', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  const cancel = vi.fn()
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={cancel} />)

  expect(screen.getByRole('button', { name: '전체 격자' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.queryByRole('button', { name: /확대경/ })).not.toBeInTheDocument()
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 40, clientY: 80 }))
  expect(screen.getByLabelText('확대경')).toBeInTheDocument()
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 40, clientY: 80 }))
  expect(screen.queryByLabelText('확대경')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('한 칸 크기')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '다음' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '취소' }))
  expect(cancel).toHaveBeenCalledOnce()
  expect(apply).not.toHaveBeenCalled()
})

it('measures from two clicks and keeps the chosen origin while setting size', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })

  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 90, clientY: 80 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 90, clientY: 80 }))
  expect(screen.getByRole('button', { name: '적용' })).toBeDisabled()
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 150, clientY: 140 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 150, clientY: 140 }))
  await userEvent.setup().click(screen.getByRole('button', { name: '적용' }))

  expect(apply.mock.calls[0][0].originX).toBeCloseTo(.2)
  expect(apply.mock.calls[0][0].originY).toBeCloseTo(.4)
  expect(apply.mock.calls[0][0].cellSize).toBeCloseTo(.1)
})

it('limits the displayed measured size to four decimals without rounding the saved value', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 300 })
  Object.defineProperty(image, 'naturalHeight', { value: 200 })
  fireEvent.load(image)
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 10, clientY: 10 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 10, clientY: 10 }))
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 40, clientY: 41 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 40, clientY: 41 }))
  const size = screen.getByRole('textbox', { name: '한 칸 크기 (원본 이미지 픽셀)' })
  expect(size).toHaveValue('10.1667')
  await userEvent.setup().click(screen.getByRole('button', { name: '적용' }))
  expect(apply.mock.calls[0][0].cellSize).toBeCloseTo(10.1666666667, 8)
})

it('cancels a pending first point with Escape and restores the draft', async () => {
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={vi.fn()} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 90, clientY: 80 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 90, clientY: 80 }))
  fireEvent.keyDown(window, { key: 'Escape' })
  expect(screen.getByRole('button', { name: '적용' })).toBeEnabled()
})

it('restores the pre-measurement draft when the pointer is cancelled', () => {
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={vi.fn()} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 90, clientY: 80 }))
  fireEvent(canvas, new MouseEvent('pointercancel', { bubbles: true }))
  expect(screen.getByRole('button', { name: '적용' })).toBeEnabled()
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

it('does not begin a measurement outside the saved crop', () => {
  render(<MapGridAlignmentEditor image="/public.png" crop={{ x: 100, y: 50, width: 300, height: 200 }} initial={initial} onApply={vi.fn()} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 1000 })
  Object.defineProperty(image, 'naturalHeight', { value: 500 })
  fireEvent.load(image)
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: -10, clientY: 20 }))
  expect(screen.getByRole('alert')).toHaveTextContent('잘린 지도 영역 안쪽')
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

it('adjusts the origin without changing scale and resets to the supplied alignment', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)
  await user.click(screen.getByRole('button', { name: '원점 오른쪽으로 이동' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  expect(apply.mock.calls[0][0]).toMatchObject({ originX: initial.originX + 1, originY: initial.originY, cellSize: initial.cellSize })
  await user.click(screen.getByRole('button', { name: '초기값으로' }))
  await user.click(screen.getByRole('button', { name: '적용' }))
  expect(apply.mock.calls[1][0]).toMatchObject(initial)
})

it('does not save an invalid numeric cell size', async () => {
  const apply = vi.fn()
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)
  await user.clear(screen.getByRole('textbox', { name: '한 칸 크기 (원본 이미지 픽셀)' }))
  expect(screen.getByRole('button', { name: '적용' })).toBeDisabled()
  expect(apply).not.toHaveBeenCalled()
})

it('keeps typed decimal formatting while updating the draft size', () => {
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={vi.fn()} onCancel={() => {}} />)
  const input = screen.getByRole('textbox', { name: '한 칸 크기 (원본 이미지 픽셀)' })
  fireEvent.change(input, { target: { value: '31.50' } })
  expect(input).toHaveValue('31.50')
})

it('moves the anchor handle while preserving cell size even when both handles are close', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={{ ...initial, originX: 10, originY: 10, cellSize: 2 }} onApply={apply} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 300 })
  Object.defineProperty(image, 'naturalHeight', { value: 200 })
  fireEvent.load(image)

  const handle = screen.getByRole('button', { name: '기준점 이동' })
  fireEvent(handle, new MouseEvent('pointerdown', { bubbles: true, clientX: 12, clientY: 10 }))
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 15, clientY: 14 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 15, clientY: 14 }))
  await userEvent.setup().click(screen.getByRole('button', { name: '적용' }))
  expect(apply.mock.calls[0][0]).toMatchObject({ originX: 13, originY: 14, cellSize: 2 })
})

it('derives the grid origin and cell size from the dragged 3×3 area', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)

  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })

  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 40, clientY: 80 }))
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 100, clientY: 140 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 100, clientY: 140 }))
  await userEvent.setup().click(screen.getByRole('button', { name: '적용' }))

  const saved = apply.mock.calls[0][0]
  expect(saved.originX).toBeCloseTo(-.05)
  expect(saved.originY).toBeCloseTo(.4)
  expect(saved.cellSize).toBeCloseTo(.1)
})

it('uses the release coordinate when it arrives after the last pointermove', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 300 })
  Object.defineProperty(image, 'naturalHeight', { value: 200 })
  fireEvent.load(image)
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 40, clientY: 40 }))
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 80, clientY: 80 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 100, clientY: 100 }))
  await userEvent.setup().click(screen.getByRole('button', { name: '적용' }))
  expect(apply.mock.calls[0][0].cellSize).toBeCloseTo(20)
})

it('does not cap the 3×3 calibration size to the full map dimensions', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)

  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 300 })
  Object.defineProperty(image, 'naturalHeight', { value: 200 })
  fireEvent.load(image)

  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 40, clientY: 40 }))
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 160, clientY: 160 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 160, clientY: 160 }))
  await userEvent.setup().click(screen.getByRole('button', { name: '적용' }))

  expect(apply.mock.calls[0][0].cellSize).toBeCloseTo(40)
})

it('exposes retry after a save error', async () => {
  const apply = vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(undefined)
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)

  await user.click(screen.getByRole('button', { name: '적용' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('network')
  await user.click(screen.getByRole('button', { name: '다시 적용' }))
  expect(apply).toHaveBeenLastCalledWith(expect.objectContaining({ mapId: 'map-1', cellSize: 31.75, expectedVersion: 3, imageRevision: 'image-v1' }))
})

it('can move the zoomed map without changing the alignment drag mode', async () => {
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={vi.fn()} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 600 })
  Object.defineProperty(image, 'naturalHeight', { value: 400 })
  fireEvent.load(image)

  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: '확대' }))
  await user.click(screen.getByRole('button', { name: '지도 이동' }))
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 100, clientY: 80 }))
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 140, clientY: 110 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 140, clientY: 110 }))

  const viewport = image.parentElement!
  expect(viewport.getAttribute('style')).toContain('left: 37.5px;')
  expect(viewport.getAttribute('style')).toContain('top: 25px;')
})

it('converts a grid drag through the zoomed image coordinates', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 300, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 600 })
  Object.defineProperty(image, 'naturalHeight', { value: 400 })
  fireEvent.load(image)

  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: '확대' }))
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 75, clientY: 50 }))
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 100, clientY: 75 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 100, clientY: 75 }))
  await user.click(screen.getByRole('button', { name: '적용' }))

  const saved = apply.mock.calls[0][0]
  expect(saved.originX).toBeCloseTo(120)
  expect(saved.originY).toBeCloseTo(80)
  expect(saved.cellSize).toBeCloseTo(13.3333)
})

it('keeps crop and zoomed pointer samples on their source image coordinates', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" crop={{ x: 300, y: 100, width: 500, height: 400 }} initial={{ ...initial, originX: 600, originY: 500, cellSize: 36 }} onApply={apply} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').closest('.map-grid-alignment-canvas')!
  const image = screen.getByAltText('공개된 지도 이미지')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 100, top: 200, width: 672, height: 538 } as DOMRect)
  Object.defineProperties(canvas, { clientLeft: { value: 1 }, clientTop: { value: 1 }, clientWidth: { value: 670 }, clientHeight: { value: 536 } })
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })
  Object.defineProperty(image, 'naturalWidth', { value: 1403 })
  Object.defineProperty(image, 'naturalHeight', { value: 992 })
  fireEvent.load(image)
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: '확대' }))
  await user.click(screen.getByRole('button', { name: '확대' }))
  await user.click(screen.getByRole('button', { name: '지도 이동' }))
  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 300, clientY: 400 }))
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 324, clientY: 384 }))
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 324, clientY: 384 }))
  await user.click(screen.getByRole('button', { name: '격자 맞추기' }))

  const sample = (x: number, y: number) => {
    const at = { clientX: 100 + 1 + 24 + (x - 300) * 2.01, clientY: 200 + 1 - 16 + (y - 100) * 2.01 }
    fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, ...at }))
    return () => fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, ...at }))
  }
  const releaseFirst = sample(419.5, 216.5)
  const magnifierImage = screen.getByLabelText('확대경').querySelector('img')!
  const translation = magnifierImage.style.transform.match(/translate\((-?[\d.]+)px, (-?[\d.]+)px\)/)!
  expect(magnifierImage).toHaveStyle({ left: '50%', top: '50%' })
  expect(Number(translation[1])).toBeCloseTo(-1048.75, 4)
  expect(Number(translation[2])).toBeCloseTo(-541.25, 4)
  releaseFirst()
  sample(527.5, 324.5)()
  await user.click(screen.getByRole('button', { name: '적용' }))
  expect(apply.mock.calls[0][0].originX).toBeCloseTo(419.5, 3)
  expect(apply.mock.calls[0][0].originY).toBeCloseTo(216.5, 3)
  expect(apply.mock.calls[0][0].cellSize).toBeCloseTo(36, 3)
})
