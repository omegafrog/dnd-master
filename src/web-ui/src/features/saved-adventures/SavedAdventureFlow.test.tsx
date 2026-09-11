import '@testing-library/jest-dom/vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import type { SetupApi } from '../rulebooks/SetupApi'
import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { AdventurePlayApi } from './AdventurePlayApi'
import { SavedAdventurePanel } from './SavedAdventurePanel'
import { toSavedAdventure } from './AdventurePlayApi'

afterEach(() => vi.restoreAllMocks())

it('maps the backend adventureId contract to the UI id contract', () => {
  expect(toSavedAdventure({ adventureId: 'adventure-1', name: '고성의 밤', status: 'SAVED', version: 4 })).toEqual({
    id: 'adventure-1', title: '고성의 밤', statusLabel: '진행 중인 모험', resumable: true, updatedAt: '', version: 4,
  })
})

it('keeps an interrupted scenario bundle in the adventure list', async () => {
  const api = { async listSaved() { return [] } } as unknown as AdventurePlayApi
  const setupApi = {
    listKnowledgeDocuments: async () => [],
    async listScenarioBundles() { return [{ bundleId: 'bundle-1', name: '중단한 모험', currentRevision: 2, documents: [] }] },
  } as unknown as SetupApi
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" forceList />)

  expect(await screen.findByText('중단한 모험')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /준비 이어하기/ })).toHaveAttribute('href', '#/bundles/bundle-1')
})

it('cleans connected sessions before deleting an interrupted scenario bundle', async () => {
  const deleteScenarioBundle = vi.fn().mockResolvedValue(undefined)
  const setupApi = {
    listKnowledgeDocuments: async () => [],
    async listScenarioBundles() { return [{ bundleId: 'bundle-1', name: '중단한 모험', currentRevision: 2, documents: [] }] },
    listScenarioPackages: vi.fn().mockResolvedValue([{ packageId: 'package-1' }]),
    deleteScenarioBundle,
  } as unknown as SetupApi
  const deleteSession = vi.fn().mockResolvedValue({ sessionId: 'session-1', status: 'DELETED', version: 6 })
  const sessionApi = {
    listByScenarioPackage: vi.fn().mockResolvedValue([{ sessionId: 'session-1', status: 'STARTING', version: 5 }]),
    delete: deleteSession,
  } as unknown as Pick<AdventureSessionApi, 'listByScenarioPackage' | 'delete'>
  const playApi = { async listSaved() { return [] } } as unknown as AdventurePlayApi
  const user = userEvent.setup()

  render(<SavedAdventurePanel playApi={playApi} setupApi={setupApi} sessionApi={sessionApi} playerId="p1" forceList />)

  await screen.findByText('중단한 모험')
  await user.click(screen.getByRole('button', { name: '중단한 모험 삭제' }))

  await waitFor(() => expect(deleteSession).toHaveBeenCalledWith('session-1', 5))
  await waitFor(() => expect(deleteScenarioBundle).toHaveBeenCalledWith('bundle-1'))
  expect(screen.queryByText('중단한 모험')).not.toBeInTheDocument()
})

it('keeps an interrupted bundle visible beside saved adventures', async () => {
  const api = { async listSaved() { return [{ id: 'saved', title: '이미 저장된 모험', statusLabel: '진행 중인 모험' as const, resumable: true, updatedAt: '', version: 1, scenarioBundleId: 'saved-bundle' }] } } as unknown as AdventurePlayApi
  const setupApi = {
    listKnowledgeDocuments: async () => [],
    async listScenarioBundles() { return [
      { bundleId: 'saved-bundle', name: '이미 저장된 모험', currentRevision: 1, documents: [] },
      { bundleId: 'bundle-2', name: '중단한 두 번째 모험', currentRevision: 3, documents: [] },
    ] },
  } as unknown as SetupApi
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" forceList />)

  expect(await screen.findByText('중단한 두 번째 모험')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /준비 이어하기/ })).toHaveAttribute('href', '#/bundles/bundle-2')
  expect(screen.getAllByRole('listitem')).toHaveLength(2)
})

it('opens an interrupted runtime through its session page', async () => {
  const api = {
    async listSaved() { return [{ id: 'adventure-1', title: '맵 준비 중', statusLabel: '진행 중인 모험' as const, resumable: true, updatedAt: '', version: 1, sessionId: 'session-1' }] },
  } as unknown as AdventurePlayApi
  const setupApi = { listKnowledgeDocuments: async () => [] } as unknown as SetupApi
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" forceList />)

  await screen.findByText('맵 준비 중')
  expect(screen.getByRole('link', { name: /열기/ })).toHaveAttribute('href', '#/sessions/session-1/party')
})

