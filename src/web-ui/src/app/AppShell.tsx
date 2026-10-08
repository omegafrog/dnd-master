import { useCallback, useEffect, useMemo, useState } from 'react'
import { useAuth } from '../features/auth/AuthContext'
import { LoginForm } from '../features/auth/LoginForm'
import { HttpAdventureApi } from '../features/adventure/AdventureApi'
import { AdventureWorkspace } from '../features/adventure/AdventureWorkspace'
import { SessionRuntime, SpatialTurnRuntime } from '../features/adventure/SessionRuntime'
import { SessionRuntimeRoute } from '../features/adventure/SessionRuntimeRoute'
import { HttpAdventurePlayApi } from '../features/saved-adventures/AdventurePlayApi'
import { SavedAdventurePanel } from '../features/saved-adventures/SavedAdventurePanel'
import { HttpSetupApi } from '../features/rulebooks/SetupApi'
import { RulebookSetup } from '../features/rulebooks/RulebookSetup'
import { BackofficePage } from '../features/backoffice/BackofficePage'
import { BundleDetailPage } from '../features/rulebooks/BundleDetailPage'
import { CharacterSheetView } from '../features/character/CharacterSheetView'
import { CharacterCreationPage } from '../features/character/CharacterCreationPage'
import { PackageBlueprintReviewPage } from '../features/character/PackageBlueprintReviewPage'
import { CombatMapView } from '../features/combat-map/CombatMapView'
import { AdventureSessionApi } from '../features/adventure-session/AdventureSessionApi'
import { AdventureSessionPanel } from '../features/adventure-session/AdventureSessionPanel'
import { AiEndpointSettings } from '../features/profile/AiEndpointSettings'
import { CodexConnectionApi, type CodexConnectionState } from '../features/profile/CodexConnectionApi'
import { CombatScreen } from '../features/combat/CombatScreen'
import { HttpCombatApi, type CombatFinalSummary, type CombatSnapshot } from '../features/combat/CombatApi'
import { parseRoute, type Route } from './route'
import { BookOpenText, FileUp, Home, Map, Settings, Swords, UserRound } from 'lucide-react'

