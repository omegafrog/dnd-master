import type { AdventureSessionApi } from '../adventure-session/AdventureSessionApi'
import type { CharacterCreationDraft, SetupApi } from '../rulebooks/SetupApi'
import { CharacterSheetCreatorView } from './CharacterSheetCreatorView'

type CharacterSetupApi = Pick<SetupApi, 'getPlayPreparation' | 'createCharacterSheet'>
type SessionApi = Pick<AdventureSessionApi, 'read' | 'addMember'>

export function CharacterCreationPage({ sessionId, setupApi, sessionApi }: { sessionId: string; setupApi: CharacterSetupApi; sessionApi: SessionApi }) {
  void sessionId
  void setupApi
  void sessionApi
  async function save(draft: Omit<CharacterCreationDraft, 'sessionId'>) {
    if (!setupApi.createCharacterSheet) throw new Error('캐릭터 생성 API를 사용할 수 없습니다.')
    const created = await setupApi.createCharacterSheet({ ...draft, sessionId })
    window.location.hash = `#/character/${created.characterSheetId}`
  }
  return <CharacterSheetCreatorView onSave={save} />
}
