import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { AdventureStream } from './AdventureStream'

it('renders completed streamed conversation', async () => {
  const api = { async *streamMessage() { yield 'The door '; yield 'opens.' } }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('행동 또는 대화'), 'Open it')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  expect(await screen.findByText(/The door opens/)).toBeInTheDocument()
  expect(screen.queryByText(/임시 응답/)).not.toBeInTheDocument()
})

it('marks interrupted stream content as temporary and announces interruption', async () => {
  const api = { async *streamMessage() { yield 'Maybe the door'; throw new Error('disconnect') } }
  const user = userEvent.setup()
  render(<AdventureStream adventureId="a1" api={api} />)
  await user.type(screen.getByLabelText('행동 또는 대화'), 'Open it')
  await user.click(screen.getByRole('button', { name: '보내기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('스트림이 중단되었습니다')
  expect(screen.getByText(/임시 응답/)).toBeInTheDocument()
})
