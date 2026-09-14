import '@testing-library/jest-dom/vitest'
import { StrictMode } from 'react'
import { render, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { PreparationFlow } from './PreparationFlow'
import type { ScenarioBundleView, SetupApi } from './SetupApi'

describe('PreparationFlow', () => {
  it('sends the main scenario document once in React strict mode', async () => {
    const startScenarioCompilation = vi.fn(async () => ({
      compilationId: 'compilation-1', bundleId: 'bundle-1', bundleRevision: 1,
      status: 'REQUESTED' as const, attempt: 0, packageId: null, failureReason: null,
    }))
    const getScenarioCompilation = vi.fn(async () => ({
      compilationId: 'compilation-1', bundleId: 'bundle-1', bundleRevision: 1,
      status: 'BLOCKED' as const, attempt: 0, packageId: null, failureReason: 'blocked',
    }))
    const api = {
      startScenarioCompilation,
      getScenarioCompilation,
      getScenarioPackage: vi.fn(),
    } as unknown as SetupApi
    const bundle: ScenarioBundleView = {
      bundleId: 'bundle-1', currentRevision: 1,
      documents: [
        { knowledgeDocumentId: 'main', documentType: 'STORYBOOK', originalFilename: 'main.pdf', status: 'INDEXED', role: 'MAIN_SCENARIO', extractionVersion: 1 },
        { knowledgeDocumentId: 'handout', documentType: 'STORYBOOK', originalFilename: 'handout.pdf', status: 'INDEXED', role: 'HANDOUT', extractionVersion: 1 },
        { knowledgeDocumentId: 'rules', documentType: 'RULEBOOK', originalFilename: 'rules.pdf', status: 'INDEXED', role: 'RULEBOOK', extractionVersion: 1 },
      ],
    }

    window.localStorage.removeItem('dnd-preparation:bundle-1:1')
    render(<StrictMode><PreparationFlow api={api} playerId="player-1" bundle={bundle} onError={() => {}} /></StrictMode>)

    await waitFor(() => expect(startScenarioCompilation).toHaveBeenCalledOnce())
    expect(startScenarioCompilation).toHaveBeenCalledWith(
      'bundle-1', 'player-1', expect.stringContaining(':setup:'), { primaryStorybookId: 'main' },
    )
  })
})
