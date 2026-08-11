import { type FormEvent, useEffect, useState } from 'react'
import type { IdentitySession } from '../auth/IdentityApi'

type Catalog = { catalogRevisionId: string; edition: 'DND_5E_2014' | 'DND_5E_2024'; displayName: string; rulebookId?: string | null; revisionNumber: number; status: string }
type Endpoint = { id: string; name: string; provider: 'OLLAMA' | 'OPENAI_COMPATIBLE'; baseUrl: string; model: string; secretEnvironmentVariable?: string | null; active: boolean }
// Backoffice APIs are scoped by the configured ADMIN player-id allowlist until the shared role claim is introduced.
const headers = (session: IdentitySession) => ({ Authorization: `Bearer ${session.playerId}` })

export function BackofficePage({ session }: { session: IdentitySession }) {
  const [catalog, setCatalog] = useState<Catalog[]>([]); const [endpoints, setEndpoints] = useState<Endpoint[]>([]); const [message, setMessage] = useState('')
  async function refresh() {
    try {
      const [catalogResponse, endpointResponse] = await Promise.all([fetch('/api/v1/rulebook-catalog'), fetch('/api/v1/backoffice/agent-endpoints', { headers: headers(session) })])
      setCatalog(await catalogResponse.json()); if (endpointResponse.ok) setEndpoints(await endpointResponse.json())
    } catch { setMessage('백오피스 정보를 불러오지 못했습니다.') }
  }
  useEffect(() => { void refresh() }, [])
  async function upload(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); try { const result = await fetch(`/api/v1/backoffice/rulebook-catalog?edition=${form.get('edition')}`, { method: 'POST', headers: headers(session), body: form }); if (!result.ok) throw new Error(await result.text()); setMessage('룰북 파이프라인을 시작했습니다. 색인 완료 후 Publish 하세요.'); await refresh() } catch (error) { setMessage(error instanceof Error ? error.message : '업로드 실패') } }
  async function publish(id: string) { const response = await fetch(`/api/v1/backoffice/rulebook-catalog/${id}/publish`, { method: 'POST', headers: headers(session) }); setMessage(response.ok ? '카탈로그 revision을 공개했습니다.' : await response.text()); await refresh() }
  async function saveEndpoint(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); const id = crypto.randomUUID(); const body = { name: form.get('name'), provider: form.get('provider'), baseUrl: form.get('baseUrl'), model: form.get('model'), secretEnvironmentVariable: form.get('secretEnvironmentVariable') || null, active: true }; const response = await fetch(`/api/v1/backoffice/agent-endpoints/${id}`, { method: 'PUT', headers: { ...headers(session), 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); setMessage(response.ok ? '활성 agent endpoint를 저장했습니다.' : await response.text()); await refresh() }
  async function health(id: string) { const response = await fetch(`/api/v1/backoffice/agent-endpoints/${id}/health`, { method: 'POST', headers: headers(session) }); const result = await response.json(); setMessage(result.healthy ? `연결 정상 (HTTP ${result.statusCode})` : `연결 실패: ${result.detail}`) }
  return <section className="setup-page"><div className="page-heading"><div><p className="eyebrow">INTERNAL BACKOFFICE</p><h1>룰북 · Agent 관리</h1></div></div><p role="status">{message}</p>
    <section className="setup-panel"><h2>공유 룰북 카탈로그</h2><form onSubmit={upload}><select name="edition" defaultValue="DND_5E_2014"><option value="DND_5E_2014">D&D 5e (2014)</option><option value="DND_5E_2024">D&D 5.5e (2024)</option></select><input name="file" type="file" accept=".pdf" required /><button>PDF 업로드·색인</button></form><ul>{catalog.map(item => <li key={item.catalogRevisionId}>{item.displayName} r{item.revisionNumber} · {item.status} {item.status !== 'READY' && item.rulebookId && <button onClick={() => void publish(item.catalogRevisionId)}>Publish</button>}</li>)}</ul></section>
    <section className="setup-panel"><h2>Agent endpoint</h2><form onSubmit={saveEndpoint}><input name="name" placeholder="이름" required /><select name="provider"><option value="OLLAMA">Ollama</option><option value="OPENAI_COMPATIBLE">OpenAI 호환</option></select><input name="baseUrl" type="url" placeholder="http://127.0.0.1:11434" required /><input name="model" placeholder="model" required /><input name="secretEnvironmentVariable" placeholder="OPENAI_API_KEY (환경변수명만)" /><button>활성 endpoint 저장</button></form><ul>{endpoints.map(item => <li key={item.id}>{item.name} · {item.provider} · {item.model} {item.active ? '· 활성' : ''} <button onClick={() => void health(item.id)}>Health check</button></li>)}</ul></section>
  </section>
}
