import { type FormEvent, useState } from 'react'
import type { AdventurePlayApi, DiceRole } from '../saved-adventures/AdventurePlayApi'

export function RoleDiceRoller({ adventureId, api }: { adventureId: string; api: AdventurePlayApi }) {
  const [total, setTotal] = useState<number | null>(null)
  async function roll(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget)
    const result = await api.roll(adventureId, String(form.get('role')) as DiceRole, String(form.get('expression')))
    setTotal(result.total)
  }
  return <section aria-labelledby="dice-heading"><h2 id="dice-heading">주사위 굴림</h2><form onSubmit={roll}>
    <label>담당 역할<select name="role"><option>PLAYER_ACTION</option><option>NPC</option><option>ENEMY</option><option>SECRET_CHECK</option></select></label>
    <label>주사위 식<input name="expression" defaultValue="1d20" required /></label><button>굴리기</button></form>
    <output aria-live="polite">{total === null ? '' : `결과: ${total}`}</output></section>
}
