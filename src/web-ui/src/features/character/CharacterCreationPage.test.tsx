import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterCreationPage } from './CharacterCreationPage'

describe('CharacterCreationPage', () => {
  it('waits for a real session, then posts session id and blueprint revision', async () => {
    const sessionApi = {
      read: vi.fn().mockResolvedValue({ sessionId: 'session-1', scenarioPackageId: 'package-1', blueprintRevision: 4, characterLimit: 2, version: 0, status: 'DRAFT', party: [], adventureId: null, runtimeConfiguration: null }),
      addMember: vi.fn(), start: vi.fn(),
    }
    const createCharacterSheet = vi.fn().mockResolvedValue({ characterSheetId: 'sheet-1', adventureId: 'adventure-1', edition: 'DND_5E_2024', characterName: 'Aria', level: 1, inspiration: false, version: 0 })
    const setupApi = {
      getPlayPreparation: vi.fn().mockResolvedValue({ scenarioPackageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, status: 'READY', blockers: [], characterLimit: { maximumCharacters: 2, source: null, sourceQuote: '' }, characterCreationBlueprint: { available: true, summary: 'published blueprint', rulebookDocumentCount: 1, storybookDocumentCount: 1, diagnostics: [], revision: 4, status: 'PUBLISHED', fields: [] } }),
      createCharacterSheet,
    }
    const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    expect(await screen.findByText('세션 ID: session-1')).toBeTruthy()
    await user.type(screen.getByLabelText('이름'), 'Aria')
    await user.click(screen.getByRole('button', { name: '캐릭터 시트 생성' }))
    expect(createCharacterSheet).toHaveBeenCalledWith(expect.objectContaining({ sessionId: 'session-1', blueprintRevision: 4, characterName: 'Aria' }))
  })
})
