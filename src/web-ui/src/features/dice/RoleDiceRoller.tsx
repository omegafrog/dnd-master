import { type FormEvent, useState } from 'react'
import type { AdventurePlayApi } from '../saved-adventures/AdventurePlayApi'

export function RoleDiceRoller({ adventureId, api }: { adventureId: string; api: AdventurePlayApi }) {
  const [total, setTotal] = useState<number | null>(null)
  const [rolling, setRolling] = useState(false)

  async function roll(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setRolling(true)
    try {
      const result = await api.rollDice(
        adventureId,
        String(form.get('ruleSetId') || ''),
        String(form.get('characterSheetId') || ''),
        String(form.get('role')),
        String(form.get('expression')),
      )
      setTotal(result.total)
    } catch {
      setTotal(null)
    } finally {
      setRolling(false)
    }
  }

  return (
    <section className="adventure-tool dice-panel" aria-labelledby="dice-heading">
      <h2 id="dice-heading">주사위 굴림</h2>
      <form onSubmit={roll}>
        <label>담당 역할
          <select name="role">
            <option value="PLAYER_ACTION">플레이어 행동</option>
            <option value="NPC">NPC</option>
            <option value="ENEMY">적</option>
          </select>
        </label>
        <label>주사위 식<input name="expression" defaultValue="1d20" required /></label>
        <label>규칙 세트 ID<input name="ruleSetId" /></label>
        <label>캐릭터 시트 ID<input name="characterSheetId" /></label>
        <button type="submit" disabled={rolling}>{rolling ? '굴리는 중…' : '굴리기'}</button>
      </form>
      <output aria-live="polite">{total === null ? '' : `결과: ${total}`}</output>
    </section>
  )
}
