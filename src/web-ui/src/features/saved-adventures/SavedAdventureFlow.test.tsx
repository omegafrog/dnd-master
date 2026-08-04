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
    async getCombatMap() { return { adventureId: 'old', status: 'authoritative-map' } },
    async getCharacter() { throw new Error() },
    async rollDice() { throw new Error() },
  }
  const setupApi: SetupApi = {
    async uploadRulebooks() { return [] },
    async getRulebookStatus() { return { rulebookId: 'rulebook-1', status: 'INDEXED' as const } },
    async retryKnowledgeDocument(knowledgeDocumentId: string) { return { rulebookId: knowledgeDocumentId, status: 'INDEXED' as const } },
    async getSourcePreview() { throw new Error() },
    async createScenarioBundle() { return { bundleId: 'bundle', ownerPlayerId: 'p1', currentRevision: 1, documents: [] } },
    async reviseScenarioBundle() { return { bundleId: 'bundle', ownerPlayerId: 'p1', currentRevision: 2, documents: [] } },
    async getScenarioBundle() { return { bundleId: 'bundle', ownerPlayerId: 'p1', currentRevision: 1, documents: [] } },
    async createCharacterSheet() {
      return {
        characterSheetId: 'sheet-1',
        adventureId: 'adventure-1',
        edition: 'DND_5E_2024',
        characterName: 'Aria',
        level: 1,
        inspiration: false,
        version: 0,
      }
    },
    async saveRuleSet() {},
    async listKnowledgeDocuments() {
      return [
        { knowledgeDocumentId: 'doc-1', documentType: 'RULEBOOK', originalFilename: 'phb.pdf', status: 'INDEXED' as const, format: 'PDF' as const },
        { knowledgeDocumentId: 'doc-2', documentType: 'STORYBOOK', originalFilename: 'campaign.md', status: 'UPLOADED' as const, format: 'TXT' as const },
      ]
    },
  }
  const user = userEvent.setup()
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" />)
  expect(await screen.findByText('Old Keep')).toBeInTheDocument()
  expect(screen.queryByText('레거시 시나리오 마이그레이션')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '자료 설정' }))
  expect(await screen.findByRole('checkbox', { name: /phb\.pdf/ })).toBeChecked()
  const old = screen.getByText('Old Keep').closest('li')!
  await user.click(old.querySelectorAll('button')[0])
  expect(screen.getByText('모험을 재개했습니다.')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '자료 설정' }))
  await user.click(screen.getByRole('button', { name: '세션 자료 저장' }))
  await user.click(old.querySelectorAll('button')[1])
  expect(screen.queryByText('Old Keep')).not.toBeInTheDocument()
  expect(calls).toContain('resume:old')
  expect(calls).toContain('delete:old')
  expect(calls).toContain('session:old:doc-1')
})
