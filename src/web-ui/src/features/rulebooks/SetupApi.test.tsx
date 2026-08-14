import { afterEach, describe, expect, it, vi } from 'vitest'
import { HttpSetupApi } from './SetupApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('HttpSetupApi', () => {
  it('preflights the active endpoint and exposes Codex login state', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify([{ id: 'endpoint-1', provider: 'CODEX_CLI', active: true }]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ healthy: false, detail: 'Codex OAuth session unavailable' }), { status: 200 }))
    await expect(new HttpSetupApi(() => 'owner-token').preflightAgentEndpoint()).resolves.toMatchObject({
      configured: true, connected: false, state: 'LOGIN_REQUIRED', provider: 'CODEX_CLI',
    })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    fetchMock.mockRestore()
  })
  it('deletes a scenario bundle through the authenticated API', async () => {
    const fetchMock = vi.fn(async () => ({ status: 204, ok: true, headers: new Headers() } as Response))
    vi.stubGlobal('fetch', fetchMock)

    await new HttpSetupApi(() => 'owner-token').deleteScenarioBundle('bundle-1')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/adventures/scenario-bundles/bundle-1', {
      method: 'DELETE',
      headers: { Authorization: 'Bearer owner-token' },
    })
  })

  it('reads legacy scenario deprecation metadata from response headers', async () => {
    const fetchMock = vi.fn(async () => ({
      status: 202,
      ok: true,
      headers: new Headers({
        Deprecation: 'true',
        Sunset: 'Fri, 31 Dec 2027 00:00:00 GMT',
        Warning: '299 dnd-master "Legacy one-file scenario upload is deprecated; migrate to bundle/package flows"',
        'X-Legacy-Scenario-Id': 'scenario-123',
      }),
    } as Response))
    vi.stubGlobal('fetch', fetchMock)

    const api = new HttpSetupApi(() => 'owner-token')
    const result = await api.uploadScenario(new File(['legacy'], 'legacy.pdf', { type: 'application/pdf' }))

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result).toEqual({
      id: 'scenario-123',
      name: 'legacy.pdf',
      deprecated: true,
      deprecationMessage: 'Legacy one-file scenario upload is deprecated; migrate to bundle/package flows',
      legacyScenarioId: 'scenario-123',
      sunset: 'Fri, 31 Dec 2027 00:00:00 GMT',
    })
  })

  it('throws when the legacy scenario id header is missing', async () => {
    const fetchMock = vi.fn(async () => ({
      status: 202,
      ok: true,
      headers: new Headers({
        Deprecation: 'true',
      }),
    } as Response))
    vi.stubGlobal('fetch', fetchMock)

    const api = new HttpSetupApi(() => 'owner-token')

    await expect(api.uploadScenario(new File(['legacy'], 'legacy.pdf', { type: 'application/pdf' })))
      .rejects.toThrow('레거시 시나리오 식별자가 없습니다.')
  })

  it('throws on a bad request response', async () => {
    const fetchMock = vi.fn(async () => ({
      status: 400,
      ok: false,
      headers: new Headers(),
    } as Response))
    vi.stubGlobal('fetch', fetchMock)

    const api = new HttpSetupApi(() => 'owner-token')

    await expect(api.uploadScenario(new File(['legacy'], 'legacy.pdf', { type: 'application/pdf' })))
      .rejects.toThrow('지원하지 않거나 손상된 파일입니다.')
  })

  it('reads play preparation and runtime options from the new endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        status: 200,
        ok: true,
        headers: new Headers(),
        json: async () => ({
          scenarioPackageId: 'package-1',
          bundleId: 'bundle-1',
          bundleRevision: 1,
          status: 'READY',
          blockers: [],
          characterCreationBlueprint: {
            available: true,
            summary: 'STORYBOOK 1개, RULEBOOK 1개',
            rulebookDocumentCount: 1,
            storybookDocumentCount: 1,
            diagnostics: [],
            baseSchema: { edition: 'DND_5E_2014', fields: [] },
            storybookProposals: [{
              proposalId: 'proposal-1', key: 'alignment', label: 'Alignment', description: 'Scenario restriction',
              sourceDocument: { knowledgeDocumentId: 'doc-1', originalFilename: 'story.pdf', extractionVersion: 3 },
              sourceQuote: 'Only elves.', evidence: [{ locator: 'page:4', excerpt: 'Only elves.' }],
              decisionState: 'UNDECIDED', readinessState: 'READY',
            }],
            storybookExtractionState: 'EXTRACTION_PARTIAL_CONFIRMED',
          },
        }),
      } as Response)
      .mockResolvedValueOnce({
        status: 200,
        ok: true,
        headers: new Headers(),
        json: async () => ({
          defaultEngineId: 'ollama',
          defaultToolIds: ['search', 'move'],
          engines: [{ id: 'ollama', label: 'Ollama', selectedByDefault: true }],
          tools: [{ id: 'search', label: 'Search', selectedByDefault: true }],
        }),
      } as Response)
    vi.stubGlobal('fetch', fetchMock)

    const api = new HttpSetupApi(() => 'owner-token')

    await expect(api.getPlayPreparation?.('package-1')).resolves.toMatchObject({
      status: 'READY',
      characterCreationBlueprint: {
        available: true,
        baseSchema: { edition: 'DND_5E_2014', fields: [] },
        storybookProposals: [{ proposalId: 'proposal-1', decisionState: 'UNDECIDED' }],
        storybookExtractionState: 'EXTRACTION_PARTIAL_CONFIRMED',
      },
    })
    await expect(api.getRuntimeOptions?.()).resolves.toMatchObject({
      defaultEngineId: 'ollama',
      defaultToolIds: ['search', 'move'],
    })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/scenario-packages/package-1/play-preparation', expect.any(Object))
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/runtime-options', expect.any(Object))
  })

  it('saves a blueprint node with expected revision and can add a child', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, headers: new Headers(), json: async () => ({}) } as Response)
    vi.stubGlobal('fetch', fetchMock)
    const api = new HttpSetupApi(() => 'owner-token')

    await api.resolveBlueprint?.('package-1', 'node-race', 'Elf', 7)
    await api.addBlueprintChild?.('package-1', 8, 'node-scores', 'con', 'CON')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/scenario-packages/package-1/character-blueprint/resolve')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body as string)).toEqual({ expectedRevision: 7, fieldKey: 'node-race', value: 'Elf' })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body as string)).toEqual({ expectedRevision: 8, parentId: 'node-scores', key: 'con', label: 'CON' })
  })

  it('generates a blueprint from the selected catalog rulebook revision', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, headers: new Headers(), json: async () => ({}) } as Response)
    vi.stubGlobal('fetch', fetchMock)
    const api = new HttpSetupApi(() => 'owner-token')

    await api.generateBlueprintDraft?.('package-1', 'catalog-5e', 2)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/scenario-packages/package-1/character-blueprint/draft?catalogRulebookId=catalog-5e&catalogExtractionVersion=2',
      expect.any(Object),
    )
  })

  it('creates a character sheet through the internal character endpoint', async () => {
    const fetchMock = vi.fn(async () => ({
      status: 200,
      ok: true,
      headers: new Headers(),
      json: async () => ({
        characterSheetId: 'sheet-1',
        adventureId: 'adventure-1',
        edition: 'DND_5E_2024',
        characterName: 'Aria',
        level: 3,
        inspiration: true,
        version: 0,
      }),
    } as Response))
    vi.stubGlobal('fetch', fetchMock)

    const api = new HttpSetupApi(() => 'owner-token')
    await expect(api.createCharacterSheet?.({
      edition: 'DND_5E_2024',
      characterName: 'Aria',
      level: 3,
      inspiration: true,
    })).resolves.toMatchObject({
      characterSheetId: 'sheet-1',
      adventureId: 'adventure-1',
    })
    expect(fetchMock).toHaveBeenCalledWith('/internal/v1/character-sheets', expect.any(Object))
  })
})
