import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { CharacterSheetView } from '../character/CharacterSheetView'
import { RoleDiceRoller } from '../dice/RoleDiceRoller'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import { CombatMapView } from './CombatMapView'

function fakeApi(): AdventurePlayApi {
  const submitMapAction = vi.fn(async () => ({ turnId: 't1', version: 1 }))
  return {
    async getCharacter() {
      return {
        characterSheetId: 'cs-1', name: "Lae'zel", edition: '2024',
        armorClass: 17, strength: 16, dexterity: 14, constitution: 15,
        intelligence: 10, wisdom: 12, charisma: 8,
      }
    },
    async getCombatMap() { return { adventureId: 'a1', status: 'authoritative-map', mapId: 'm1', version: 0, sessionVersion: 7, grid: { width: 3, height: 2 }, tokens: [{ id: 'p1', type: 'PLAYER', x: 1, y: 1 }] } },
    submitMapAction,
    async rollDice() { return { rollId: 'r1', total: 19 } },
    async listSaved() { return [] },
    async save() { return { adventureId: 'a1', newVersion: 1 } },
    async resume() {},
    async deleteAdventure() {},
    async getSessionKnowledgeSet() { return { adventureId: 'a1', sessionId: 's1', knowledgeDocumentIds: [] } },
    async saveSessionKnowledgeSet() { return { adventureId: 'a1', sessionId: 's1', knowledgeDocumentIds: [] } },
  }
}

it('shows character sheet, rolls dice, and shows combat map', async () => {
  const api = fakeApi()
  const user = userEvent.setup()
  render(<>
    <CharacterSheetView sheetId="cs-1" api={api} />
    <RoleDiceRoller adventureId="a1" api={api} />
    <CombatMapView adventureId="a1" api={api} />
  </>)
  expect(await screen.findByRole('heading', { name: /Lae'zel/ })).toBeInTheDocument()
  expect(screen.getByText('17')).toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('담당 역할'), 'ENEMY')
  await user.click(screen.getByRole('button', { name: '굴리기' }))
  expect(await screen.findByText('결과: 19')).toBeInTheDocument()
  expect(await screen.getByRole('heading', { name: '플레이어 전투 맵' })).toBeInTheDocument()
  expect(await screen.findByText('현재 맵 상태: authoritative-map')).toBeInTheDocument()
})

it('keeps a drag candidate local until confirmed, and cancel sends nothing', async () => {
  const api = fakeApi()
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} />)

  await user.click(await screen.findByRole('button', { name: /PLAYER.*1,1/ }))
  await user.click(screen.getByRole('button', { name: '격자 2,1' }))

  expect(screen.getByRole('dialog', { name: '맵 행동 확인' })).toHaveTextContent('이동: (1,1) → (2,1)')
  await user.click(screen.getByRole('button', { name: '취소' }))
  expect(screen.queryByRole('dialog', { name: '맵 행동 확인' })).not.toBeInTheDocument()
  expect(api.submitMapAction).not.toHaveBeenCalled()
})

it('submits exactly one typed map action after confirmation', async () => {
  const api = fakeApi()
  const user = userEvent.setup()
  render(<CombatMapView adventureId="a1" api={api} />)
  await user.click(await screen.findByRole('button', { name: /PLAYER.*1,1/ }))
  await user.click(screen.getByRole('button', { name: '격자 2,1' }))
  await user.click(screen.getByRole('button', { name: '확인' }))
  expect(api.submitMapAction).toHaveBeenCalledTimes(1)
  expect(api.submitMapAction).toHaveBeenCalledWith('a1', expect.objectContaining({ action: 'MOVE', path: [{ x: 1, y: 1 }, { x: 2, y: 1 }] }), undefined, 7)
})
