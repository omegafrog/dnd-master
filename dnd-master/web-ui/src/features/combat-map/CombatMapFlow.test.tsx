import '@testing-library/jest-dom/vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { CharacterSheetView } from '../character/CharacterSheetView'
import { RoleDiceRoller } from '../dice/RoleDiceRoller'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'
import { CombatMapView } from './CombatMapView'

function fakeApi(): AdventurePlayApi {
  return {
    async getCharacter() {
      return {
        characterSheetId: 'cs-1', name: "Lae'zel", edition: '2024',
        armorClass: 17, strength: 16, dexterity: 14, constitution: 15,
        intelligence: 10, wisdom: 12, charisma: 8,
      }
    },
    async getCombatMap() { return { adventureId: 'a1', status: 'authoritative-map' } },
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
