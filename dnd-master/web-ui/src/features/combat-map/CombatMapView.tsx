import { type FormEvent, useState } from 'react'

export function CombatMapView({ adventureId }: { adventureId: string }) {
  const [message, setMessage] = useState('')

  async function move(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage('전투 맵 이동 기능은 준비 중입니다.')
  }

  return (
    <section aria-labelledby="map-heading">
      <h2 id="map-heading">플레이어 전투 맵</h2>
      <p>모험 ID: {adventureId}</p>
      <form onSubmit={move}>
        <label>이동 경로<input name="path" required /></label>
        <button type="submit">이동</button>
      </form>
      <p role="status">{message}</p>
    </section>
  )
}
