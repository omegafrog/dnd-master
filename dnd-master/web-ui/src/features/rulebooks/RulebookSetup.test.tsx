import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { RulebookSetup } from './RulebookSetup'
import type { RulebookView, SetupApi } from './SetupApi'

class FakeSetupApi implements SetupApi {
  uploadError = ''
  saved?: { edition: string; ids: string[] }
  confirmed = false
  private book: RulebookView = { id: 'phb', name: 'Player Handbook', status: 'PARTIAL', warnings: ['3쪽 표 추출 실패'], owned: true }

  async uploadRulebook() {
    if (this.uploadError) throw new Error(this.uploadError)
    return this.book
  }
  async refreshRulebook() { return { ...this.book, status: 'READY' as const, warnings: [] } }
  async confirmPartialExtraction() {
    this.confirmed = true
    this.book = { ...this.book, status: 'INDEXING' }
    return this.book
  }
  async uploadScenario(file: File) { return { id: 'scenario-1', name: file.name } }
  async saveRuleSet(edition: '2014' | '2024', rulebookIds: string[]) { this.saved = { edition, ids: rulebookIds } }
}

describe('rulebook and adventure setup', () => {
  it('shows partial extraction, confirms it, and exposes indexing delay until ready', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} />)
    fireEvent.change(screen.getByLabelText('룰북 파일'), {
      target: { files: [new File(['rules'], 'phb.pdf', { type: 'application/pdf' })] },
    })
    await user.click(screen.getByRole('button', { name: '룰북 업로드' }))

    expect(await screen.findByText(/부분 추출 확인 필요/)).toBeInTheDocument()
    expect(screen.getByText('3쪽 표 추출 실패')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '부분 추출 사용' }))
    expect(api.confirmed).toBe(true)
    expect(await screen.findByText(/색인 생성 중/)).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: '상태 새로고침' }))
    expect(await screen.findByText(/사용 준비 완료/)).toBeInTheDocument()
  })

  it.each(['지원하지 않거나 손상된 파일입니다.', '다른 플레이어의 자료는 사용할 수 없습니다.'])(
    'displays upload denial: %s', async error => {
      const api = new FakeSetupApi()
      api.uploadError = error
      const user = userEvent.setup()
      render(<RulebookSetup api={api} />)
      fireEvent.change(screen.getByLabelText('룰북 파일'), { target: { files: [new File(['bad'], 'bad.pdf')] } })
      await user.click(screen.getByRole('button', { name: '룰북 업로드' }))
      await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent(error))
    },
  )

  it('registers a scenario and saves edition with an owned ready rulebook', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} />)
    fireEvent.change(screen.getByLabelText('룰북 파일'), { target: { files: [new File(['rules'], 'phb.pdf')] } })
    await user.click(screen.getByRole('button', { name: '룰북 업로드' }))
    await user.click(await screen.findByRole('button', { name: '부분 추출 사용' }))
    await user.click(await screen.findByRole('button', { name: '상태 새로고침' }))
    fireEvent.change(screen.getByLabelText('시나리오 파일'), { target: { files: [new File(['story'], 'castle.pdf')] } })
    await user.click(screen.getByRole('button', { name: '시나리오 등록' }))
    await user.selectOptions(screen.getByLabelText('판본'), '2014')
    await user.click(screen.getByRole('checkbox', { name: 'Player Handbook' }))
    await user.click(screen.getByRole('button', { name: '룰 세트 저장' }))

    expect(await screen.findByText('등록 완료: castle.pdf')).toBeInTheDocument()
    expect(api.saved).toEqual({ edition: '2014', ids: ['phb'] })
    expect(screen.getByText('룰 세트가 저장되었습니다.')).toBeInTheDocument()
  })
})
