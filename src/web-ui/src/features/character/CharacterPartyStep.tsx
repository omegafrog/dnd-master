import type { SessionControlMode } from '../adventure-session/AdventureSessionApi'

export function CharacterPartyStep({ partyMemberIds, createdCharacterSheetId, mode, onModeChange, onAdd }: {
  partyMemberIds: string[]
  createdCharacterSheetId?: string
  mode: SessionControlMode
  onModeChange: (mode: SessionControlMode) => void
  onAdd: () => void
}) {
  return <section aria-label="파티 구성"><h3>일행 추가</h3>
    {partyMemberIds.map(characterSheetId => <p key={characterSheetId}>{characterSheetId}</p>)}
    {createdCharacterSheetId && <>
      <p>생성한 캐릭터: {createdCharacterSheetId}</p>
      <select aria-label="조작 방식" value={mode} onChange={event => onModeChange(event.currentTarget.value as SessionControlMode)}>
        <option value="DIRECT">직접 조작</option><option value="AGENT">에이전트 조작</option>
      </select>
      <button type="button" onClick={onAdd}>생성한 캐릭터를 파티에 추가</button>
    </>}
  </section>
}
