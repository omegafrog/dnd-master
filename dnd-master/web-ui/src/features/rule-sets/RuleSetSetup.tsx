import { type FormEvent, useState } from 'react'
import type { RulebookView, SetupApi } from '../rulebooks/SetupApi'

export function RuleSetSetup({ api, rulebooks, onError }: {
  api: SetupApi
  rulebooks: RulebookView[]
  onError(message: string): void
}) {
  const [saved, setSaved] = useState(false)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const edition = String(form.get('edition')) as '2014' | '2024'
    const ids = form.getAll('rulebooks').map(String)
    if (ids.length === 0) {
      onError('적용할 룰북을 하나 이상 선택하세요.')
      return
    }
    try {
      await api.saveRuleSet(edition, ids)
      setSaved(true)
    } catch (error) {
      onError(error instanceof Error ? error.message : '룰 세트를 저장하지 못했습니다.')
    }
  }

  return (
    <section aria-labelledby="rules-heading">
      <h2 id="rules-heading">적용 룰 세트</h2>
      <form onSubmit={save}>
        <label>판본
          <select name="edition" defaultValue="2024">
            <option value="2014">2014</option>
            <option value="2024">2024</option>
          </select>
        </label>
        <fieldset>
          <legend>내 룰북 선택</legend>
          {rulebooks.filter(book => book.owned && book.status === 'READY').map(book => (
            <label key={book.id}>
              <input type="checkbox" name="rulebooks" value={book.id} />{book.name}
            </label>
          ))}
        </fieldset>
        <button type="submit">룰 세트 저장</button>
      </form>
      {saved && <p>룰 세트가 저장되었습니다.</p>}
    </section>
  )
}
