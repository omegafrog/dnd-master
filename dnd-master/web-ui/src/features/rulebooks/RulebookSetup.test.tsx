import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { RulebookSetup } from './RulebookSetup'
import type { RulebookView, SetupApi } from './SetupApi'

class FakeSetupApi implements SetupApi {
  uploadError = ''
  private book: RulebookView = { rulebookId: 'phb', status: 'PENDING' }

  async uploadRulebook() {
    if (this.uploadError) throw new Error(this.uploadError)
    return this.book
  }
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async getRulebookStatus(_rulebookId: string) {
    return { ...this.book, status: 'INDEXED' as const }
  }
  async uploadScenario(file: File) { return { id: 'scenario-1', name: file.name } }
}

describe('rulebook and adventure setup', () => {
  it('uploads rulebook, shows status, and refreshes to ready', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('룰북 파일'), {
      target: { files: [new File(['rules'], 'phb.pdf', { type: 'application/pdf' })] },
    })
    await user.click(screen.getByRole('button', { name: '룰북 업로드' }))

    expect(await screen.findByText('phb')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '상태 새로고침' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '상태 새로고침' }))
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: '상태 새로고침' })).not.toBeInTheDocument()
    })
  })

  it('displays upload error', async () => {
    const api = new FakeSetupApi()
    api.uploadError = '지원하지 않거나 손상된 파일입니다.'
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('룰북 파일'), { target: { files: [new File(['bad'], 'bad.pdf')] } })
    await user.click(screen.getByRole('button', { name: '룰북 업로드' }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(api.uploadError))
  })

  it('registers a scenario', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('시나리오 파일'), { target: { files: [new File(['story'], 'castle.pdf')] } })
    await user.click(screen.getByRole('button', { name: '시나리오 등록' }))
    expect(await screen.findByText('등록 완료: castle.pdf')).toBeInTheDocument()
  })
})