it('routes an empty adventure entry directly to creation', async () => {
  window.location.hash = '#/adventures'
  const api = { async listSaved() { return [] } } as unknown as AdventurePlayApi
  const setupApi = { listKnowledgeDocuments: async () => [] } as unknown as SetupApi
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" />)
  await waitFor(() => expect(window.location.hash).toBe('#/setup?mode=create'))
})

it('routes a single adventure entry directly to its workspace', async () => {
  window.location.hash = '#/adventures'
  const api = {
    async listSaved() { return [{ id: 'solo', title: '고성의 밤', statusLabel: '진행 중인 모험' as const, resumable: true, updatedAt: '', version: 1 }] },
  } as unknown as AdventurePlayApi
  const setupApi = { listKnowledgeDocuments: async () => [] } as unknown as SetupApi
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" />)
  await waitFor(() => expect(window.location.hash).toBe('#/adventures/solo?tab=materials'))
})

it('shows the new adventure prompt instead of redirecting from the adventure menu', async () => {
  window.location.hash = '#/adventures'
  const api = { async listSaved() { return [] } } as unknown as AdventurePlayApi
  const setupApi = { listKnowledgeDocuments: async () => [] } as unknown as SetupApi
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" forceList />)
  expect(await screen.findByRole('heading', { name: '새로운 모험을 시작하세요' })).toBeInTheDocument()
  expect(window.location.hash).toBe('#/adventures')
})

it('shows user-facing adventure states and only offers resume for resumable adventures', async () => {
  const api = {
    async listSaved() { return [
      { id: 'active', title: '고성의 밤', statusLabel: '진행 중인 모험', resumable: true, updatedAt: '', version: 1 },
      { id: 'done', title: '끝난 여정', statusLabel: '완료한 모험', resumable: false, updatedAt: '', version: 2 },
    ] },
    async resume() {}, async deleteAdventure() {}, async getSessionKnowledgeSet() { throw new Error() },
    async saveSessionKnowledgeSet() { throw new Error() }, async getCharacter() { throw new Error() },
    async rollDice() { throw new Error() }, async save() { throw new Error() }, async getCombatMap() { throw new Error() },
  } satisfies AdventurePlayApi
  const setupApi = { listKnowledgeDocuments: async () => [] } as unknown as SetupApi
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" />)
  expect(await screen.findByText('진행 중인 모험')).toBeInTheDocument()
  expect(screen.getByText('완료한 모험')).toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: '재개' })).toHaveLength(1)
  expect(screen.queryByText('active')).not.toBeInTheDocument()
})

it('lists, resumes, and deletes adventures', async () => {
  const calls: string[] = []
  const api: AdventurePlayApi = {
    async listSaved() { return [{ id: 'old', title: 'Old Keep', statusLabel: '진행 중인 모험' as const, resumable: true, updatedAt: '2026-01-01', version: 4 }] },
    async save() { return { adventureId: 'new', newVersion: 1 } },
    async resume(id) { calls.push(`resume:${id}`) },
    async deleteAdventure(id) { calls.push(`delete:${id}`) },
    async getSessionKnowledgeSet() { throw new Error() },
    async saveSessionKnowledgeSet() { throw new Error() },
    async getCombatMap() { return { adventureId: 'old', status: 'authoritative-map' } },
    async getCharacter() { throw new Error() },
    async rollDice() { throw new Error() },
  }
  const setupApi = { listKnowledgeDocuments: async () => [] } as unknown as SetupApi
  const user = userEvent.setup()
  const resumed: string[] = []
  render(<SavedAdventurePanel playApi={api} setupApi={setupApi} playerId="p1" onResumed={id => resumed.push(id)} forceList />)
  expect(await screen.findByText('Old Keep')).toBeInTheDocument()
  expect(screen.queryByText('레거시 시나리오 마이그레이션')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '재개' }))
  expect(screen.getByText('모험을 재개했습니다.')).toBeInTheDocument()
  expect(resumed).toEqual(['old'])
  await user.click(screen.getByRole('button', { name: 'Old Keep 삭제' }))
  expect(screen.queryByText('Old Keep')).not.toBeInTheDocument()
  expect(calls).toContain('resume:old')
  expect(calls).toContain('delete:old')
})
