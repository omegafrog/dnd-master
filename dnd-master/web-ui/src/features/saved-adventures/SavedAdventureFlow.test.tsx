import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import type { SetupApi } from '../rulebooks/SetupApi'
import type { AdventurePlayApi } from './AdventurePlayApi'
import { SavedAdventurePanel } from './SavedAdventurePanel'

it('lists, resumes, deletes and configures session knowledge sets', async () => {
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
    async getSessionKnowledgeSet() { return { adventureId: 'old', sessionId: 'session-1', knowledgeDocumentIds: ['doc-1'] } },
    async saveSessionKnowledgeSet(adventureId, _playerId, knowledgeDocumentIds) {
      calls.push(`session:${adventureId}:${knowledgeDocumentIds.join(',')}`)
      return { adventureId, sessionId: 'session-1', knowledgeDocumentIds }
    },
    async getCharacter() { throw new Error() },
    async rollDice() { throw new Error() },
  }
  const setupApi: SetupApi = {
    async uploadRulebooks() { return [] },
    async getRulebookStatus() { return { rulebookId: 'rulebook-1', status: 'INDEXED' as const } },
    async retryKnowledgeDocument(knowledgeDocumentId: string) { return { rulebookId: knowledgeDocumentId, status: 'INDEXED' as const } },
    async uploadScenario(file) { return { id: 'scenario', name: file.name } },
    async saveRuleSet() {},
    async listKnowledgeDocuments() {
      return [
        { knowledgeDocumentId: 'doc-1', documentType: 'RULEBOOK', originalFilename: 'phb.pdf', status: 'INDEXED' as const },
        { knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK', originalFilename: 'campaign.md', status: 'UPLOADED' as const },
      ]
    },
  }
  const user = userEvent.setup()
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" />)
  expect(await screen.findByText('Old Keep')).toBeInTheDocument()
  const old = screen.getByText('Old Keep').closest('li')!
  await user.click(old.querySelectorAll('button')[0])
  expect(screen.getByRole('status')).toHaveTextContent('재개했습니다')
  await user.click(screen.getByRole('button', { name: '자료 설정' }))
  expect(await screen.findByRole('checkbox', { name: /phb\.pdf/ })).toBeChecked()
  await user.click(screen.getByRole('button', { name: '세션 자료 저장' }))
  await user.click(old.querySelectorAll('button')[1])
  expect(screen.queryByText('Old Keep')).not.toBeInTheDocument()
  expect(calls).toContain('resume:old')
  expect(calls).toContain('delete:old')
  expect(calls).toContain('session:old:doc-1')
})
