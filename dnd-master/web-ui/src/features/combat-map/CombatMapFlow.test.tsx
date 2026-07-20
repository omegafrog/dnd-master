import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { CharacterSheetView } from '../character/CharacterSheetView'
import { RoleDiceRoller } from '../dice/RoleDiceRoller'
import type { AdventurePlayApi, DiceRole } from '../saved-adventures/AdventurePlayApi'
import { CombatMapView } from './CombatMapView'

function fakeApi(): AdventurePlayApi & { role?: DiceRole } {
  return {
    async getCharacter() { return { edition: '2024', name: 'Lae’zel', armorClass: 17, heroicInspiration: true } },
    async roll(_id, role) { this.role = role; return { total: 19 } },
    async getMap() { return { token: { x: 1, y: 2 }, layers: [
      { id: 'visible', label: 'Stone wall', visibility: 'PLAYER_VISIBLE' },
      { id: 'secret', label: 'Hidden ambush', visibility: 'AI_ONLY' }] } },
    async move(_id, path) { if (path === 'too far') throw new Error('적용 규칙상 해당 이동을 할 수 없습니다.'); return this.getMap('a1') },
    async listSaved() { return [] }, async save() { throw new Error() }, async resume() {}, async delete() {},
  }
}

it('shows edition sheet, role dice, player-visible map and movement rejection through Adventure API', async () => {
  const api = fakeApi(); const user = userEvent.setup()
  render(<><CharacterSheetView adventureId="a1" api={api} /><RoleDiceRoller adventureId="a1" api={api} /><CombatMapView adventureId="a1" api={api} /></>)
  expect(await screen.findByRole('heading', { name: '2024 캐릭터 시트' })).toBeInTheDocument()
  expect(screen.getByText('보유')).toBeInTheDocument()
  expect(await screen.findByText('Stone wall')).toBeInTheDocument()
  expect(screen.queryByText('Hidden ambush')).not.toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('담당 역할'), 'ENEMY')
  await user.click(screen.getByRole('button', { name: '굴리기' }))
  expect(await screen.findByText('결과: 19')).toBeInTheDocument(); expect(api.role).toBe('ENEMY')
  await user.type(screen.getByLabelText('이동 경로'), 'too far'); await user.click(screen.getByRole('button', { name: '이동' }))
  expect(await screen.findByText(/적용 규칙상 해당 이동을 할 수 없습니다/)).toBeInTheDocument()
})
