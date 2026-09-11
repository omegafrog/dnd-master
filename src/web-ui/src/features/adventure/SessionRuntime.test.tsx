import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SessionRuntime } from './SessionRuntime'
import type { AdventureApi } from './AdventureApi'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'

vi.mock('./AdventureStream', () => ({ AdventureStream: () => <div>게임 기록</div> }))

describe('SessionRuntime', () => {
  it('접기 버튼으로 플레이 캐릭터 패널을 숨겼다가 다시 펼친다', async () => {
    const user = userEvent.setup()
    render(
      <SessionRuntime
        adventureId="adventure-1"
        adventureApi={{} as AdventureApi}
        playApi={{} as AdventurePlayApi}
        partyCharacters={[{ characterSheetId: 'sheet-1', name: '아리아', characterClass: '위저드', level: 1 }]}
      />,
    )

    expect(screen.getByText('아리아')).toBeVisible()
    await user.click(screen.getByRole('button', { name: '플레이 캐릭터 패널 접기' }))
    expect(screen.queryByText('아리아')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '플레이 캐릭터 패널 펼치기' })).toHaveAttribute('aria-expanded', 'false')

    await user.click(screen.getByRole('button', { name: '플레이 캐릭터 패널 펼치기' }))
    expect(screen.getByText('아리아')).toBeVisible()
  })

  it('게임 시작 후 핸드아웃 목록을 항상 표시하고 열람할 수 있다', async () => {
    const user = userEvent.setup()
    const getHandoutPreview = vi.fn().mockResolvedValue({ content: '문 안쪽에 숨겨진 단서', originalFilename: '던전 단서.txt' })
    render(
      <SessionRuntime
        adventureId="adventure-1"
        adventureApi={{} as AdventureApi}
        playApi={{} as AdventurePlayApi}
        handouts={[{ knowledgeDocumentId: 'document-1', originalFilename: '던전 단서.txt' }]}
        getHandoutPreview={getHandoutPreview}
      />,
    )

    expect(screen.getByRole('heading', { name: '핸드아웃' })).toBeVisible()
    const handoutButton = screen.getByRole('button', { name: '던전 단서.txt 열기' })
    expect(handoutButton).toBeVisible()
    await user.click(handoutButton)
    expect(await screen.findByText('문 안쪽에 숨겨진 단서')).toBeVisible()
    expect(getHandoutPreview).toHaveBeenCalledWith('document-1')
  })
})
