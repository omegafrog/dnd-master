import '@testing-library/jest-dom/vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
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
  const entries = screen.getByRole('list', { name: '대화 기록' }).querySelectorAll('li')
  expect(entries).toHaveLength(3)
  expect(entries[1]).toHaveTextContent('근거를 바탕으로 응답한다.')
  expect(entries[2]).toHaveTextContent('판정 완료')
})

it('does not append a blank judgment as a duplicate GM message', async () => {
  const api: AdventureApi = {
    async sendMessage() {
      return { narration: 'GM 응답', judgment: ' \n\t ', currentScene: '', sourceRefs: [], warnings: [], version: 1 }
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), '조사한다')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))

  const entries = screen.getByRole('list', { name: '대화 기록' }).querySelectorAll('li')
  expect(entries).toHaveLength(2)
  expect(screen.getByText('GM 응답')).toBeInTheDocument()
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

it('disables input while hydration is pending and enables it after hydration', async () => {
  let resolveConversation: ((response: { adventureId: string; version: number; entries: { sequence: number; speaker: string; content: string }[] }) => void) | undefined
  const conversation = new Promise<{ adventureId: string; version: number; entries: { sequence: number; speaker: string; content: string }[] }>(resolve => { resolveConversation = resolve })
  const api: AdventureApi = {
    async readConversation() { return conversation },
    async sendMessage() { throw new Error('not used') },
  }
  render(<AdventureStream adventureId="a1" api={api} expectedVersion={3} />)
  expect(screen.getByRole('textbox', { name: '무엇을 하시겠어요?' })).toBeDisabled()
  expect(screen.getByRole('button', { name: '행동 보내기' })).toBeDisabled()
  expect(screen.getByRole('status')).toHaveTextContent('대화 기록 불러오는 중')
  expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')

  resolveConversation?.({
    adventureId: 'a1',
    version: 4,
    entries: [{ sequence: 0, speaker: 'AI_GAME_MASTER', content: '저장된 프롤로그' }],
  })

  await waitFor(() => expect(screen.getByRole('textbox', { name: '무엇을 하시겠어요?' })).toBeEnabled())
  expect(screen.getByRole('button', { name: '행동 보내기' })).toBeEnabled()
  expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'false')
  expect(screen.getByText('저장된 프롤로그')).toBeInTheDocument()
})

it('enables input after hydration failure while showing a notice', async () => {
  const api: AdventureApi = {
    async readConversation() { throw new Error('읽기 실패') },
    async sendMessage() { throw new Error('not used') },
  }
  render(<AdventureStream adventureId="a1" api={api} />)
  expect(screen.getByRole('button', { name: '행동 보내기' })).toBeDisabled()
  expect(await screen.findByRole('alert')).toHaveTextContent('대화 기록을 불러오지 못했습니다.')
  expect(screen.getByRole('textbox', { name: '무엇을 하시겠어요?' })).toBeEnabled()
  expect(screen.getByRole('button', { name: '행동 보내기' })).toBeEnabled()
})

it('announces failure when message send fails', async () => {
  const onTurnCommitted = vi.fn()
  const api: AdventureApi = {
    async sendMessage() { throw new Error('전송 실패') },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} onTurnCommitted={onTurnCommitted} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), 'Kick the door')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('메시지를 전송하지 못했습니다')
  expect(screen.getByRole('status')).toHaveTextContent('턴 처리 실패')
  expect(onTurnCommitted).not.toHaveBeenCalled()
})

it('notifies after a successful text turn', async () => {
  const onTurnCommitted = vi.fn()
  const api: AdventureApi = {
    async sendMessage() { return { narration: 'ok', judgment: '', currentScene: '', sourceRefs: [], warnings: [], version: 1 } },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} onTurnCommitted={onTurnCommitted} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), 'Open')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  await waitFor(() => expect(onTurnCommitted).toHaveBeenCalledTimes(1))
})

