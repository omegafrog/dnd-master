import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Progress } from '../../components/ui/progress'

describe('Tactical scene preparation progress', () => {
  it('exposes Shard CN progress and an actionable retry reason', () => {
    render(<section aria-label="Shard CN 전술 장면 준비"><p>지하 묘지</p><Progress value={100} aria-label="Shard CN 전술 장면 준비 진행률" /><p role="alert">전술 장면 준비에 실패했습니다. 적 배치 근거를 확인해 주세요.</p><button type="button">전술 장면 다시 준비</button></section>)
    expect(screen.getByLabelText('Shard CN 전술 장면 준비 진행률')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('적 배치 근거')
    expect(screen.getByRole('button', { name: '전술 장면 다시 준비' })).toBeTruthy()
  })

  it('renders unknown totals as accessible indeterminate progress', () => {
    render(<Progress value={null} aria-label="전술 장면 준비 진행률" />)
    const progress = screen.getByLabelText('전술 장면 준비 진행률')
    expect(progress.getAttribute('aria-valuenow')).toBeNull()
    expect(progress.getAttribute('data-state')).toBe('indeterminate')
  })
})
