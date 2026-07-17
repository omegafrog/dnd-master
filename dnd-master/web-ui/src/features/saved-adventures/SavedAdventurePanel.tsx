import { useEffect, useState } from 'react'
import type { AdventurePlayApi, SavedAdventure } from './AdventurePlayApi'

export function SavedAdventurePanel({ currentAdventureId, api }: { currentAdventureId: string; api: AdventurePlayApi }) {
  const [items, setItems] = useState<SavedAdventure[]>([]); const [message, setMessage] = useState('')
  useEffect(() => { void api.listSaved().then(setItems) }, [api])
  async function save() { const item = await api.save(currentAdventureId); setItems(old => [...old.filter(x => x.id !== item.id), item]); setMessage('모험을 저장했습니다.') }
  async function resume(id: string) { await api.resume(id); setMessage('모험을 재개했습니다.') }
  async function remove(id: string) { await api.delete(id); setItems(old => old.filter(x => x.id !== id)); setMessage('모험을 삭제했습니다.') }
  return <section aria-labelledby="saved-heading"><h2 id="saved-heading">저장한 모험</h2><button onClick={() => void save()}>현재 모험 저장</button>
    <p role="status">{message}</p><ul>{items.map(item => <li key={item.id}><strong>{item.title}</strong>
      <button onClick={() => void resume(item.id)}>재개</button><button onClick={() => void remove(item.id)}>삭제</button></li>)}</ul></section>
}
