import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterSpellSelection } from './CharacterSpellSelection'

const rule = { model: 'PREPARED', cantripCount: 2, learnedSpellCount: 0, preparedSpellCount: 2, firstLevelSlots: 2, recovery: 'LONG_REST' } as const

describe('CharacterSpellSelection', () => {
  it('선택 개수를 제한하고 선택 해제를 허용한다', async () => {
    const user = userEvent.setup()
    const onCantripsChange = vi.fn()
    const { rerender } = render(<CharacterSpellSelection
      rule={rule}
      cantripOptions={['빛', '저항', '안내']}
      firstLevelOptions={['축복']}
      selectedCantrips={['빛', '저항']}
      selectedFirstLevelSpells={[]}
      requiredCantrips={2}
      requiredFirstLevelSpells={1}
      automaticSpells={[]}
      onCantripsChange={onCantripsChange}
      onFirstLevelSpellsChange={vi.fn()}
    />)

    expect((screen.getByLabelText('안내') as HTMLInputElement).disabled).toBe(true)
    await user.click(screen.getByLabelText('빛'))
    expect(onCantripsChange).toHaveBeenCalledWith(['저항'])

    rerender(<CharacterSpellSelection
      rule={rule}
      cantripOptions={['빛', '저항', '안내']}
      firstLevelOptions={['축복']}
      selectedCantrips={['저항']}
      selectedFirstLevelSpells={[]}
      requiredCantrips={2}
      requiredFirstLevelSpells={1}
      automaticSpells={[]}
      onCantripsChange={onCantripsChange}
      onFirstLevelSpellsChange={vi.fn()}
    />)
    expect((screen.getByLabelText('안내') as HTMLInputElement).disabled).toBe(false)
  })

  it('주문 모델과 자동 권역 주문을 표시한다', () => {
    render(<CharacterSpellSelection
      rule={rule}
      cantripOptions={[]}
      firstLevelOptions={[]}
      selectedCantrips={[]}
      selectedFirstLevelSpells={[]}
      requiredCantrips={0}
      requiredFirstLevelSpells={0}
      automaticSpells={['축복', '상처 치료']}
      onCantripsChange={vi.fn()}
      onFirstLevelSpellsChange={vi.fn()}
    />)

    expect(screen.getByText(/준비 주문/).textContent).toContain('긴 휴식')
    expect(screen.getByText(/자동 권역 주문/).textContent).toContain('축복')
  })
})
