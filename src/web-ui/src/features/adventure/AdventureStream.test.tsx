import '@testing-library/jest-dom/vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
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
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), 'Open it')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  expect(sent).toEqual(['Open it'])
  expect(await screen.findByText('Open it')).toBeInTheDocument()
  expect(await screen.findByText('근거를 바탕으로 응답한다.')).toBeInTheDocument()
})

it('renders GM choices as a separate ordered list', async () => {
  const api: AdventureApi = {
    async readConversation() { return { adventureId: 'a1', version: 1, entries: [{ sequence: 0, speaker: 'AI_GAME_MASTER', content: '문 앞에 서 있습니다.\n\n선택지:\n1. 문을 엽니다.\n2. 주변을 살핍니다.' }] } },
    async sendMessage() { throw new Error('not used') },
  }
  render(<AdventureStream adventureId="a1" api={api} />)
  expect(await screen.findByText('선택지')).toBeInTheDocument()
  expect(screen.getByRole('list', { name: '선택지' })).toBeInTheDocument()
  expect(screen.getByText('문을 엽니다.')).toBeInTheDocument()
})

it('hydrates persisted conversation on mount', async () => {
  const api: AdventureApi = {
    async readConversation() { return { adventureId: 'a1', version: 1, entries: [{ sequence: 0, speaker: 'AI_GAME_MASTER', content: '저장된 프롤로그' }] } },
    async sendMessage() { throw new Error('not used') },
  }
  render(<AdventureStream adventureId="a1" api={api} />)
  await waitFor(() => expect(screen.getByRole('heading', { name: '첫 장면' })).toBeInTheDocument())
  expect(screen.getByText('저장된 프롤로그')).toBeInTheDocument()
})

it('uses the persisted conversation version for the next turn', async () => {
  let receivedVersion: number | undefined
  const api: AdventureApi = {
    async readConversation() { return { adventureId: 'a1', version: 4, entries: [] } },
    async sendMessage(_adventureId, _message, _command, expectedVersion) {
      receivedVersion = expectedVersion
      return { narration: 'GM 응답', judgment: 'accepted', currentScene: 'scene', sourceRefs: [], warnings: [], version: 5 }
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  const input = await screen.findByRole('textbox', { name: '무엇을 하시겠어요?' })
  await user.type(input, '조사한다')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  expect(receivedVersion).toBe(4)
  expect(await screen.findByText('GM 응답')).toBeInTheDocument()
})

it('announces failure when message send fails', async () => {
  const api: AdventureApi = {
    async sendMessage() { throw new Error('전송 실패') },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), 'Kick the door')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('메시지를 전송하지 못했습니다')
  expect(screen.getByRole('status')).toHaveTextContent('턴 처리 실패')
})

it('waits for direct input while agent turns progress automatically', () => {
  const api: AdventureApi = { async sendMessage() { throw new Error('must not send') } }
  const { rerender } = render(<AdventureStream adventureId="a1" api={api} controlMode="DIRECT" />)
  expect(screen.getByRole('status')).toHaveTextContent('직접 플레이 입력 대기')
  expect(screen.getByRole('button', { name: '행동 보내기' })).toBeEnabled()
  rerender(<AdventureStream adventureId="a1" api={api} controlMode="AGENT" />)
  expect(screen.getByRole('status')).toHaveTextContent('에이전트 캐릭터 차례')
  expect(screen.getByRole('button', { name: '행동 보내기' })).toBeDisabled()
})

it('shows processing and failed projection states from the event stream', async () => {
  let publish: ((event: { version: number; type: string; payload: string }) => void) | undefined
  const api: AdventureApi = {
    subscribeEvents(_id, _version, onEvent) { publish = onEvent; return () => {} },
    async sendMessage() { return { narration: 'ok', judgment: '', currentScene: '', sourceRefs: [], warnings: [], version: 2 } },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), 'Open')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  expect(screen.getByRole('status')).toHaveTextContent('턴 처리 중')
  await act(async () => { publish?.({ version: 2, type: 'GM_TURN_FAILED', payload: 'failure' }) })
  expect(await screen.findByRole('status')).toHaveTextContent('턴 처리 실패')
})

it('does not let a stale same-version failure override a committed turn', async () => {
  let publish: ((event: { version: number; type: string; payload: string }) => void) | undefined
  const api: AdventureApi = {
    subscribeEvents(_id, _version, onEvent) { publish = onEvent; return () => {} },
    async sendMessage() { return { narration: 'ok', judgment: '', currentScene: '', sourceRefs: [], warnings: [], version: 2 } },
  }
  render(<AdventureStream adventureId="a1" api={api} />)
  await act(async () => {
    publish?.({ version: 2, type: 'GM_TURN_COMMITTED', payload: 'turn' })
    publish?.({ version: 2, type: 'GM_TURN_FAILED', payload: 'stale failure' })
  })
  expect(screen.getByRole('status')).toHaveTextContent('직접 플레이 입력 대기')
  expect(screen.getByRole('alert')).toHaveTextContent('')
})
