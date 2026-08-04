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
    </section>
  )
}
