import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import type { AdventurePlayApi } from './AdventurePlayApi'
import { SavedAdventurePanel } from './SavedAdventurePanel'

it('lists, resumes and deletes adventures through the AdventurePlayApi', async () => {
  const calls: string[] = []
  const api: AdventurePlayApi = {
    async listSaved() { return [{ id: 'old', title: 'Old Keep', updatedAt: '2026-01-01' }] },
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    async save(_adventureId: string, _playerId: string, _expectedVersion: number, _currentScene: string) {
      calls.push('save')
      return { adventureId: 'new', newVersion: 1 }
    },
    async resume(id) { calls.push(`resume:${id}`) },
    async deleteAdventure(id) { calls.push(`delete:${id}`) },
    async getCharacter() { throw new Error() },
    async rollDice() { throw new Error() },
  }
  const user = userEvent.setup()
  render(<SavedAdventurePanel api={api} playerId="p1" />)
  expect(await screen.findByText('Old Keep')).toBeInTheDocument()
  const old = screen.getByText('Old Keep').closest('li')!
  await user.click(old.querySelectorAll('button')[0])
  expect(screen.getByRole('status')).toHaveTextContent('재개했습니다')
  await user.click(old.querySelectorAll('button')[1])
  expect(screen.queryByText('Old Keep')).not.toBeInTheDocument()
  expect(calls).toEqual(['resume:old', 'delete:old'])
})
