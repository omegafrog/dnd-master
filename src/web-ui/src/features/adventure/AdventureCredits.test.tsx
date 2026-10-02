import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AdventureCredits } from './AdventureCredits'

describe('AdventureCredits', () => {
  it('presents committed story beats and cast while omitting mechanical combat results', () => {
    render(<AdventureCredits adventureId="adventure-1" party={[
      { characterSheetId: 'hero-1', name: '검증용 용사', controlMode: 'DIRECT' },
      { characterSheetId: 'companion-1', name: '린', controlMode: 'AGENT' },
    ]} entries={[
      { sequence: 0, speaker: 'AI_GAME_MASTER', content: '폐허 아래 저장고 입구에 도착했습니다.' },
      { sequence: 1, speaker: 'PLAYER', content: '횃불을 켜고 계단을 내려간다.' },
      { sequence: 2, speaker: 'AI_GAME_MASTER', content: '확정 전투 결과: d20=14, hit.' },
      { sequence: 3, speaker: 'AI_GAME_MASTER', content: '횃불 아래에서 거대 쥐가 모습을 드러냅니다.' },
      { sequence: 4, speaker: 'PLAYER', content: 'attack' },
      { sequence: 5, speaker: 'AI_GAME_MASTER', content: '확정 전투 결과: hit.' },
      { sequence: 6, speaker: 'AI_GAME_MASTER', content: '검이 거대 쥐에게 닿았습니다.' },
      { sequence: 7, speaker: 'PLAYER', content: '저장고를 정리하고 글로우킨에게 보고한다.' },
      { sequence: 8, speaker: 'AI_GAME_MASTER', content: '저장고를 정리한 뒤 글로우킨에게 안전하다고 보고했습니다.' },
    ]} />)

    expect(screen.getByRole('heading', { name: '모험의 막이 내렸습니다' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: '모험 여정 요약' })).toBeInTheDocument()
    expect(screen.getByText('폐허 아래 저장고 입구에 도착했습니다.')).toBeInTheDocument()
    expect(screen.getByText('횃불 아래에서 거대 쥐가 모습을 드러냅니다.')).toBeInTheDocument()
    expect(screen.getByText('횃불을 켜고 계단을 내려간다.')).toBeInTheDocument()
    expect(screen.getByText('거대 쥐를 향해 공격했습니다.')).toBeInTheDocument()
    expect(screen.getByText('저장고를 정리한 뒤 글로우킨에게 안전하다고 보고했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '모험의 출연진' })).toBeInTheDocument()
    expect(screen.getByText('검증용 용사')).toBeInTheDocument()
    expect(screen.queryByText(/확정 전투 결과/)).not.toBeInTheDocument()
  })
})
