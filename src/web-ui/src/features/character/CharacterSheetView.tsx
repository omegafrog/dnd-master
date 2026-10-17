import { useEffect, useState } from 'react'
import type { AdventurePlayApi, CharacterSheet } from '../saved-adventures/AdventurePlayApi'

export function CharacterSheetView({ sheetId, api }: { sheetId: string; api: AdventurePlayApi }) {
  const [sheet, setSheet] = useState<CharacterSheet | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    setError(null)
    void api.getCharacter(sheetId).then(setSheet).catch(reason => {
      setError(reason instanceof Error ? reason.message : '캐릭터 시트를 불러오지 못했습니다.')
    })
  }, [sheetId, api])
  if (error) return <p role="alert">{error}</p>
  if (!sheet) return <p>캐릭터 시트 불러오는 중…</p>
  const abilities = [
    ['힘', 'STR', sheet.strength],
    ['민첩', 'DEX', sheet.dexterity],
    ['건강', 'CON', sheet.constitution],
    ['지능', 'INT', sheet.intelligence],
    ['지혜', 'WIS', sheet.wisdom],
    ['매력', 'CHA', sheet.charisma],
  ] as const
  const recoveryConditionLabel = (condition: string) => {
    const names: Record<string, string> = { LONG_REST: '긴 휴식', SHORT_REST: '짧은 휴식' }
    const name = names[condition]
    return name ? `${name} (저장된 회복 조건 식별자: ${condition})` : `저장된 회복 조건 식별자: ${condition}`
  }
  return (
    <section className="character-sheet-page" aria-labelledby="sheet-heading">
      <div className="page-heading character-sheet-heading"><div><p className="eyebrow">CHARACTER RECORD</p><h1 id="sheet-heading">{sheet.name}</h1><p>모험에 고정된 캐릭터 능력치와 방어 정보를 확인합니다.</p></div><span className="status-chip">{sheet.edition}</span></div>
      <nav className="character-sheet-actions" aria-label="캐릭터 다음 이동">
        <button type="button" onClick={() => {
          if (window.history.length > 1) window.history.back()
          else window.location.hash = '#/adventures'
        }}>이전 화면</button>
        <a className="button button-secondary" href="#/adventures">모험 목록</a>
        <a className="button" href="#/setup">새 모험 준비</a>
      </nav>
      <div className="character-sheet-summary">
        <div className="armor-class-card"><small>ARMOR CLASS</small><strong>{sheet.armorClass}</strong><span>방어도</span></div>
        <div className="character-ability-grid">
          {abilities.map(([label, abbreviation, value], index) => (
            <article className={`character-ability-card ability-tone-${index + 1}`} key={abbreviation}>
              <small>{abbreviation}</small><strong>{value}</strong><span>{label}</span>
            </article>
          ))}
        </div>
      </div>
      <section aria-labelledby="sheet-resources-heading" className="character-sheet-resources">
        <h2 id="sheet-resources-heading">배운 능력과 자원</h2>
        {(sheet.learnedAbilityIds?.length ?? 0) > 0 && <ul aria-label="배운 능력의 고유 식별자">{sheet.learnedAbilityIds!.map(id => <li key={id}>능력의 고유 식별자: {id}</li>)}</ul>}
        {Object.entries(sheet.resources ?? {}).length === 0 ? <p>저장된 능력 자원이 없습니다.</p> : Object.entries(sheet.resources ?? {}).map(([id, resource]) => <article key={id}>
          <h3>자원 고유 식별자: {id}</h3><p>현재 {resource.current}/{resource.maximum} · 회복 조건 {recoveryConditionLabel(resource.recoveryCondition)}</p>
          {resource.abilityIds.length > 0 && <p>연결된 능력의 고유 식별자: {resource.abilityIds.join(', ')}</p>}
        </article>)}
      </section>
    </section>
  )
}
