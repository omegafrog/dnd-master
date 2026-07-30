import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterCreationPage } from './CharacterCreationPage'

describe('CharacterCreationPage', () => {
  it('renders explicit input mode instead of inferring control from options', async () => {
    const setupApi = {
      getPlayPreparation: vi.fn().mockResolvedValue({ scenarioPackageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, status: 'READY', blockers: [], characterLimit: { maximumCharacters: 2, source: null, sourceQuote: '' }, characterCreationBlueprint: { available: true, summary: 'published blueprint', rulebookDocumentCount: 1, storybookDocumentCount: 0, diagnostics: [], revision: 1, status: 'PUBLISHED', fields: [
        { key: 'race', options: [], required: true, sourceType: 'RULEBOOK', inputStatus: 'EXTRACTED', inputMode: 'SINGLE_SELECT', suggestions: ['Elf'], diagnostics: [], evidence: [] },
      ] } }),
      createCharacterSheet: vi.fn(),
    }
    const sessionApi = { read: vi.fn().mockResolvedValue({ sessionId: 'session-1', scenarioPackageId: 'package-1', blueprintRevision: 1, characterLimit: 2, version: 0, status: 'DRAFT', party: [], adventureId: null, runtimeConfiguration: null }), addMember: vi.fn(), start: vi.fn() }

    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)

    expect(await screen.findByRole('combobox', { name: 'race' })).toBeTruthy()
  })

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

  it('submits nested blueprint values as starting abilities', async () => {
    const createCharacterSheet = vi.fn().mockResolvedValue({ characterSheetId: 'sheet-1', adventureId: 'adventure-1', edition: 'DND_5E_2024', characterName: 'Aria', level: 1, inspiration: false, version: 0 })
    const setupApi = {
      getPlayPreparation: vi.fn().mockResolvedValue({ scenarioPackageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, status: 'READY', blockers: [], characterLimit: { maximumCharacters: 2, source: null, sourceQuote: '' }, characterCreationBlueprint: { available: true, summary: 'published', rulebookDocumentCount: 1, storybookDocumentCount: 1, diagnostics: [], revision: 4, status: 'PUBLISHED', fields: [], roots: [{
        id: 'node-scores', parentId: null, key: 'starting_ability_scores', label: 'Scores', inputMode: 'FREE_TEXT', value: null, options: [], suggestions: [], status: 'EXTRACTED', allowUserAddChild: false, confidence: 'HIGH', sourceQuote: '', diagnostics: [], sourceEvidence: [], children: [{
          id: 'node-str', parentId: 'node-scores', key: 'str', label: 'STR', inputMode: 'FREE_TEXT', value: null, options: [], suggestions: [], status: 'EXTRACTED', allowUserAddChild: false, confidence: 'HIGH', sourceQuote: '', diagnostics: [], sourceEvidence: [], children: [],
        }],
      }] } }),
      createCharacterSheet,
    }
    const sessionApi = { read: vi.fn().mockResolvedValue({ sessionId: 'session-1', scenarioPackageId: 'package-1', blueprintRevision: 4, characterLimit: 2, version: 0, status: 'DRAFT', party: [], adventureId: null, runtimeConfiguration: null }), addMember: vi.fn(), start: vi.fn() }
    const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    const strInput = (await screen.findAllByLabelText('STR')).find(element => element.tagName === 'INPUT')!
    await user.type(strInput, '12')
    await user.type(screen.getByLabelText('이름'), 'Aria')
    await user.click(screen.getByRole('button', { name: '캐릭터 시트 생성' }))
    expect(createCharacterSheet).toHaveBeenCalledWith(expect.objectContaining({ startingAbilities: 'str=12' }))
  })

  it('stores subrace, equipped armor, and level-up HP as build and state', async () => {
    const createCharacterSheet = vi.fn().mockResolvedValue({ characterSheetId: 'sheet-1', adventureId: 'adventure-1', edition: 'DND_5E_2024', characterName: 'Aria', level: 2, inspiration: false, version: 0 })
    const setupApi = { getPlayPreparation: vi.fn().mockResolvedValue({ scenarioPackageId: 'package-1', bundleId: 'bundle-1', bundleRevision: 1, status: 'READY', blockers: [], characterLimit: { maximumCharacters: 2, source: null, sourceQuote: '' }, characterCreationBlueprint: { available: true, summary: 'published', rulebookDocumentCount: 1, storybookDocumentCount: 0, diagnostics: [], revision: 1, status: 'PUBLISHED', fields: [
      { key: 'race', options: ['Elf'], required: true, sourceType: 'RULEBOOK', inputStatus: 'EXTRACTED', inputMode: 'SINGLE_SELECT', suggestions: [], diagnostics: [], evidence: [] },
      { key: 'class', options: ['Ranger'], required: true, sourceType: 'RULEBOOK', inputStatus: 'EXTRACTED', inputMode: 'SINGLE_SELECT', suggestions: [], diagnostics: [], evidence: [] },
    ] } }) , createCharacterSheet }
    const sessionApi = { read: vi.fn().mockResolvedValue({ sessionId: 'session-1', scenarioPackageId: 'package-1', blueprintRevision: 1, characterLimit: 2, version: 0, status: 'DRAFT', party: [], adventureId: null, runtimeConfiguration: null }), addMember: vi.fn(), start: vi.fn() }
    const user = userEvent.setup()
    render(<CharacterCreationPage sessionId="session-1" setupApi={setupApi} sessionApi={sessionApi} />)
    await user.selectOptions(await screen.findByRole('combobox', { name: 'race' }), 'Elf')
    await user.selectOptions(screen.getByRole('combobox', { name: 'class' }), 'Ranger')
    await user.selectOptions(screen.getByLabelText('하위 종족'), 'Wood Elf')
    await user.selectOptions(screen.getByLabelText('장착 갑옷'), 'scale mail')
    await user.click(screen.getByLabelText('방패 장착'))
    await user.clear(screen.getByLabelText('캐릭터 레벨'))
    await user.type(screen.getByLabelText('캐릭터 레벨'), '2')
    await user.selectOptions(await screen.findByLabelText('2레벨 HP 방식'), 'AVERAGE')
    await user.type(screen.getByLabelText('캐릭터 이름'), 'Aria')
    await user.click(screen.getByRole('button', { name: '캐릭터 시트 생성' }))
    expect(createCharacterSheet).toHaveBeenCalledWith(expect.objectContaining({
      characterBuild: expect.stringContaining('Wood Elf'), characterState: expect.stringContaining('AVERAGE'),
    }))
  })
})
