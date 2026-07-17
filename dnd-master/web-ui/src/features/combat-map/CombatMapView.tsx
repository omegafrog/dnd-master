import { type FormEvent, useEffect, useState } from 'react'
import type { AdventurePlayApi, CombatMapView as MapView } from '../saved-adventures/AdventurePlayApi'

export function CombatMapView({ adventureId, api }: { adventureId: string; api: AdventurePlayApi }) {
  const [map, setMap] = useState<MapView | null>(null); const [message, setMessage] = useState('')
  useEffect(() => { void api.getMap(adventureId).then(setMap) }, [adventureId, api])
  async function move(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const path = String(new FormData(event.currentTarget).get('path'))
    try { setMap(await api.move(adventureId, path)); setMessage('이동했습니다.') }
    catch (error) { setMessage(error instanceof Error ? error.message : '이동할 수 없습니다.') }
  }
  if (!map) return <p>전투 맵 불러오는 중…</p>
  const visible = map.layers.filter(layer => layer.visibility === 'PLAYER_VISIBLE')
  return <section aria-labelledby="map-heading"><h2 id="map-heading">플레이어 전투 맵</h2>
    <p>현재 위치: {map.token.x}, {map.token.y}</p><ul aria-label="공개 맵 계층">{visible.map(layer => <li key={layer.id}>{layer.label}</li>)}</ul>
    <form onSubmit={move}><label>이동 경로<input name="path" required /></label><button>이동</button></form><p role="status">{message}</p></section>
}
