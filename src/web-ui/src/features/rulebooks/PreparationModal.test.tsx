import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { PreparationModal } from './PreparationModal'
import type { SetupApi } from './SetupApi'

it('focuses the dialog, closes with Escape, and exposes actionable status text', async () => {
  const onClose = vi.fn()
  const api = {
    preflightAgentEndpoint: vi.fn().mockResolvedValue({ configured: false, connected: false, state: 'NOT_CONFIGURED' as const, detail: 'raw internal failure' }),
  } as unknown as SetupApi
  render(<PreparationModal bundleId="bundle-1" revision={1} api={api} ownerId="owner-1" onClose={onClose} onCharacter={vi.fn()} onAdventure={vi.fn()} />)
  const dialog = await screen.findByRole('dialog')
  expect(dialog).toHaveFocus()
  expect(screen.getByRole('alert')).toHaveTextContent('AI 엔드포인트를 설정해야 합니다.')
  expect(screen.getByRole('alert')).not.toHaveTextContent('raw internal failure')
  await userEvent.keyboard('{Escape}')
  expect(onClose).toHaveBeenCalledOnce()
})
