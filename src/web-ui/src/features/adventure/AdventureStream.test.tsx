import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { AdventureStream } from './AdventureStream'
import type { AdventureApi } from './AdventureApi'

it('renders sent conversation and acknowledges delivery', async () => {
  const sent: string[] = []
  const api: AdventureApi = {
    async sendMessage(_id, message) {
      sent.push(message)
      return {
        narration: '근거를 바탕으로 응답한다.',
        judgment: '판정 완료',
        currentScene: '새 장면',
        sourceRefs: ['storybook:page:1'],
        warnings: [],
        version: 1,
      }
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('행동 또는 대화'), 'Open it')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  expect(sent).toEqual(['Open it'])
  expect(await screen.findByText((_, node) => node?.textContent === '플레이어: Open it')).toBeInTheDocument()
  expect(await screen.findByText((_, node) => node?.textContent === 'AI 게임 마스터: 근거를 바탕으로 응답한다.')).toBeInTheDocument()
})

it('announces failure when message send fails', async () => {
  const api: AdventureApi = {
    async sendMessage() { throw new Error('전송 실패') },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('행동 또는 대화'), 'Kick the door')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('메시지를 전송하지 못했습니다')
})
