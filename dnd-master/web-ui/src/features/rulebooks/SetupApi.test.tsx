import { afterEach, describe, expect, it, vi } from 'vitest'
import { HttpSetupApi } from './SetupApi'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('HttpSetupApi', () => {
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
      },
    })
    await expect(api.getRuntimeOptions?.()).resolves.toMatchObject({
      defaultEngineId: 'ollama',
      defaultToolIds: ['search', 'move'],
    })
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/scenario-packages/package-1/play-preparation', expect.any(Object))
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/runtime-options', expect.any(Object))
  })
})
