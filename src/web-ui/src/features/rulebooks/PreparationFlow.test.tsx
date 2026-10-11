import '@testing-library/jest-dom/vitest'
import { StrictMode } from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { advancePreparationProgress, PreparationFlow } from './PreparationFlow'
import type { PlayPreparationView, ScenarioBundleView, SetupApi } from './SetupApi'
import type { AdventureSessionView } from '../adventure-session/AdventureSessionApi'

describe('PreparationFlow', () => {
  it('advances smoothly without claiming completion while the server is still working', () => {
    expect(advancePreparationProgress(70)).toBeGreaterThan(70)
    expect(advancePreparationProgress(87.99)).toBeLessThanOrEqual(88)
    expect(advancePreparationProgress(88)).toBe(88)
  })

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

  it('creates play preparation for a completed package using the persisted request key', async () => {
    const scenarioPackage = {
      packageId: 'package-11-units', bundleId: 'bundle-1', bundleRevision: 1,
      inputFingerprint: 'fingerprint', reportStatus: 'COMPLETE' as const, warnings: [],
      characterLimit: { maximumCharacters: 4, source: null, sourceQuote: '' },
      units: Array.from({ length: 11 }, () => ({
        kind: 'CHECK', status: 'COMPLETE' as const, abilityOrSkill: 'Wisdom', dc: 12,
        diceExpression: '1d20', visibility: 'PUBLIC', sourceQuote: '', provenance: 'source',
        validationMessages: [], runtimeCapabilities: [], detail: {
          triggerCondition: null, actor: null, roller: null, instructionVisibility: null,
        },
      })),
    }
    const preparation = {
      preparationId: 'preparation-1', scenarioPackageId: scenarioPackage.packageId,
      bundleId: 'bundle-1', bundleRevision: 1, status: 'READY' as const, blockers: [],
      characterCreationBlueprint: {
        available: true, summary: null, rulebookDocumentCount: 1, storybookDocumentCount: 1,
        diagnostics: [], status: 'PUBLISHED' as const, revision: 3,
      },
      characterLimit: scenarioPackage.characterLimit,
    } satisfies PlayPreparationView
    const createPlayPreparation = vi.fn<(packageId: string, requestKey: string) => Promise<PlayPreparationView>>()
      .mockResolvedValue(preparation)
    const getPlayPreparation = vi.fn(async () => { throw new Error('package id is not a preparation id') })
    const createSession = vi.fn<(request: { scenarioPackageId: string; blueprintId: string; blueprintRevision: number; partySize?: number; preparationId?: string }) => Promise<AdventureSessionView>>()
      .mockResolvedValue({ sessionId: 'session-1' } as AdventureSessionView)
    const onAdventureCreated = vi.fn()
    const api = {
      startScenarioCompilation: vi.fn(async () => ({
        compilationId: 'compilation-1', bundleId: 'bundle-1', bundleRevision: 1,
        status: 'COMPLETED' as const, attempt: 1, packageId: scenarioPackage.packageId, failureReason: null,
      })),
      getScenarioCompilation: vi.fn(),
      getScenarioPackage: vi.fn(async () => scenarioPackage),
      createPlayPreparation,
      getPlayPreparation,
    } as unknown as SetupApi
    const bundle: ScenarioBundleView = {
      bundleId: 'bundle-1', currentRevision: 1,
      documents: [{ knowledgeDocumentId: 'main', documentType: 'STORYBOOK', originalFilename: 'main.pdf', status: 'INDEXED', role: 'MAIN_SCENARIO', extractionVersion: 1 }],
    }
    const requestStorageKey = `play-preparation-request:${scenarioPackage.packageId}`
    window.localStorage.removeItem('dnd-preparation:bundle-1:1')
    window.localStorage.removeItem(requestStorageKey)
    const onError = vi.fn()
    const diagnostics = vi.spyOn(console, 'info').mockImplementation(() => {})

    try {
      render(<StrictMode><PreparationFlow api={api} playerId="player-1" bundle={bundle} onError={onError}
        sessionApi={{ create: createSession }} onAdventureCreated={onAdventureCreated} /></StrictMode>)

      const createAdventureButton = await screen.findByRole('button', { name: '모험 만들기' })
      const requestKey = window.localStorage.getItem(requestStorageKey)
      expect(requestKey).toBeTruthy()
      expect(createPlayPreparation).toHaveBeenCalled()
      expect(createPlayPreparation.mock.calls.every(([packageId, key]) => packageId === scenarioPackage.packageId && key === requestKey)).toBe(true)
      expect(getPlayPreparation).not.toHaveBeenCalled()
      expect(onError).not.toHaveBeenCalled()
      expect(diagnostics).toHaveBeenCalledWith('dev_preparation', expect.objectContaining({
        stage: 'character_setup_request_started', bundleId: 'bundle-1', packageId: scenarioPackage.packageId,
        branch: 'new_key_saved',
      }))
      expect(diagnostics).toHaveBeenCalledWith('dev_preparation', expect.objectContaining({
        stage: 'character_setup_ready', packageId: scenarioPackage.packageId,
        expected: 'preparation view', actual: 'READY',
      }))
      await userEvent.click(createAdventureButton)
      await waitFor(() => expect(createSession).toHaveBeenCalledWith(expect.objectContaining({
        scenarioPackageId: scenarioPackage.packageId, blueprintId: scenarioPackage.packageId,
        blueprintRevision: 3, preparationId: preparation.preparationId,
      })))
      expect(onAdventureCreated).toHaveBeenCalledWith(expect.objectContaining({ sessionId: 'session-1' }))
    } finally {
      diagnostics.mockRestore()
    }
  })
})
