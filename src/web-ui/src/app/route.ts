export type Route =
  | { page: 'setup' }
  | { page: 'bundle'; bundleId: string }
  | { page: 'adventures' }
  | { page: 'adventure'; adventureId: string }
  | { page: 'adventure-workspace'; adventureId: string; tab: 'materials' | 'review' | 'characters' | 'sessions' }
  | { page: 'party'; sessionId: string }
  | { page: 'character'; sheetId: string }
  | { page: 'session'; sessionId: string }
  | { page: 'session-runtime'; sessionId: string }
  | { page: 'character-create'; sessionId: string }
  | { page: 'character-blueprint'; sessionId: string }
  | { page: 'package-blueprint'; packageId: string }
  | { page: 'profile' }
  | { page: 'backoffice' }
  | { page: 'login' }

export function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '') || '/login'
  const [pathname, query = ''] = path.split('?')
  const segments = pathname.split('/').filter(Boolean)
  const params = new URLSearchParams(query)
  if (segments[0] === 'setup') return { page: 'setup' }
  if (segments[0] === 'bundles' && segments[1]) return { page: 'bundle', bundleId: segments[1] }
  if (segments[0] === 'adventures' && segments[1]) {
    const tab = params.get('tab')
    if (tab === 'materials' || tab === 'review' || tab === 'characters' || tab === 'sessions') return { page: 'adventure-workspace', adventureId: segments[1], tab }
    return { page: 'adventure', adventureId: segments[1] }
  }
  if (segments[0] === 'adventures') return { page: 'adventures' }
  if (segments[0] === 'sessions' && segments[1] && params.get('mode') === 'play') return { page: 'session-runtime', sessionId: segments[1] }
  if (segments[0] === 'sessions' && segments[1] && segments[2] === 'party') return { page: 'party', sessionId: segments[1] }
  if (segments[0] === 'character' && segments[1]) return { page: 'character', sheetId: segments[1] }
  if (segments[0] === 'scenario-packages' && segments[1] && segments[2] === 'character-blueprint') {
    return { page: 'package-blueprint', packageId: segments[1] }
  }
  if (segments[0] === 'sessions' && segments[1] && segments[2] === 'character-blueprint') return { page: 'character-blueprint', sessionId: segments[1] }
  if (segments[0] === 'sessions' && segments[1] && segments[2] === 'character') return { page: 'character-create', sessionId: segments[1] }
  if (segments[0] === 'sessions' && segments[1]) return { page: 'session', sessionId: segments[1] }
  if (segments[0] === 'profile') return { page: 'profile' }
  if (segments[0] === 'backoffice') return { page: 'backoffice' }
  return { page: 'login' }
}
