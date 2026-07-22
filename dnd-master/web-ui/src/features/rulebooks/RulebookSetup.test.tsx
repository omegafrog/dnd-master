import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { RulebookSetup } from './RulebookSetup'
import type { BatchRulebookView, SetupApi } from './SetupApi'

class FakeSetupApi implements SetupApi {
  uploadError = ''
  private results: BatchRulebookView[] = [
    { knowledgeDocumentId: 'doc-1', documentType: 'RULEBOOK', originalFilename: 'phb.pdf', status: 'ACCEPTED' },
    { knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK', originalFilename: 'campaign.md', status: 'VALIDATION_FAILED', failureReason: 'unsupported format' },
  ]

  async uploadRulebooks() {
    if (this.uploadError) throw new Error(this.uploadError)
    return this.results
  }
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async getRulebookStatus(_rulebookId: string) {
    return { rulebookId: 'phb', status: 'INDEXED' as const }
  }
  async uploadScenario(file: File) { return { id: 'scenario-1', name: file.name } }
  async saveRuleSet() {}
}

describe('rulebook and adventure setup', () => {
  it('uploads mixed documents and shows per-file status', async () => {
    const api = new FakeSetupApi()
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('자료 파일'), {
      target: { files: [
        new File(['rules'], 'phb.pdf', { type: 'application/pdf' }),
        new File(['story'], 'campaign.md', { type: 'text/markdown' }),
      ] },
    })
    await user.selectOptions(screen.getByLabelText('phb.pdf 유형'), 'RULEBOOK')
    await user.selectOptions(screen.getByLabelText('campaign.md 유형'), 'STORYBOOK')
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))

    expect(await screen.findByRole('checkbox', { name: 'phb.pdf' })).toBeChecked()
    expect(screen.getByText((_, element) => element?.tagName === 'LI' && element.textContent?.includes('사용 준비 완료') === true)).toBeInTheDocument()
    expect(screen.getByText((_, element) => element?.tagName === 'LI' && element.textContent?.includes('검증 실패') === true)).toBeInTheDocument()
  })

  it('displays upload error', async () => {
    const api = new FakeSetupApi()
    api.uploadError = '지원하지 않거나 손상된 파일입니다.'
    const user = userEvent.setup()
    render(<RulebookSetup api={api} playerId="p1" />)
    fireEvent.change(screen.getByLabelText('자료 파일'), { target: { files: [new File(['bad'], 'bad.pdf')] } })
    await user.click(screen.getByRole('button', { name: '자료 업로드' }))
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
