import { type FormEvent, useEffect, useState } from 'react'
import type { AdventurePlayApi, CombatMapView as CombatMapState } from '../saved-adventures/AdventurePlayApi'

export function CombatMapView({ adventureId, api }: { adventureId: string; api: AdventurePlayApi }) {
  const [map, setMap] = useState<CombatMapState | null>(null)
  const [message, setMessage] = useState('')

  useEffect(() => {
    void api.getCombatMap(adventureId).then(setMap).catch(() => setMap(null))
  }, [adventureId, api])

  async function move(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage('전투 맵 이동 기능은 준비 중입니다.')
  }

  return (
    <section aria-labelledby="map-heading">
      <h2 id="map-heading">플레이어 전투 맵</h2>
      <p>모험 ID: {adventureId}</p>
      <p role="status">{map ? `현재 맵 상태: ${map.status}` : '전투 맵을 불러오는 중…'}</p>
      <form onSubmit={move}>
        <label>이동 경로<input name="path" required /></label>
        <button type="submit">이동</button>
      </form>
      <p role="status">{message}</p>
    </section>
  )
}