it('notifies after a successful agent turn', async () => {
  const onTurnCommitted = vi.fn()
  const api: AdventureApi = {
    async sendMessage() { throw new Error('not used') },
    async runAgentTurn() { return { narration: 'agent', judgment: '', currentScene: '', sourceRefs: [], warnings: [], version: 1, nextControlMode: 'DIRECT' } },
  }
  render(<AdventureStream adventureId="a1" api={api} controlMode="AGENT" onTurnCommitted={onTurnCommitted} />)
  await waitFor(() => expect(onTurnCommitted).toHaveBeenCalledTimes(1))
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

it('returns to direct input after a committed response and still shows a later event failure', async () => {
  let publish: ((event: { version: number; type: string; payload: string }) => void) | undefined
  const api: AdventureApi = {
    subscribeEvents(_id, _version, onEvent) { publish = onEvent; return () => {} },
    async sendMessage() { return { narration: 'ok', judgment: '', currentScene: '', sourceRefs: [], warnings: [], version: 2 } },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), 'Open')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('직접 플레이 입력 대기'))
  await act(async () => { publish?.({ version: 3, type: 'GM_TURN_FAILED', payload: 'failure' }) })
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

it('refreshes persisted conversation when a GM turn commit arrives', async () => {
  let publish: ((event: { version: number; type: string; payload: string }) => void) | undefined
  let reads = 0
  const api: AdventureApi = {
    subscribeEvents(_id, _version, onEvent) { publish = onEvent; return () => {} },
    async readConversation() {
      reads += 1
      return reads === 1
        ? { adventureId: 'a1', version: 1, entries: [{ sequence: 0, speaker: 'AI_GAME_MASTER', content: '초기 프롤로그' }] }
        : {
            adventureId: 'a1', version: 3,
            entries: [
              { sequence: 0, speaker: 'AI_GAME_MASTER', content: '초기 프롤로그' },
              { sequence: 1, speaker: 'AI_GAME_MASTER', content: '완성된 프롤로그' },
              { sequence: 2, speaker: 'AI_GAME_MASTER', content: '첫 행동을 기다립니다.' },
            ],
          }
    },
    async sendMessage() { throw new Error('not used') },
  }
  render(<AdventureStream adventureId="a1" api={api} />)
  expect(await screen.findByText('초기 프롤로그')).toBeInTheDocument()
  await waitFor(() => expect(publish).toBeDefined())

  await act(async () => { publish?.({ version: 3, type: 'GM_TURN_COMMITTED', payload: 'prologue' }) })

  await waitFor(() => expect(screen.getByText('완성된 프롤로그')).toBeInTheDocument())
  const entries = screen.getByRole('list', { name: '대화 기록' }).querySelectorAll('li')
  expect(entries).toHaveLength(3)
})

it('reconciles an optimistic response without duplicating persisted entries', async () => {
  let publish: ((event: { version: number; type: string; payload: string }) => void) | undefined
  let resolveRefresh: ((response: { adventureId: string; version: number; entries: { sequence: number; speaker: string; content: string }[] }) => void) | undefined
  let reads = 0
  const api: AdventureApi = {
    subscribeEvents(_id, _version, onEvent) { publish = onEvent; return () => {} },
    async readConversation() {
      reads += 1
      if (reads <= 2) return { adventureId: 'a1', version: 0, entries: [] }
      return new Promise(resolve => { resolveRefresh = resolve })
    },
    async sendMessage() { return { narration: '저장된 응답', judgment: '판정', currentScene: '', sourceRefs: [], warnings: [], version: 2 } },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  const input = await screen.findByRole('textbox', { name: '무엇을 하시겠어요?' })
  await waitFor(() => expect(publish).toBeDefined())
  await user.type(input, '문을 연다')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))
  expect(await screen.findByText('저장된 응답')).toBeInTheDocument()

  await act(async () => { publish?.({ version: 2, type: 'GM_TURN_COMMITTED', payload: 'turn' }) })
  resolveRefresh?.({
    adventureId: 'a1', version: 2,
    entries: [
      { sequence: 0, speaker: 'PLAYER', content: '문을 연다' },
      { sequence: 1, speaker: 'AI_GAME_MASTER', content: '저장된 응답' },
      { sequence: 2, speaker: 'AI_GAME_MASTER', content: '판정' },
    ],
  })

  await waitFor(() => expect(screen.getByRole('list', { name: '대화 기록' }).querySelectorAll('li')).toHaveLength(3))
  expect(screen.getAllByText('저장된 응답')).toHaveLength(1)
  expect(screen.getAllByText('판정')).toHaveLength(1)
})
