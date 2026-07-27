import type { IdentityApi } from '../features/auth/IdentityApi'
import { AuthProvider } from '../features/auth/AuthContext'
import { AppShell } from './AppShell'

export function App({ identityApi }: { identityApi: IdentityApi }) {
  return (
    <AuthProvider api={identityApi}>
      <AppShell />
    </AuthProvider>
  )
}
