import { type FormEvent, useCallback, useEffect, useState } from 'react'
import type { IdentitySession } from '../auth/IdentityApi'

type Endpoint = {
  id: string
  name: string
  provider: 'OLLAMA' | 'OPENAI_COMPATIBLE' | 'CODEX_CLI'
  baseUrl: string
  model: string
  secretEnvironmentVariable?: string | null
  active: boolean
}

const headers = (session: IdentitySession) => ({ Authorization: `Bearer ${session.playerId}` })
const providerLabel = (provider: Endpoint['provider']) => provider === 'CODEX_CLI' ? 'Codex OAuth' : provider === 'OPENAI_COMPATIBLE' ? 'OpenAI 호환' : 'Ollama'

export function AiEndpointSettings({ session }: { session: IdentitySession }) {
  const [endpoints, setEndpoints] = useState<Endpoint[]>([])
  const [message, setMessage] = useState('')
  const [provider, setProvider] = useState<Endpoint['provider']>('OLLAMA')

  const refresh = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/profile/agent-endpoints', { headers: headers(session) })
      if (!response.ok) throw new Error(await response.text() || `HTTP ${response.status}`)
      setEndpoints(await response.json() as Endpoint[])
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'AI 엔드포인트 설정을 불러오지 못했습니다.')
    }
  }, [session])

  useEffect(() => { void refresh() }, [refresh])

  async function saveEndpoint(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const id = crypto.randomUUID()
    const body = {
      name: form.get('name'),
      provider: form.get('provider'),
      baseUrl: provider === 'CODEX_CLI' ? null : form.get('baseUrl'),
      model: provider === 'CODEX_CLI' ? null : form.get('model'),
      secretEnvironmentVariable: provider === 'OPENAI_COMPATIBLE' ? form.get('secretEnvironmentVariable') || null : null,
      active: true,
    }
    const response = await fetch(`/api/v1/profile/agent-endpoints/${id}`, {
      method: 'PUT',
      headers: { ...headers(session), 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    setMessage(response.ok ? 'AI 엔드포인트를 저장했습니다.' : await response.text())
    if (response.ok) event.currentTarget.reset()
    await refresh()
  }

  async function health(id: string) {
    const response = await fetch(`/api/v1/profile/agent-endpoints/${id}/health`, { method: 'POST', headers: headers(session) })
    const result = await response.json() as { healthy: boolean; statusCode?: number; detail?: string }
    setMessage(result.healthy ? `연결 정상${result.statusCode ? ` (HTTP ${result.statusCode})` : ''}` : `연결 실패: ${result.detail ?? '알 수 없는 오류'}`)
  }

  async function login(id: string) {
    const response = await fetch(`/api/v1/profile/agent-endpoints/${id}/login`, { method: 'POST', headers: headers(session) })
    if (!response.ok) { setMessage(await response.text()); return }
    const result = await response.json() as { authUrl: string }
    window.open(result.authUrl, '_blank', 'noopener,noreferrer')
    setMessage('새 창에서 Codex OAuth 로그인을 완료하세요.')
  }

  return <section className="setup-panel" aria-labelledby="ai-endpoint-settings-title">
    <h2 id="ai-endpoint-settings-title">AI 엔드포인트 설정</h2>
    <p>모험의 AI 게임 마스터가 사용할 연결 방식을 설정합니다.</p>
    <p role="status" aria-live="polite">{message}</p>
    <form onSubmit={saveEndpoint}>
      <label>이름<input name="name" placeholder="예: 내 로컬 Ollama" required /></label>
      <label>연결 방식<select name="provider" value={provider} onChange={event => setProvider(event.currentTarget.value as Endpoint['provider'])}><option value="OLLAMA">로컬 AI (Ollama)</option><option value="OPENAI_COMPATIBLE">OpenAI 호환</option><option value="CODEX_CLI">Codex OAuth (로컬 CLI)</option></select></label>
      {provider === 'CODEX_CLI' ? <p>이 컴퓨터에서 <code>codex login</code>을 먼저 완료하면 됩니다. 주소, 모델, API 키는 입력하지 않습니다.</p> : <>
        <label>주소<input name="baseUrl" type="url" placeholder="http://127.0.0.1:11434" required /></label>
        <label>모델<input name="model" placeholder="예: llama3.2" required /></label>
        {provider === 'OPENAI_COMPATIBLE' ? <label>API 키 환경변수명<input name="secretEnvironmentVariable" placeholder="예: OPENAI_API_KEY" required /></label> : null}
      </>}
      <button type="submit">AI 엔드포인트 저장</button>
    </form>
    {endpoints.length > 0 ? <ul aria-label="저장된 AI 엔드포인트">{endpoints.map(item => <li key={item.id}>{item.name} · {providerLabel(item.provider)}{item.provider !== 'CODEX_CLI' ? ` · ${item.model}` : ''} {item.active ? '· 활성' : ''} {item.provider === 'CODEX_CLI' ? <button type="button" onClick={() => void login(item.id)}>Codex OAuth 로그인</button> : null} <button type="button" onClick={() => void health(item.id)}>연결 확인</button></li>)}</ul> : <p>저장된 AI 엔드포인트가 없습니다.</p>}
  </section>
}
