import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { RuleEvidence } from './RuleEvidence'
import type { RuleGuidance, RuleGuidanceApi } from './RuleGuidanceApi'

function apiFor(result: RuleGuidance) {
  const selected: string[] = []
  const api: RuleGuidanceApi = {
    async ask() { return result },
    async selectFinalRule(_inquiryId, candidateId) { selected.push(candidateId) },
  }
  return { api, selected }
}

async function ask(api: RuleGuidanceApi) {
  const user = userEvent.setup()
  render(<RuleEvidence adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('상황'), 'Can advantage stack?')
  await user.click(screen.getByRole('button', { name: '룰 확인' }))
  return user
}

describe('rule evidence', () => {
  it('shows authoritative answer only with source locations', async () => {
    const { api } = apiFor({ inquiryId: 'i1', status: 'SUFFICIENT', answer: 'It does not stack.',
      sources: [{ rulebook: 'PHB', locator: 'p. 173' }], candidates: [] })
    await ask(api)
    expect(await screen.findByRole('heading', { name: '근거가 충분한 답변' })).toBeInTheDocument()
    expect(screen.getByText('PHB — p. 173')).toBeInTheDocument()
  })

  it('never presents an uncited response as authoritative', async () => {
    const { api } = apiFor({ inquiryId: 'i1', status: 'SUFFICIENT', answer: 'Trust me', sources: [], candidates: [] })
    await ask(api)
    expect(await screen.findByRole('alert')).toHaveTextContent('출처가 없는 응답')
    expect(screen.queryByText('Trust me')).not.toBeInTheDocument()
  })

  it.each(['INSUFFICIENT', 'CONFLICTING'] as const)('discloses %s candidates and selects a disclosed rule', async status => {
    const { api, selected } = apiFor({ inquiryId: 'i1', status, sources: [], candidates: [
      { id: 'c1', text: 'Use the general rule', sources: [{ rulebook: 'PHB', locator: 'p. 7' }] },
      { id: 'c2', text: 'Use the variant', sources: [{ rulebook: 'DMG', locator: 'p. 252' }] },
    ] })
    const user = await ask(api)
    expect(await screen.findByRole('heading', { name: status === 'INSUFFICIENT' ? '근거 부족' : '근거 충돌' })).toBeInTheDocument()
    expect(screen.getAllByRole('listitem').length).toBeGreaterThanOrEqual(2)
    await user.click(screen.getAllByRole('button', { name: '이 규칙 선택' })[1])
    expect(selected).toEqual(['c2'])
    expect(screen.getByText('최종 적용 규칙: Use the variant')).toBeInTheDocument()
  })
})
