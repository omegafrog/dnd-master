import { type FormEvent, useState } from 'react'
import { useAuth } from './AuthContext'

export function LoginForm() {
  const auth = useAuth()
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setSubmitting(true)
    await auth.login({ email: String(form.get('email')), password: String(form.get('password')) })
    setSubmitting(false)
  }

  return (
    <form aria-labelledby="login-heading" onSubmit={submit}>
      <h2 id="login-heading">로그인</h2>
      <label>이메일<input name="email" type="email" autoComplete="username" required /></label>
      <label>비밀번호<input name="password" type="password" autoComplete="current-password" required /></label>
      <button type="submit" disabled={submitting}>{submitting ? '로그인 중…' : '로그인'}</button>
    </form>
  )
}
