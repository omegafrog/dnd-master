import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../features/auth/AuthContext'
import { LoginForm } from '../features/auth/LoginForm'
import { HttpAdventureApi } from '../features/adventure/AdventureApi'
import { AdventureStream } from '../features/adventure/AdventureStream'
import { HttpAdventurePlayApi } from '../features/saved-adventures/AdventurePlayApi'
import { SavedAdventurePanel } from '../features/saved-adventures/SavedAdventurePanel'
import { HttpRuleGuidanceApi } from '../features/rule-guidance/RuleGuidanceApi'
import { RuleEvidence } from '../features/rule-guidance/RuleEvidence'
import { HttpSetupApi } from '../features/rulebooks/SetupApi'
import { RulebookSetup } from '../features/rulebooks/RulebookSetup'
import { CharacterSheetView } from '../features/character/CharacterSheetView'
import { CharacterCreationPage } from '../features/character/CharacterCreationPage'
import { CharacterBlueprintReviewPage } from '../features/character/CharacterBlueprintReviewPage'
import { PackageBlueprintReviewPage } from '../features/character/PackageBlueprintReviewPage'
import { RoleDiceRoller } from '../features/dice/RoleDiceRoller'
import { CombatMapView } from '../features/combat-map/CombatMapView'
import { AdventureSessionApi } from '../features/adventure-session/AdventureSessionApi'
import { AdventureSessionPanel } from '../features/adventure-session/AdventureSessionPanel'

type Route =
  | { page: 'setup' }
  | { page: 'adventures' }
  | { page: 'adventure'; adventureId: string }
  | { page: 'character'; sheetId: string }
  | { page: 'session'; sessionId: string }
  | { page: 'character-create'; sessionId: string }
  | { page: 'character-blueprint'; sessionId: string }
  | { page: 'package-blueprint'; packageId: string }
  | { page: 'login' }

function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '') || '/login'
  const segments = path.split('/').filter(Boolean)
  if (segments[0] === 'setup') return { page: 'setup' }
  if (segments[0] === 'adventures' && segments[1]) return { page: 'adventure', adventureId: segments[1] }
  if (segments[0] === 'adventures') return { page: 'adventures' }
  if (segments[0] === 'character' && segments[1]) return { page: 'character', sheetId: segments[1] }
  if (segments[0] === 'scenario-packages' && segments[1] && segments[2] === 'character-blueprint') {
    return { page: 'package-blueprint', packageId: segments[1] }
  }
  if (segments[0] === 'sessions' && segments[1] && segments[2] === 'character-blueprint') return { page: 'character-blueprint', sessionId: segments[1] }
  if (segments[0] === 'sessions' && segments[1] && segments[2] === 'character') return { page: 'character-create', sessionId: segments[1] }
  if (segments[0] === 'sessions' && segments[1]) return { page: 'session', sessionId: segments[1] }
  return { page: 'login' }
}

export function AppShell() {
  const auth = useAuth()
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash))
  const sessionApi = useMemo(() => new AdventureSessionApi(auth.session?.accessToken ?? ''), [auth.session?.accessToken])

  const onHashChange = useCallback(() => setRoute(parseRoute(window.location.hash)), [])
  useEffect(() => {
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [onHashChange])

  if (!auth.session) {
    return <><header><h1>D&amp;D Master</h1></header><main id="main"><p role="status" aria-live="polite">{auth.message}</p><LoginForm /></main></>
  }

  const token = auth.session.accessToken
  const playerId = auth.session.playerId
  const getToken = () => token
  const getPlayerId = () => playerId
  const adventureApi = new HttpAdventureApi(getToken, getPlayerId)
  const playApi = new HttpAdventurePlayApi(getToken)
  const guidanceApi = new HttpRuleGuidanceApi(getToken, getPlayerId)
  const setupApi = createNavigatingSetupApi(token)

  return <>
    <header><a href="#main">본문으로 건너뛰기</a><h1>D&amp;D Master</h1><nav aria-label="주요 메뉴"><a href="#/setup">자료 설정</a><a href="#/adventures">모험 목록</a><button type="button" onClick={() => void auth.logout()}>로그아웃</button></nav></header>
    <main id="main">
      <p role="status" aria-live="polite">{auth.message}</p><p>{auth.session.playerName}님 환영합니다!</p>
      {route.page === 'login' && <a href="#/setup">자료 설정으로 이동</a>}
      {route.page === 'setup' && <RulebookSetup api={setupApi} playerId={playerId} sessionApi={sessionApi} onSessionCreated={sessionId => { window.location.hash = `#/sessions/${sessionId}/character` }} />}
      {route.page === 'adventures' && <SavedAdventurePanel playApi={playApi} setupApi={setupApi} playerId={playerId} />}
      {route.page === 'adventure' && <><h2>모험 진행 중</h2><AdventureStream adventureId={route.adventureId} api={adventureApi} /><RoleDiceRoller adventureId={route.adventureId} api={playApi} /><RuleEvidence adventureId={route.adventureId} api={guidanceApi} /><CombatMapView adventureId={route.adventureId} api={playApi} /></>}
      {route.page === 'character' && <CharacterSheetView sheetId={route.sheetId} api={playApi} />}
      {route.page === 'session' && <AdventureSessionPanel api={sessionApi} sessionId={route.sessionId} />}
      {route.page === 'character-blueprint' && <CharacterBlueprintReviewPage sessionId={route.sessionId} setupApi={setupApi} sessionApi={sessionApi} />}
      {route.page === 'package-blueprint' && <PackageBlueprintReviewPage packageId={route.packageId} setupApi={setupApi} sessionApi={sessionApi} onSessionCreated={sessionId => { window.location.hash = `#/sessions/${sessionId}/character` }} />}
      {route.page === 'character-create' && <CharacterCreationPage sessionId={route.sessionId} setupApi={setupApi} sessionApi={sessionApi} />}
    </main>
  </>
}

function createNavigatingSetupApi(token: string) {
  const api = new HttpSetupApi(() => token)

  const getScenarioPackage = api.getScenarioPackage?.bind(api)
  if (getScenarioPackage) {
    api.getScenarioPackage = async packageId => {
      const scenarioPackage = await getScenarioPackage(packageId)
      window.location.hash = `#/scenario-packages/${scenarioPackage.packageId}/character-blueprint`
      return scenarioPackage
    }
  }

  const compileScenarioBundle = api.compileScenarioBundle?.bind(api)
  if (compileScenarioBundle) {
    api.compileScenarioBundle = async (bundleId, ownerId) => {
      const scenarioPackage = await compileScenarioBundle(bundleId, ownerId)
      window.location.hash = `#/scenario-packages/${scenarioPackage.packageId}/character-blueprint`
      return scenarioPackage
    }
  }

  return api
}