export function AppShell() {
  const auth = useAuth()
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash))
  const [selectedBundleId, setSelectedBundleId] = useState(() => window.localStorage.getItem('dnd-selected-bundle-id') ?? '')
  const [mapRefreshToken, setMapRefreshToken] = useState(0)
  const [connectionGate, setConnectionGate] = useState<{ token: string; status: CodexConnectionState } | null>(null)
  const sessionToken = auth.session?.accessToken
  const connectionReady = connectionGate?.token === auth.session?.accessToken && connectionGate?.status.status === 'CONNECTED'
  const onConnectionChange = useCallback((status: CodexConnectionState) => {
    if (sessionToken) setConnectionGate(current => mergeConnectionStatus(current, sessionToken, status))
  }, [sessionToken])
  const sessionApi = useMemo(() => new AdventureSessionApi(auth.session?.accessToken ?? ''), [auth.session?.accessToken])
  const setupApi = useMemo(() => new HttpSetupApi(() => auth.session?.accessToken ?? ''), [auth.session?.accessToken])
  const rawSetupApi = useMemo(() => new HttpSetupApi(() => auth.session?.accessToken ?? ''), [auth.session?.accessToken])

  const onHashChange = useCallback(() => setRoute(parseRoute(window.location.hash)), [])
  const refreshCombatMap = useCallback(() => setMapRefreshToken(current => current + 1), [])
  useEffect(() => {
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [onHashChange])
  useEffect(() => {
    if (auth.session && route.page === 'login') window.location.hash = '#/adventures'
  }, [auth.session, route.page])
  useEffect(() => {
    const session = auth.session
    if (!session) { setConnectionGate(null); return }
    let active = true
    const api = new CodexConnectionApi(session)
    const refresh = () => void api.status()
      .then(status => { if (active) setConnectionGate(current => mergeConnectionStatus(current, session.accessToken, status)) })
      .catch(() => { if (active) setConnectionGate(current => mergeConnectionStatus(current, session.accessToken, { status: 'UNAVAILABLE', cliAvailable: false })) })
    refresh()
    const timer = connectionReady ? undefined : window.setInterval(refresh, 1500)
    return () => { active = false; if (timer !== undefined) window.clearInterval(timer) }
  }, [auth.session, connectionReady])
  useEffect(() => {
    if (!auth.session || route.page !== 'adventures') return
    const currentPath = window.location.hash.split('?')[0]
    if (currentPath === '#/setup') window.location.hash = '#/adventures'
  }, [auth.session, route.page])
  useEffect(() => {
    const refreshSelectedBundle = () => setSelectedBundleId(window.localStorage.getItem('dnd-selected-bundle-id') ?? '')
    window.addEventListener('dnd-selected-bundle-change', refreshSelectedBundle)
    return () => window.removeEventListener('dnd-selected-bundle-change', refreshSelectedBundle)
  }, [])
  useEffect(() => {
    if (!auth.session || !connectionReady) return
    const sessionId = route.page === 'character-blueprint' || route.page === 'character-create' || route.page === 'session' || route.page === 'party' || route.page === 'session-runtime'
      ? route.sessionId
      : null
    const adventureId = route.page === 'adventure' || route.page === 'adventure-workspace' ? route.adventureId : null
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
  }, [auth.session, connectionReady, rawSetupApi, route, sessionApi])
  useEffect(() => {
    if (!auth.session || !connectionReady || route.page !== 'adventures' || selectedBundleId || !rawSetupApi.listScenarioBundles) return
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
  }, [auth.session, connectionReady, rawSetupApi, route.page, selectedBundleId])

  const token = auth.session?.accessToken ?? ''
  const playerId = auth.session?.playerId ?? ''
  const getToken = useCallback(() => token, [token])
  const adventureApi = useMemo(() => new HttpAdventureApi(getToken), [getToken])
  const combatApi = useMemo(() => new HttpCombatApi(getToken), [getToken])
  const [combatSnapshot, setCombatSnapshot] = useState<CombatSnapshot | null>(null)
  const [combatFinalSummary, setCombatFinalSummary] = useState<CombatFinalSummary | null>(null)
  const [adventureVersion, setAdventureVersion] = useState<number | null>(null)
  const refreshCombat = useCallback(() => {
    if (!auth.session || !connectionReady || (route.page !== 'adventure' && route.page !== 'adventure-workspace')) return
    const summaryRequest = combatApi.readFinalSummary
      ? combatApi.readFinalSummary(route.adventureId)
      : Promise.resolve(null)
    void Promise.all([combatApi.readSnapshot(route.adventureId), summaryRequest]).then(([snapshot, summary]) => {
      setCombatSnapshot(snapshot)
      setCombatFinalSummary(snapshot ? null : summary)
    }).catch(() => undefined)
  }, [auth.session, combatApi, connectionReady, route])
  useEffect(() => {
    if (!auth.session || !connectionReady || route.page !== 'adventure') return
    let active = true
    void adventureApi.readConversation(route.adventureId).then(response => {
      if (active) setAdventureVersion(response.version)
    }).catch(() => { if (active) setAdventureVersion(null) })
    return () => { active = false }
  }, [auth.session, adventureApi, connectionReady, route])
  useEffect(() => {
    if (!auth.session || !connectionReady || (route.page !== 'adventure' && route.page !== 'adventure-workspace')) {
      setCombatSnapshot(null)
      setCombatFinalSummary(null)
      return
    }
    let active = true
    setCombatFinalSummary(null)
    const finalSummary = combatApi.readFinalSummary
      ? combatApi.readFinalSummary(route.adventureId)
      : Promise.resolve(null)
    void Promise.all([combatApi.readSnapshot(route.adventureId), finalSummary])
      .then(([snapshot, summary]) => {
        if (!active) return
        setCombatSnapshot(snapshot)
        setCombatFinalSummary(snapshot ? null : summary)
      })
      .catch(() => {
        if (active) { setCombatSnapshot(null); setCombatFinalSummary(null) }
      })
    return () => { active = false }
  }, [auth.session, combatApi, connectionReady, route])
  useEffect(() => {
    if (!auth.session || !connectionReady || route.page !== 'adventure' || !combatApi.subscribeEvents || combatSnapshot?.eventCursor == null) return
    const adventureId = route.adventureId
    let active = true
    const cursor = combatSnapshot?.eventCursor ?? -1
    const close = combatApi.subscribeEvents(adventureId, cursor, event => {
      if (event.type === 'COMBAT_ENDED') {
        const summaryRequest = combatApi.readFinalSummary
          ? combatApi.readFinalSummary(adventureId)
          : Promise.resolve(null)
        void summaryRequest.then(summary => {
          if (!active) return
          setCombatSnapshot(null)
          setCombatFinalSummary(summary)
        }).catch(() => {
          if (active) { setCombatSnapshot(null); setCombatFinalSummary(null) }
        })
        return
      }
      void combatApi.readSnapshot(adventureId).then(snapshot => { if (active) setCombatSnapshot(snapshot) }).catch(() => undefined)
    }, () => undefined)
    return () => { active = false; close() }
  }, [auth.session, combatApi, connectionReady, route, combatSnapshot?.eventCursor])

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

  const activeConnection = connectionGate?.token === auth.session.accessToken ? connectionGate.status : null
  if (!connectionReady) {
    return <div className="app-shell auth-shell codex-connection-gate">
      <header className="app-header auth-header"><Brand /></header>
      <main id="main" className="auth-main">
        <div className="auth-intro">
          <p className="eyebrow">CODEX ACCOUNT CONNECTION</p>
          <h1>Codex 계정을 연결해 주세요</h1>
          <p>Codex 계정 연결을 완료하면 모험과 AI 기능을 사용할 수 있습니다. 연결이 취소되거나 실패해도 다시 시도할 수 있습니다.</p>
        </div>
        <div className="auth-panel">
          {!activeConnection && <p role="status" aria-live="polite">Codex 연결 상태를 확인하고 있습니다.</p>}
          <AiEndpointSettings session={auth.session} connectionOnly connectionHint={activeConnection}
            onConnectionChange={onConnectionChange} />
        </div>
      </main>
    </div>
  }

  const playApi = new HttpAdventurePlayApi(getToken)
  if (route.page === 'session-runtime') {
    return <div className="game-shell">
      <main id="main" className="game-shell-main app-page-session-runtime">
        <SessionRuntimeRoute sessionId={route.sessionId} sessionApi={sessionApi} adventureApi={adventureApi} playApi={playApi} setupApi={setupApi} combatApi={combatApi} />
      </main>
    </div>
  }

  const creatorRoute = route.page === 'character-blueprint' || route.page === 'character-create'
  const initials = auth.session.playerName.slice(0, 1).toUpperCase()
  const currentAdventureId = route.page === 'adventure-workspace' || route.page === 'adventure' ? route.adventureId : null
  const currentSessionId = 'sessionId' in route ? route.sessionId : null
  const materialsHref = currentAdventureId ? `#/adventures/${encodeURIComponent(currentAdventureId)}?tab=materials` : '#/setup?mode=create'
  const scenarioHref = currentAdventureId ? `#/adventures/${encodeURIComponent(currentAdventureId)}?tab=review` : '#/adventures'
  const characterHref = currentSessionId ? `#/sessions/${encodeURIComponent(currentSessionId)}/party` : currentAdventureId ? `#/adventures/${encodeURIComponent(currentAdventureId)}?tab=characters` : '#/adventures'
  const sessionHref = currentSessionId ? `#/sessions/${encodeURIComponent(currentSessionId)}?mode=play` : currentAdventureId ? `#/adventures/${encodeURIComponent(currentAdventureId)}?tab=sessions` : '#/adventures'
  return <div className="app-shell">
    <header className="app-header"><a href="#main">본문으로 건너뛰기</a><Brand /><nav aria-label="주요 메뉴">
      <a aria-label="모험" className={route.page === 'adventures' ? 'active' : undefined} aria-current={route.page === 'adventures' ? 'page' : undefined} href="#/adventures"><Home size={17} aria-hidden="true" /><span>모험</span></a>
      <a aria-label="자료" className={route.page === 'setup' || route.page === 'bundle' || (route.page === 'adventure-workspace' && route.tab === 'materials') ? 'active' : undefined} href={materialsHref}><FileUp size={17} aria-hidden="true" /><span>자료</span></a>
      <a aria-label="시나리오" className={route.page === 'adventure-workspace' && route.tab === 'review' ? 'active' : undefined} href={scenarioHref}><Map size={17} aria-hidden="true" /><span>시나리오</span></a>
      <a aria-label="캐릭터" className={route.page === 'character' || route.page === 'character-create' || route.page === 'character-blueprint' || (route.page === 'adventure-workspace' && route.tab === 'characters') ? 'active' : undefined} href={characterHref}><UserRound size={17} aria-hidden="true" /><span>캐릭터</span></a>
      <a aria-label="세션" className={route.page === 'session' || route.page === 'party' || (route.page === 'adventure-workspace' && route.tab === 'sessions') ? 'active' : undefined} href={sessionHref}><Swords size={17} aria-hidden="true" /><span>세션</span></a>
      <details className="account-menu"><summary role="button" aria-label="계정 메뉴"><span className="account-avatar" aria-hidden="true">{initials}</span><span className="account-name">{auth.session.playerName}</span></summary><div className="account-menu-panel"><a href="#/profile"><Settings size={15} aria-hidden="true" />내 설정</a><button type="button" onClick={() => void auth.logout()}><BookOpenText size={15} aria-hidden="true" />로그아웃</button></div></details>
    </nav></header>
    <main id="main" className={creatorRoute ? 'creator-main' : `app-content app-page-${route.page}`}>
      <div className="app-notices"><p role="status" aria-live="polite">{auth.message}</p></div>
      {route.page === 'login' && <section className="welcome-card"><p className="eyebrow">ADVENTURE AWAITS</p><h2>모험 준비가 완료되었습니다</h2><a className="text-link" href="#/setup">자료 설정으로 이동</a></section>}
      {route.page === 'profile' && <ProfilePage session={auth.session} onConnectionChange={onConnectionChange} />}
      {route.page === 'backoffice' && <BackofficePage session={auth.session} />}
      {route.page === 'setup' && <RulebookSetup api={setupApi} playerId={playerId} sessionApi={sessionApi} asMain={false} resumeBundleId={route.resumeBundleId} />}
      {route.page === 'bundle' && <BundleDetailPage bundleId={route.bundleId} api={setupApi} playerId={playerId} sessionApi={sessionApi} />}
      {route.page === 'adventures' && <SavedAdventurePanel playApi={playApi} setupApi={setupApi} sessionApi={sessionApi} playerId={playerId} forceList onResumed={adventureId => { window.location.hash = `#/adventures/${adventureId}?tab=materials` }} />}
      {route.page === 'adventure-workspace' && <AdventureWorkspace adventureId={route.adventureId} activeTab={route.tab} playApi={playApi} setupApi={setupApi} sessionApi={sessionApi} playerId={playerId} />}
      {route.page === 'adventure' && (combatSnapshot && combatSnapshot.status !== 'ENDED' ? <><SpatialTurnRuntime adventureId={route.adventureId} playApi={playApi} combatSnapshot={combatSnapshot} /><CombatScreen snapshot={combatSnapshot} api={combatApi} onCommandCommitted={refreshCombat} map={<CombatMapView adventureId={route.adventureId} api={playApi} refreshToken={mapRefreshToken} compact />} /></> : <>
        {combatFinalSummary && <section className="combat-final-summary" aria-labelledby="combat-final-summary-title">
          <p className="eyebrow">COMBAT COMPLETE</p>
          <h2 id="combat-final-summary-title">전투 종료 요약</h2>
          <p>{combatFinalSummary.summary}</p>
          <p>상세 전투 기록은 종료 후 제공되지 않습니다.</p>
        </section>}
        <SessionRuntime adventureId={route.adventureId} adventureApi={adventureApi} expectedVersion={adventureVersion} playApi={playApi} combatSnapshot={combatSnapshot} mapRefreshToken={mapRefreshToken} onTurnCommitted={() => { refreshCombatMap(); refreshCombat() }} />
      </>)}
      {route.page === 'character' && <CharacterSheetView sheetId={route.sheetId} api={playApi} />}
      {(route.page === 'session' || route.page === 'party') && <AdventureSessionPanel api={sessionApi} ownerPlayerId={playerId} sessionId={route.sessionId} playApi={playApi} />}
      {route.page === 'character-blueprint' && <CharacterCreationPage sessionId={route.sessionId} ownerPlayerId={playerId} setupApi={setupApi} sessionApi={sessionApi} />}
      {route.page === 'package-blueprint' && <PackageBlueprintReviewPage packageId={route.packageId} setupApi={setupApi} sessionApi={sessionApi} onSessionCreated={sessionId => { window.location.hash = `#/sessions/${sessionId}/character-blueprint` }} />}
      {route.page === 'character-create' && <CharacterCreationPage sessionId={route.sessionId} ownerPlayerId={playerId} setupApi={setupApi} sessionApi={sessionApi} />}
    </main>
  </div>
}

