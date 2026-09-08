import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { MapGridAlignmentEditor } from './MapGridAlignmentEditor'

const initial = { version: 3, imageRevision: 'image-v1', originX: 12.25, originY: 8.5, cellSize: 31.75 }

it('keeps the 3×3 alignment draft local until explicit apply', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  const cancel = vi.fn()
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={cancel} />)

  expect(screen.getByText('3×3 격자 맞추기', { selector: 'strong' })).toBeInTheDocument()
  const magnifier = screen.getByRole('button', { name: '확대경 켜기' })
  expect(magnifier).toHaveAttribute('aria-pressed', 'false')
  await user.click(magnifier)
  expect(screen.getByRole('button', { name: '확대경 끄기' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByLabelText('확대경')).toBeInTheDocument()
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
  expect(apply).toHaveBeenLastCalledWith(expect.objectContaining({ cellSize: 31.75, expectedVersion: 3, imageRevision: 'image-v1' }))
})
