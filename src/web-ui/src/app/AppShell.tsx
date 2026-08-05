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
import { BundleDetailPage } from '../features/rulebooks/BundleDetailPage'
import { CharacterSheetView } from '../features/character/CharacterSheetView'
import { CharacterCreationPage } from '../features/character/CharacterCreationPage'
import { PackageBlueprintReviewPage } from '../features/character/PackageBlueprintReviewPage'
import { RoleDiceRoller } from '../features/dice/RoleDiceRoller'
import { CombatMapView } from '../features/combat-map/CombatMapView'
import { AdventureSessionApi } from '../features/adventure-session/AdventureSessionApi'
import { AdventureSessionPanel } from '../features/adventure-session/AdventureSessionPanel'
import { AdventureStoryPlanPage } from '../features/adventure-session/AdventureStoryPlanPage'
import { parseRoute, type Route } from './route'

export function AppShell() {
  const auth = useAuth()
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash))
  const [selectedBundleId, setSelectedBundleId] = useState(() => window.localStorage.getItem('dnd-selected-bundle-id') ?? '')
  const sessionApi = useMemo(() => new AdventureSessionApi(auth.session?.accessToken ?? ''), [auth.session?.accessToken])
  const setupApi = useMemo(() => new HttpSetupApi(() => auth.session?.accessToken ?? ''), [auth.session?.accessToken])
  const rawSetupApi = useMemo(() => new HttpSetupApi(() => auth.session?.accessToken ?? ''), [auth.session?.accessToken])

  const onHashChange = useCallback(() => setRoute(parseRoute(window.location.hash)), [])
  useEffect(() => {
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [onHashChange])
  useEffect(() => {
    const refreshSelectedBundle = () => setSelectedBundleId(window.localStorage.getItem('dnd-selected-bundle-id') ?? '')
    window.addEventListener('dnd-selected-bundle-change', refreshSelectedBundle)
    return () => window.removeEventListener('dnd-selected-bundle-change', refreshSelectedBundle)
  }, [])
  useEffect(() => {
    if (!auth.session) return
    const sessionId = route.page === 'character-blueprint' || route.page === 'character-create' || route.page === 'session' || route.page === 'party' || route.page === 'story-plan'
      ? route.sessionId
      : null
    const adventureId = route.page === 'adventure' ? route.adventureId : null
    if ((!sessionId && !adventureId) || !rawSetupApi.getScenarioPackage) return
    let active = true
    const packageId = sessionId
      ? sessionApi.read(sessionId).then(session => session.scenarioPackageId ?? null)
      : rawSetupApi.getRuntimeBinding?.(adventureId!, auth.session?.playerId ?? '').then(binding => binding.scenarioPackageId)
    void packageId
      ?.then(id => id ? rawSetupApi.getScenarioPackage!(id) : null)
      .then(scenarioPackage => {
        if (!active || !scenarioPackage) return
        window.localStorage.setItem('dnd-selected-bundle-id', scenarioPackage.bundleId)
        window.dispatchEvent(new Event('dnd-selected-bundle-change'))
        setSelectedBundleId(scenarioPackage.bundleId)
      })
      .catch(() => undefined)
    return () => { active = false }
  }, [auth.session, rawSetupApi, route, sessionApi])
  useEffect(() => {
    if (!auth.session || route.page !== 'adventures' || selectedBundleId || !rawSetupApi.listScenarioBundles) return
    let active = true
    void rawSetupApi.listScenarioBundles()
      .then(bundles => {
        const bundleId = bundles[0]?.bundleId
        if (!active || !bundleId) return
        window.localStorage.setItem('dnd-selected-bundle-id', bundleId)
        window.dispatchEvent(new Event('dnd-selected-bundle-change'))
        setSelectedBundleId(bundleId)
      })
      .catch(() => undefined)
    return () => { active = false }
  }, [auth.session, rawSetupApi, route.page, selectedBundleId])

  if (!auth.session) {
    return <div className="app-shell auth-shell">
      <header className="app-header auth-header"><Brand /></header>
      <main id="main" className="auth-main">
        <div className="auth-intro">
          <p className="eyebrow">SOLO TABLETOP ADVENTURES</p>
          <h2>당신만의 모험을 시작하세요</h2>
          <p>룰북과 이야기 자료를 준비하면 AI 게임 마스터가 캐릭터 생성부터 모험의 결말까지 함께합니다.</p>
        </div>
        <div className="auth-panel"><p role="status" aria-live="polite">{auth.message}</p><LoginForm /></div>
      </main>
    </div>
  }

  const token = auth.session.accessToken
  const playerId = auth.session.playerId
  const getToken = () => token
  const getPlayerId = () => playerId
  const adventureApi = new HttpAdventureApi(getToken, getPlayerId)
  const playApi = new HttpAdventurePlayApi(getToken)
  const guidanceApi = new HttpRuleGuidanceApi(getToken, getPlayerId)
  const creatorRoute = route.page === 'character-blueprint' || route.page === 'character-create'
  const initials = auth.session.playerName.slice(0, 1).toUpperCase()
  return <div className="app-shell">
    <header className="app-header"><a href="#main">본문으로 건너뛰기</a><Brand /><nav aria-label="주요 메뉴"><a className={route.page === 'setup' ? 'active' : undefined} aria-current={route.page === 'setup' ? 'page' : undefined} href="#/setup">자료 설정</a><a className={route.page === 'adventures' || route.page === 'adventure' ? 'active' : undefined} aria-current={route.page === 'adventures' || route.page === 'adventure' ? 'page' : undefined} href="#/adventures">모험 목록</a>{selectedBundleId && <a className="selected-bundle-toolbar" href={`#/bundles/${selectedBundleId}`} title={`${selectedBundleId} 번들 화면`}>현재 번들 <span>{shortId(selectedBundleId)}</span></a>}<details className="account-menu"><summary role="button" aria-label="계정 메뉴"><span className="account-avatar" aria-hidden="true">{initials}</span><span className="account-name">{auth.session.playerName}</span></summary><div className="account-menu-panel"><a href="#/profile">내 정보</a><button type="button" onClick={() => void auth.logout()}>로그아웃</button></div></details></nav></header>
    <main id="main" className={creatorRoute ? 'creator-main' : `app-content app-page-${route.page}`}>
      <div className="app-notices"><p role="status" aria-live="polite">{auth.message}</p><p className="welcome-message">{auth.session.playerName}님 환영합니다!</p></div>
      {route.page === 'login' && <section className="welcome-card"><p className="eyebrow">ADVENTURE AWAITS</p><h2>모험 준비가 완료되었습니다</h2><a className="text-link" href="#/setup">자료 설정으로 이동</a></section>}
      {route.page === 'profile' && <ProfilePage session={auth.session} />}
      {route.page === 'setup' && <RulebookSetup api={setupApi} playerId={playerId} sessionApi={sessionApi} asMain={false} />}
      {route.page === 'bundle' && <BundleDetailPage bundleId={route.bundleId} api={setupApi} playerId={playerId} sessionApi={sessionApi} />}
      {route.page === 'adventures' && <SavedAdventurePanel playApi={playApi} setupApi={setupApi} playerId={playerId} />}
      {route.page === 'adventure' && <><div className="page-heading"><div><p className="eyebrow">ACTIVE ADVENTURE</p><h1>모험 진행 중</h1></div><span className="page-id">{shortId(route.adventureId)}</span></div><div className="adventure-workspace"><AdventureStream adventureId={route.adventureId} api={adventureApi} /><div className="adventure-tools"><RoleDiceRoller adventureId={route.adventureId} api={playApi} /><RuleEvidence adventureId={route.adventureId} api={guidanceApi} /><CombatMapView adventureId={route.adventureId} api={playApi} /></div></div></>}
      {route.page === 'character' && <CharacterSheetView sheetId={route.sheetId} api={playApi} />}
      {(route.page === 'session' || route.page === 'party') && <AdventureSessionPanel api={sessionApi} ownerPlayerId={playerId} sessionId={route.sessionId} />}
      {route.page === 'story-plan' && <AdventureStoryPlanPage api={sessionApi} sessionId={route.sessionId} />}
      {route.page === 'character-blueprint' && <CharacterCreationPage sessionId={route.sessionId} ownerPlayerId={playerId} setupApi={setupApi} sessionApi={sessionApi} />}
      {route.page === 'package-blueprint' && <PackageBlueprintReviewPage packageId={route.packageId} setupApi={setupApi} sessionApi={sessionApi} onSessionCreated={sessionId => { window.location.hash = `#/sessions/${sessionId}/character-blueprint` }} />}
      {route.page === 'character-create' && <CharacterCreationPage sessionId={route.sessionId} ownerPlayerId={playerId} setupApi={setupApi} sessionApi={sessionApi} />}
    </main>
  </div>
}

function Brand() {
  return <a className="app-brand" href="#/setup" aria-label="D&D Master 홈"><img src="/assets/characters/compass.png" alt="" aria-hidden="true" /><span><strong>D&amp;D Master</strong><small>Solo Adventure Studio</small></span></a>
}

function shortId(value: string) {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value
}

function ProfilePage({ session }: { session: NonNullable<ReturnType<typeof useAuth>['session']> }) {
  return <section aria-labelledby="profile-title" className="profile-page"><p className="eyebrow">PLAYER PROFILE</p><h1 id="profile-title">내 정보</h1><dl><dt>이름</dt><dd>{session.playerName}</dd><dt>플레이어 ID</dt><dd>{session.playerId}</dd><dt>인증 만료</dt><dd>{new Date(session.expiresAt).toLocaleString('ko-KR')}</dd></dl></section>
}
