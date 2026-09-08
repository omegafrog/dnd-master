import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { MapGridAlignmentEditor } from './MapGridAlignmentEditor'

const initial = { version: 3, imageRevision: 'image-v1', originX: 12.25, originY: 8.5, cellSize: 31.75 }

it('keeps a three-step alignment draft local until explicit apply', async () => {
  const apply = vi.fn().mockResolvedValue(undefined)
  const cancel = vi.fn()
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={cancel} />)

  expect(screen.getByText('3×3 격자 크기', { selector: 'strong' })).toBeInTheDocument()
  const magnifier = screen.getByRole('button', { name: '확대경 켜기' })
  expect(magnifier).toHaveAttribute('aria-pressed', 'false')
  await user.click(magnifier)
  expect(screen.getByRole('button', { name: '확대경 끄기' })).toHaveAttribute('aria-pressed', 'true')
  await user.click(screen.getByRole('button', { name: '다음' }))
  expect(screen.getByText('한 칸 크기', { selector: 'strong' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '다음' }))
  expect(screen.getByText('미세 조정', { selector: 'strong' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '취소' }))
  expect(cancel).toHaveBeenCalledOnce()
  expect(apply).not.toHaveBeenCalled()
})

it('preserves the draft and exposes retry after a save error', async () => {
  const apply = vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(undefined)
  const user = userEvent.setup()
  render(<MapGridAlignmentEditor image="/public.png" initial={initial} onApply={apply} onCancel={() => {}} />)

  await user.clear(screen.getByLabelText('한 칸 크기'))
  await user.type(screen.getByLabelText('한 칸 크기'), '33.5')
  await user.click(screen.getByRole('button', { name: '적용' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('network')
  expect(screen.getByLabelText('한 칸 크기')).toHaveValue(33.5)
  await user.click(screen.getByRole('button', { name: '다시 적용' }))
  expect(apply).toHaveBeenLastCalledWith(expect.objectContaining({ cellSize: 33.5, expectedVersion: 3, imageRevision: 'image-v1' }))
})
