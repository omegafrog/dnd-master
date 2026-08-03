import { type FormEvent, useState } from 'react'
import { useAuth } from './AuthContext'

export function LoginForm() {
  const auth = useAuth()
  const [submitting, setSubmitting] = useState(false)
  const [isRegister, setIsRegister] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setSubmitting(true)
    if (isRegister) {
      await auth.register({ email: String(form.get('email')), password: String(form.get('password')) })
    } else {
      await auth.login({ email: String(form.get('email')), password: String(form.get('password')) })
    }
    setSubmitting(false)
  }

  async function loginWithDemoAccount() {
    setSubmitting(true)
    await auth.login({ email: 'demo-player@example.com', password: 'secret-password' })
    setSubmitting(false)
  }

  return (
    <form aria-labelledby="login-heading" onSubmit={submit}>
      <h2 id="login-heading">{isRegister ? '회원가입' : '로그인'}</h2>
      <label>이메일<input name="email" type="email" autoComplete="username" required /></label>
      <label>비밀번호<input name="password" type="password" autoComplete={isRegister ? 'new-password' : 'current-password'} required /></label>
      <button type="submit" disabled={submitting}>
        {submitting ? (isRegister ? '가입 중…' : '로그인 중…') : (isRegister ? '회원가입' : '로그인')}
      </button>
      {!isRegister && (
        <button type="button" disabled={submitting} onClick={() => void loginWithDemoAccount()}>
          테스트 계정으로 로그인
        </button>
      )}
      <button type="button" onClick={() => setIsRegister(!isRegister)}>
        {isRegister ? '로그인으로 돌아가기' : '회원가입'}
      </button>
    </form>
  )
}
