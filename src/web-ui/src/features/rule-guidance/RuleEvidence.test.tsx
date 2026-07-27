import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { RuleEvidence } from './RuleEvidence'
import type { RuleGuidanceApi, RuleInquiryResponse } from './RuleGuidanceApi'

function apiFor(result: RuleInquiryResponse) {
  const api: RuleGuidanceApi = {
    async ask() { return result },
  }
  return api
}

async function ask(api: RuleGuidanceApi) {
  const user = userEvent.setup()
  render(<RuleEvidence adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('상황'), 'Can advantage stack?')
  await user.click(screen.getByRole('button', { name: '룰 확인' }))
  return user
}

describe('rule evidence', () => {
  it('shows inquiry result after asking a rule question', async () => {
    const api = apiFor({ inquiryId: 'i1', status: 'SUFFICIENT' })
    await ask(api)
    expect(await screen.findByText((_, node) => node?.textContent === '질의 ID: i1')).toBeInTheDocument()
    expect(screen.getByText((_, node) => node?.textContent === '상태: SUFFICIENT')).toBeInTheDocument()
  })

  it('announces failure when rule inquiry fails', async () => {
    const api: RuleGuidanceApi = {
      async ask() { throw new Error('룰 안내를 가져오지 못했습니다.') },
    }
    await ask(api)
    expect(await screen.findByRole('status')).toHaveTextContent('룰 안내를 가져오지 못했습니다.')
  })
})
