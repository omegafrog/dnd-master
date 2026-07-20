import { LoginForm } from '../features/auth/LoginForm'
import { useAuth } from '../features/auth/AuthContext'

export function AppShell() {
  const auth = useAuth()
  return (
    <>
      <header>
        <a href="#main">본문으로 건너뛰기</a>
        <h1>D&amp;D Master</h1>
        {auth.session && (
          <nav aria-label="주요 메뉴">
            <a href="/adventures">모험</a>
            <a href="/rulebooks">룰북</a>
            <a href="/characters">캐릭터</a>
            <button type="button" onClick={() => void auth.logout()}>로그아웃</button>
          </nav>
        )}
      </header>
      <main id="main">
        <p role="status" aria-live="polite">{auth.message}</p>
        {auth.session ? <h2>{auth.session.playerName}님의 모험</h2> : <LoginForm />}
      </main>
    </>
  )
}
