import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CharacterRoleplayDetails } from './CharacterRoleplayDetails'

const background = { id: '학자', label: '학자', description: '', skills: [], equipment: [], personality: ['기록을 남긴다.'], ideals: ['지식'], bonds: ['도서관'], flaws: ['고집'] }
const values = { personality: '', ideal: '', bond: '', flaw: '' }
const help = { personality: '성격', ideal: '이상', bond: '유대', flaw: '단점' }

describe('CharacterRoleplayDetails', () => {
  it('배경별 역할극 선택지를 상태에 전달한다', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<CharacterRoleplayDetails background={background} help={help} values={values} onChange={onChange} />)
    await user.selectOptions(screen.getByLabelText('인격 특성 선택'), '기록을 남긴다.')
    expect(onChange).toHaveBeenCalledWith({ ...values, personality: '기록을 남긴다.' })
    expect(screen.queryByLabelText('외형 묘사')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('인격 특성 직접 작성')).not.toBeInTheDocument()
  })
})
