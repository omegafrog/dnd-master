import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterPartyStep } from './CharacterPartyStep'

describe('character party add flow', () => {
  it('sends the created sheet with its control mode and renders the updated party', async () => {
    const addMember = vi.fn().mockResolvedValue({
      version: 2,
      party: [{ characterSheetId: 'existing-sheet' }, { characterSheetId: 'new-sheet' }],
    })

    function Harness() {
      const [mode, setMode] = useState<'DIRECT' | 'AGENT'>('DIRECT')
      const [party, setParty] = useState(['existing-sheet'])
      return <CharacterPartyStep
        partyMemberIds={party}
        createdCharacterSheetId="new-sheet"
        mode={mode}
        onModeChange={setMode}
        onAdd={() => void addMember('session-1', 1, {
          characterSheetId: 'new-sheet', controlMode: mode,
          nameMutableAfterStart: false, raceMutableAfterStart: false,
          characterClassMutableAfterStart: false, backgroundMutableAfterStart: false,
          startingAbilitiesMutableAfterStart: false, levelMutableAfterStart: false,
        }).then(next => setParty(next.party.map((member: { characterSheetId: string }) => member.characterSheetId)))}
      />
    }

    const user = userEvent.setup()
    render(<Harness />)
    await user.selectOptions(screen.getByLabelText('조작 방식'), 'AGENT')
    await user.click(screen.getByRole('button', { name: '생성한 캐릭터를 파티에 추가' }))

    expect(addMember).toHaveBeenCalledWith('session-1', 1, expect.objectContaining({
      characterSheetId: 'new-sheet', controlMode: 'AGENT',
    }))
    expect(await screen.findByText('new-sheet')).toBeTruthy()
  })
})
