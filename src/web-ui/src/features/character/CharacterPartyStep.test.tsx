import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterPartyStep } from './CharacterPartyStep'

describe('CharacterPartyStep', () => {
  it('생성된 캐릭터의 조작 방식과 파티 추가 동작을 전달한다', async () => {
    const user = userEvent.setup()
    const onModeChange = vi.fn()
    const onAdd = vi.fn()
    render(<CharacterPartyStep partyMemberIds={['existing-sheet']} createdCharacterSheetId="new-sheet" mode="DIRECT" onModeChange={onModeChange} onAdd={onAdd} />)
    expect(screen.getByText('existing-sheet')).toBeTruthy()
    await user.selectOptions(screen.getByLabelText('조작 방식'), 'AGENT')
    expect(onModeChange).toHaveBeenCalledWith('AGENT')
    await user.click(screen.getByRole('button', { name: '생성한 캐릭터를 파티에 추가' }))
    expect(onAdd).toHaveBeenCalledTimes(1)
  })
})
