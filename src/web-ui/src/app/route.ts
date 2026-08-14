export type Route =
  | { page: 'setup' }
  | { page: 'bundle'; bundleId: string }
  | { page: 'adventures' }
  | { page: 'adventure'; adventureId: string }
  | { page: 'party'; sessionId: string }
  | { page: 'story-plan'; sessionId: string }
  | { page: 'character'; sheetId: string }
  | { page: 'session'; sessionId: string }
  | { page: 'character-create'; sessionId: string }
  | { page: 'character-blueprint'; sessionId: string }
  | { page: 'package-blueprint'; packageId: string }
  | { page: 'profile' }
  | { page: 'backoffice' }
  | { page: 'login' }

export function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '') || '/login'
  const segments = path.split('/').filter(Boolean)
  if (segments[0] === 'setup') return { page: 'setup' }
  if (segments[0] === 'bundles' && segments[1]) return { page: 'bundle', bundleId: segments[1] }
  if (segments[0] === 'adventures' && segments[1]) return { page: 'adventure', adventureId: segments[1] }
  if (segments[0] === 'adventures') return { page: 'adventures' }
  if (segments[0] === 'sessions' && segments[1] && segments[2] === 'party') return { page: 'party', sessionId: segments[1] }
  if (segments[0] === 'sessions' && segments[1] && segments[2] === 'story-plan') return { page: 'story-plan', sessionId: segments[1] }
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
