import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { MapGridAlignmentEditor } from './MapGridAlignmentEditor'

const initial = { mapId: 'map-1', version: 3, imageRevision: 'image-v1', originX: 12.25, originY: 8.5, cellSize: 31.75 }

it('keeps the 3×3 alignment draft local until explicit apply', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  const cancel = vi.fn()
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={cancel} />)

  expect(screen.getByText('3×3 격자 맞추기', { selector: 'strong' })).toBeInTheDocument()
  expect(screen.getByText(/격자 교차점 하나를 누른 채/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /확대경/ })).not.toBeInTheDocument()
  const canvas = screen.getByAltText('공개된 지도 이미지').parentElement!
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

it('derives the grid origin and cell size from the dragged 3×3 area', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)

  const canvas = screen.getByAltText('공개된 지도 이미지').parentElement!
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
  const canvas = screen.getByAltText('공개된 지도 이미지').parentElement!
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

  expect(image.getAttribute('style')).toContain('translate(37.5px, 25px)')
})

it('converts a grid drag through the zoomed image coordinates', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)
  const canvas = screen.getByAltText('공개된 지도 이미지').parentElement!
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
