import '@testing-library/jest-dom/vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { AdventureStream } from './AdventureStream'
import { AdventureRequestError, type AdventureApi } from './AdventureApi'

it('renders the GM narration without exposing its internal judgment', async () => {
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
  expect(entries).toHaveLength(2)
  expect(entries[1]).toHaveTextContent('근거를 바탕으로 응답한다.')
  expect(screen.queryByText('판정 완료')).not.toBeInTheDocument()
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

it('shows the scene narration before asking for a player roll', async () => {
  const api: AdventureApi = {
    async readConversation() { return { adventureId: 'a1', version: 1, entries: [] } },
    async sendMessage() {
      return {
        narration: '해치를 열자 차가운 공기와 발톱 소리가 어둠 속에서 밀려옵니다.',
        currentScene: 'beer-cellar', version: 1,
        rollRequest: { pendingTurnId: 'pending-turn', label: '지각', diceExpression: '1d20', prompt: '숨은 움직임을 알아차릴 수 있을까요?', expectedVersion: 1 },
      }
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(await screen.findByRole('textbox', { name: '무엇을 하시겠어요?' }), '해치를 열고 들어간다')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))

  expect(await screen.findByText('해치를 열자 차가운 공기와 발톱 소리가 어둠 속에서 밀려옵니다.')).toBeInTheDocument()
  expect(screen.getByRole('form', { name: '주사위 굴림 요청' })).toBeInTheDocument()
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

it('restores an outstanding player roll and persisted current scene after reopening', async () => {
  const onCurrentSceneChanged = vi.fn()
  const api: AdventureApi = {
    async readConversation() {
      return {
        adventureId: 'a1', version: 4, currentScene: 'beer-cellar',
        entries: [{ sequence: 0, speaker: 'AI_GAME_MASTER', content: '판정이 필요합니다.' }],
        pendingRoll: { pendingTurnId: 'pending-turn', label: '지각', diceExpression: '1d20', prompt: '굴림 결과를 제출하세요.', expectedVersion: 4 },
      }
    },
    async submitPlayerRoll(_adventureId, turnId, result, expectedVersion) {
      expect([turnId, result, expectedVersion]).toEqual(['pending-turn', 13, 4])
      return { narration: '판정을 마쳤습니다.', currentScene: 'beer-cellar', version: 5 }
    },
    async sendMessage() { throw new Error('not used') },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} onCurrentSceneChanged={onCurrentSceneChanged} />)

  expect(await screen.findByRole('form', { name: '주사위 굴림 요청' })).toBeInTheDocument()
  expect(screen.getByRole('textbox', { name: '무엇을 하시겠어요?' })).toBeDisabled()
  expect(onCurrentSceneChanged).toHaveBeenCalledWith('beer-cellar')
  await user.type(screen.getByLabelText('1d20 결과'), '13')
  await user.click(screen.getByRole('button', { name: '결과 제출' }))
  expect(await screen.findByText('판정을 마쳤습니다.')).toBeInTheDocument()
})

it('reloads current scene and pending roll after the conversation event connection drops', async () => {
  let failEvents: (() => void) | undefined
  let reads = 0
  const api: AdventureApi = {
    subscribeEvents(_adventureId, _version, _onEvent, onError) {
      failEvents = onError
      return () => undefined
    },
    async readConversation() {
      reads += 1
      if (reads === 1) return { adventureId: 'a1', version: 5, currentScene: '입구', entries: [] }
      return {
        adventureId: 'a1', version: 6, currentScene: '저장고', entries: [],
        pendingRoll: { pendingTurnId: 'pending-turn', label: '운동', diceExpression: '1d20', prompt: '굴림을 제출하세요.', expectedVersion: 6 },
      }
    },
    async sendMessage() { throw new Error('not used') },
  }
  const onCurrentSceneChanged = vi.fn()
  render(<AdventureStream adventureId="a1" api={api} onCurrentSceneChanged={onCurrentSceneChanged} />)
  await waitFor(() => expect(failEvents).toBeDefined())
  act(() => failEvents?.())

  expect(await screen.findByRole('form', { name: '주사위 굴림 요청' })).toBeInTheDocument()
  expect(onCurrentSceneChanged).toHaveBeenCalledWith('저장고')
  expect(reads).toBe(2)
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

it('does not automatically resend a player action after a 502 response', async () => {
  let attempts = 0
  const api: AdventureApi = {
    async sendMessage() {
      attempts += 1
      throw new AdventureRequestError('provider failed', 502, 'Codex 계정을 다시 인증해야 합니다. (REAUTH_REQUIRED)')
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), '문을 연다')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('REAUTH_REQUIRED'))
  expect(attempts).toBe(1)
  expect(screen.queryByText('문을 연다')).not.toBeInTheDocument()
})

it('surfaces the server validation reason without retrying', async () => {
  let attempts = 0
  const api: AdventureApi = {
    async sendMessage() {
      attempts += 1
      throw new AdventureRequestError('provider failed', 502, '턴 계획 검증 실패: STORYBOOK_CITATION_REQUIRED')
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), '문을 연다')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('STORYBOOK_CITATION_REQUIRED'))
  expect(attempts).toBe(1)
})

it('removes an optimistic player action when the final request fails', async () => {
  const api: AdventureApi = {
    async sendMessage() { throw new AdventureRequestError('server failed', 500) },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), '실패 행동')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))

  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('메시지를 전송하지 못했습니다'))
  expect(screen.queryByText('실패 행동')).not.toBeInTheDocument()
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

it('accepts a player roll using the dice range requested by the GM', async () => {
  let submitted: number | undefined
  const api: AdventureApi = {
    async sendMessage() {
      return {
        narration: '굴림 결과를 기다립니다.', currentScene: '저장고', version: 1,
        rollRequest: { pendingTurnId: 'turn-1', label: '자연', diceExpression: '1d4', prompt: '약초를 조사합니다.', expectedVersion: 1 },
      }
    },
    async submitPlayerRoll(_adventureId, _pendingTurnId, result) {
      submitted = result
      return { narration: '판정을 마쳤습니다.', currentScene: '저장고', version: 2 }
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('무엇을 하시겠어요?'), '약초를 조사한다')
  await user.click(screen.getByRole('button', { name: '행동 보내기' }))

  const rollInput = await screen.findByLabelText('1d4 결과')
  expect(rollInput).toHaveAttribute('min', '1')
  expect(rollInput).toHaveAttribute('max', '4')
  await user.type(rollInput, '4')
  await user.click(screen.getByRole('button', { name: '결과 제출' }))

  expect(submitted).toBe(4)
  expect(await screen.findByText('판정을 마쳤습니다.')).toBeInTheDocument()
})
