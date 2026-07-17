export type Edition = '2014' | '2024'
export type CharacterSheet = { edition: Edition; name: string; armorClass: number; proficiencyBonus?: number; heroicInspiration?: boolean }
export type DiceRole = 'PLAYER_ACTION' | 'NPC' | 'ENEMY' | 'SECRET_CHECK'
export type MapLayer = { id: string; label: string; visibility: 'PLAYER_VISIBLE' | 'AI_ONLY' }
export type CombatMapView = { token: { x: number; y: number }; layers: MapLayer[] }
export type SavedAdventure = { id: string; title: string; updatedAt: string }

export interface AdventurePlayApi {
  getCharacter(adventureId: string): Promise<CharacterSheet>
  roll(adventureId: string, role: DiceRole, expression: string): Promise<{ total: number }>
  getMap(adventureId: string): Promise<CombatMapView>
  move(adventureId: string, path: string): Promise<CombatMapView>
  listSaved(): Promise<SavedAdventure[]>
  save(adventureId: string): Promise<SavedAdventure>
  resume(adventureId: string): Promise<void>
  delete(adventureId: string): Promise<void>
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, init)
  if (response.status === 409 || response.status === 422) throw new Error('적용 규칙상 해당 이동을 할 수 없습니다.')
  if (!response.ok) throw new Error('모험 요청을 처리하지 못했습니다.')
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export class HttpAdventurePlayApi implements AdventurePlayApi {
  getCharacter(id: string) { return request<CharacterSheet>(`/api/public/adventures/${id}/character-sheet`) }
  roll(id: string, role: DiceRole, expression: string) {
    return request<{ total: number }>(`/api/public/adventures/${id}/dice-rolls`, { method: 'POST',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ role, expression }) })
  }
  getMap(id: string) { return request<CombatMapView>(`/api/public/adventures/${id}/combat-map`) }
  move(id: string, path: string) { return request<CombatMapView>(`/api/public/adventures/${id}/combat-map/movements`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ path }) }) }
  listSaved() { return request<SavedAdventure[]>('/api/public/adventures/saved') }
  save(id: string) { return request<SavedAdventure>(`/api/public/adventures/${id}/save`, { method: 'PUT' }) }
  resume(id: string) { return request<void>(`/api/public/adventures/${id}/resume`, { method: 'POST' }) }
  delete(id: string) { return request<void>(`/api/public/adventures/${id}`, { method: 'DELETE' }) }
}
