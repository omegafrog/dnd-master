import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PreparationModal } from './PreparationModal'
import type { SetupApi } from './SetupApi'

describe('PreparationModal', () => {
  it('lets a configured Codex user request preparation while showing that connection is checked by the request', async () => {
    const startScenarioCompilation = vi.fn(async () => ({
      compilationId: 'compilation-1', bundleId: 'bundle-1', bundleRevision: 1,
      status: 'REQUESTED' as const, attempt: 0, packageId: null, failureReason: null,
    }))
    const api = {
      preflightAgentEndpoint: async () => ({ configured: true, connected: false, canRequest: true, state: 'CHECK_ON_REQUEST' as const, provider: 'CODEX_CLI' as const }),
      startScenarioCompilation,
    } as unknown as SetupApi
    render(<PreparationModal bundleId="bundle-codex" revision={1} api={api} ownerId="owner-1" storybookDocuments={[]} onClose={() => {}} onCharacter={vi.fn()} onAdventure={vi.fn()} />)
    expect(await screen.findByText('사용자 PC 에이전트에서 Codex 로그인과 연결을 확인하세요.')).toBeInTheDocument()
    expect(screen.queryByText('AI 엔드포인트 연결됨')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '게임 준비 시작' }))
    await waitFor(() => expect(startScenarioCompilation).toHaveBeenCalledOnce())
  })

  it('focuses the dialog, closes with Escape, and exposes actionable status text', async () => {
    const onClose = vi.fn()
    const api = {
      preflightAgentEndpoint: vi.fn().mockResolvedValue({ configured: false, connected: false, state: 'NOT_CONFIGURED' as const, detail: 'raw internal failure' }),
    } as unknown as SetupApi
    render(<PreparationModal bundleId="bundle-1" revision={1} api={api} ownerId="owner-1" storybookDocuments={[]} onClose={onClose} onCharacter={vi.fn()} onAdventure={vi.fn()} />)
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveFocus()
    expect(screen.getByRole('alert')).toHaveTextContent('AI 엔드포인트를 설정해야 합니다.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('raw internal failure')
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('sends the selected Primary Storybook when starting a multi-storybook preparation', async () => {
    const startScenarioCompilation = vi.fn(async () => ({
      compilationId: 'compilation-1', bundleId: 'bundle-1', bundleRevision: 1,
      status: 'REQUESTED' as const, attempt: 0, packageId: null, failureReason: null,
    }))
    const api = {
      preflightAgentEndpoint: async () => ({ configured: true, connected: true, state: 'CONNECTED' as const }),
      startScenarioCompilation,
    } as unknown as SetupApi
    const user = userEvent.setup()

    render(<PreparationModal bundleId="bundle-1" revision={1} api={api} ownerId="owner-1"
      storybookDocuments={[
        { knowledgeDocumentId: 'main', documentType: 'STORYBOOK', originalFilename: 'main.pdf', role: 'MAIN_SCENARIO', status: 'INDEXED', extractionVersion: 1 },
        { knowledgeDocumentId: 'handout', documentType: 'STORYBOOK', originalFilename: 'handout.pdf', role: 'HANDOUT', status: 'INDEXED', extractionVersion: 1 },
      ]}
      onClose={() => {}} onCharacter={() => {}} onAdventure={() => {}} />)

    const primary = await screen.findByLabelText('Primary Storybook')
    expect(primary).toHaveValue('main')
    await user.selectOptions(primary, 'handout')
    await user.click(screen.getByRole('button', { name: '게임 준비 시작' }))

    await waitFor(() => expect(startScenarioCompilation).toHaveBeenCalledWith(
      'bundle-1', 'owner-1', 'scenario-bundle:bundle-1:revision:1', { primaryStorybookId: 'handout' },
    ))
  })
})
