import { useEffect, useState } from 'react'
import type { AdventurePlayApi, CharacterSheet } from '../saved-adventures/AdventurePlayApi'

export function CharacterSheetView({ sheetId, api }: { sheetId: string; api: AdventurePlayApi }) {
  const [sheet, setSheet] = useState<CharacterSheet | null>(null)
  useEffect(() => { void api.getCharacter(sheetId).then(setSheet) }, [sheetId, api])
  if (!sheet) return <p>캐릭터 시트 불러오는 중…</p>
  return (
    <section aria-labelledby="sheet-heading">
      <h2 id="sheet-heading">{sheet.name} ({sheet.edition})</h2>
      <dl>
        <dt>방어도</dt><dd>{sheet.armorClass}</dd>
        <dt>힘</dt><dd>{sheet.strength}</dd>
        <dt>민첩</dt><dd>{sheet.dexterity}</dd>
        <dt>건강</dt><dd>{sheet.constitution}</dd>
        <dt>지능</dt><dd>{sheet.intelligence}</dd>
        <dt>지혜</dt><dd>{sheet.wisdom}</dd>
        <dt>매력</dt><dd>{sheet.charisma}</dd>
      </dl>
    </section>
  )
}
