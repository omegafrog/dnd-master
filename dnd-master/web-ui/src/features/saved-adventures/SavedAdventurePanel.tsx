import { useEffect, useState } from 'react'
import type { AdventurePlayApi, SavedAdventure } from './AdventurePlayApi'

export function SavedAdventurePanel({ api, playerId }: { api: AdventurePlayApi; playerId: string }) {
  const [items, setItems] = useState<SavedAdventure[]>([])
  const [message, setMessage] = useState('')
  useEffect(() => { void api.listSaved(playerId).then(setItems).catch(() => {}) }, [api, playerId])

  async function resume(id: string) {
    try {
      await api.resume(id)
      setMessage('모험을 재개했습니다.')
    } catch {
      setMessage('모험을 재개하지 못했습니다.')
    }
  }

  async function remove(id: string) {
    try {
      await api.deleteAdventure(id, playerId, 0)
      setItems(old => old.filter(x => x.id !== id))
      setMessage('모험을 삭제했습니다.')
    } catch {
      setMessage('모험을 삭제하지 못했습니다.')
    }
  }

  return (
    <section aria-labelledby="saved-heading">
      <h2 id="saved-heading">저장한 모험</h2>
      <p role="status">{message}</p>
      {items.length === 0 && <p>저장된 모험이 없습니다.</p>}
      <ul>
        {items.map(item => (
          <li key={item.id}>
            <strong>{item.title}</strong>
            <button onClick={() => void resume(item.id)}>재개</button>
            <button onClick={() => void remove(item.id)}>삭제</button>
          </li>
        ))}
      </ul>
    </section>
  )
}
