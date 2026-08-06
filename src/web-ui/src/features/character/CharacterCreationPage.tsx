import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CharacterCreationDraft, SetupApi } from '../rulebooks/SetupApi'
import { CharacterSheetCreatorView } from './CharacterSheetCreatorView'

type CharacterSetupApi = { getPlayPreparation: NonNullable<SetupApi['getPlayPreparation']>; createCharacterSheet?: SetupApi['createCharacterSheet']; resolveBlueprint?: SetupApi['resolveBlueprint']; addBlueprintChild?: SetupApi['addBlueprintChild']; publishBlueprint?: SetupApi['publishBlueprint'] }
type SessionApi = Pick<AdventureSessionApi, 'read' | 'addMember'>

export function CharacterCreationPage({ sessionId, ownerPlayerId, setupApi, sessionApi }: { sessionId: string; ownerPlayerId: string; setupApi: CharacterSetupApi; sessionApi: SessionApi }) {
  async function save(draft: Omit<CharacterCreationDraft, 'sessionId'>) {
    if (!setupApi.createCharacterSheet) throw new Error('캐릭터 생성 API를 사용할 수 없습니다.')
    const created = await setupApi.createCharacterSheet({ ...draft, sessionId, ownerPlayerId })
    const session = await sessionApi.read(sessionId)
    await sessionApi.addMember(sessionId, session.version, {
      characterSheetId: created.characterSheetId,
      controlMode: 'DIRECT',
      nameMutableAfterStart: false,
      raceMutableAfterStart: false,
      characterClassMutableAfterStart: false,
      backgroundMutableAfterStart: false,
      startingAbilitiesMutableAfterStart: false,
      levelMutableAfterStart: false,
    })
    window.location.hash = `#/sessions/${sessionId}/party`
  }
  return <CharacterSheetCreatorView onSave={save} blueprint={{ sessionId, setupApi, sessionApi }} />
}
