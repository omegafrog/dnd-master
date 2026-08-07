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
  await user.type(screen.getByLabelText('행동 또는 대화'), 'Open it')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  expect(sent).toEqual(['Open it'])
  expect(await screen.findByText((_, node) => node?.textContent === '플레이어: Open it')).toBeInTheDocument()
  expect(await screen.findByText((_, node) => node?.textContent === 'AI 게임 마스터: 근거를 바탕으로 응답한다.')).toBeInTheDocument()
})

it('shows a safe grounding notice when backend refuses unsupported output', async () => {
  const api: AdventureApi = {
    async sendMessage() {
      return {
        narration: '아직 확인된 근거가 없어 결과를 말할 수 없습니다.',
        judgment: 'pending judgment',
        currentScene: 'scene',
        sourceRefs: [],
        warnings: ['degraded-mode:RULE;repair-attempted=true;refusal-reason=secret internal detail'],
        version: 1,
      }
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('행동 또는 대화'), '공격한다')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('근거가 부족해 안전한 대기 응답을 표시했습니다.')
  expect(screen.getByRole('alert')).not.toHaveTextContent('secret internal detail')
})

it('hydrates persisted conversation on mount', async () => {
  const api: AdventureApi = {
    async readConversation() { return { adventureId: 'a1', version: 1, entries: [{ sequence: 0, speaker: 'AI_GAME_MASTER', content: '저장된 프롤로그' }] } },
    async sendMessage() { throw new Error('not used') },
  }
  render(<AdventureStream adventureId="a1" api={api} />)
  await waitFor(() => expect(screen.getAllByRole('listitem').some(item => item.textContent?.includes('저장된 프롤로그'))).toBe(true))
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
  const input = await screen.findByRole('textbox', { name: '행동 또는 대화' })
  await user.type(input, '조사한다')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  expect(receivedVersion).toBe(4)
  expect(await screen.findByText((_, node) => node?.textContent === 'AI 게임 마스터: GM 응답')).toBeInTheDocument()
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
  expect(screen.getByRole('status')).toHaveTextContent('턴 처리 실패')
})

it('retries a typed failure with the original command identity', async () => {
  const commands: unknown[] = []
  let attempts = 0
  const api: AdventureApi = {
    async sendMessage(_id, _message, command) {
      commands.push(command)
      attempts++
      if (attempts === 1) throw new Error('typed safe failure')
      return { narration: '재시도 성공', judgment: '', currentScene: '', sourceRefs: [], warnings: [], version: 1 }
    },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('행동 또는 대화'), 'Open the door')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  await user.click(await screen.findByRole('button', { name: '다시 시도' }))
  expect(commands[1]).toEqual(commands[0])
  expect(await screen.findByText((_, node) => node?.textContent === 'AI 게임 마스터: 재시도 성공')).toBeInTheDocument()
})

it('waits for direct input while agent turns progress automatically', () => {
  const api: AdventureApi = { async sendMessage() { throw new Error('must not send') } }
  const { rerender } = render(<AdventureStream adventureId="a1" api={api} controlMode="DIRECT" />)
  expect(screen.getByRole('status')).toHaveTextContent('직접 플레이 입력 대기')
  expect(screen.getByRole('button', { name: '보내기' })).toBeEnabled()
  rerender(<AdventureStream adventureId="a1" api={api} controlMode="AGENT" />)
  expect(screen.getByRole('status')).toHaveTextContent('에이전트 캐릭터 차례')
  expect(screen.getByRole('button', { name: '보내기' })).toBeDisabled()
})

it('shows processing and failed projection states from the event stream', async () => {
  let publish: ((event: { version: number; type: string; payload: string }) => void) | undefined
  const api: AdventureApi = {
    subscribeEvents(_id, _version, onEvent) { publish = onEvent; return () => {} },
    async sendMessage() { return { narration: 'ok', judgment: '', currentScene: '', sourceRefs: [], warnings: [], version: 2 } },
  }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('행동 또는 대화'), 'Open')
  await user.click(screen.getByRole('button', { name: '보내기' }))
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
