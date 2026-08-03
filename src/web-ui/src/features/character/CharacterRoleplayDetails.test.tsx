import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterRoleplayDetails } from './CharacterRoleplayDetails'

const background = { id: '학자', label: '학자', description: '', skills: [], equipment: [], personality: ['기록을 남긴다.'], ideals: ['지식'], bonds: ['도서관'], flaws: ['고집'] }
const values = { personality: '', ideal: '', bond: '', flaw: '', appearance: '' }
const help = { personality: '성격', ideal: '이상', bond: '유대', flaw: '단점' }

describe('CharacterRoleplayDetails', () => {
  it('배경 예시와 직접 입력을 같은 상태 계약으로 전달한다', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<CharacterRoleplayDetails background={background} help={help} values={values} onChange={onChange} />)
    await user.selectOptions(screen.getByLabelText('인격 특성 예시'), '기록을 남긴다.')
    expect(onChange).toHaveBeenCalledWith({ ...values, personality: '기록을 남긴다.' })
    fireEvent.change(screen.getByLabelText('외형 묘사'), { target: { value: '검은 로브' } })
    expect(onChange).toHaveBeenLastCalledWith({ ...values, appearance: '검은 로브' })
  })
})