function Brand() {
  return <a className="app-brand" href="#/adventures" aria-label="D&D Master 홈"><img src="/assets/characters/compass.png" alt="" aria-hidden="true" /><span><strong>D&amp;D Master</strong><small>Solo Adventure Studio</small></span></a>
}

function mergeConnectionStatus(
  current: { token: string; status: CodexConnectionState } | null,
  token: string,
  status: CodexConnectionState,
) {
  const sameSession = current !== null && current.token === token
  const unlinked = status.status === 'DISCONNECTED' || status.status === 'AUTH_REQUIRED'
  const hasUnresolvedSwitch = sameSession && current.status.status === 'AUTHENTICATING'
    && Boolean(current.status.operationId)
  const hasTerminalSwitch = sameSession
    && (current.status.status === 'CANCELLED' || current.status.status === 'FAILED')
  if (unlinked && (hasUnresolvedSwitch || hasTerminalSwitch)) return current
  return { token, status }
}

function ProfilePage({ session, onConnectionChange }: {
  session: NonNullable<ReturnType<typeof useAuth>['session']>
  onConnectionChange: (status: CodexConnectionState) => void
}) {
  return <section aria-labelledby="profile-title" className="profile-page"><div className="page-heading"><div><p className="eyebrow">PLAYER SETTINGS</p><h1 id="profile-title">내 설정</h1></div></div><section className="setup-panel"><h2>내 정보</h2><dl><dt>이름</dt><dd>{session.playerName}</dd><dt>플레이어 ID</dt><dd>{session.playerId}</dd><dt>인증 만료</dt><dd>{new Date(session.expiresAt).toLocaleString('ko-KR')}</dd></dl></section><AiEndpointSettings session={session} onConnectionChange={onConnectionChange} /></section>
}
