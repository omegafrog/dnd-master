import { useEffect, useState } from 'react'
import type { AdventurePlayApi, CharacterSheet } from '../saved-adventures/AdventurePlayApi'

export function CharacterSheetView({ adventureId, api }: { adventureId: string; api: AdventurePlayApi }) {
  const [sheet, setSheet] = useState<CharacterSheet | null>(null)
  useEffect(() => { void api.getCharacter(adventureId).then(setSheet) }, [adventureId, api])
  if (!sheet) return <p>캐릭터 시트 불러오는 중…</p>
  return <section aria-labelledby="sheet-heading"><h2 id="sheet-heading">{sheet.edition} 캐릭터 시트</h2>
    <dl><dt>이름</dt><dd>{sheet.name}</dd><dt>방어도</dt><dd>{sheet.armorClass}</dd>
      {sheet.edition === '2014' && <><dt>숙련 보너스</dt><dd>{sheet.proficiencyBonus}</dd></>}
      {sheet.edition === '2024' && <><dt>영웅적 고양</dt><dd>{sheet.heroicInspiration ? '보유' : '없음'}</dd></>}
    </dl></section>
}
