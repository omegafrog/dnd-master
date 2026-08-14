import { useEffect, useRef, useState } from 'react'
import { Button } from '../../components/ui/button'
import type { AgentEndpointPreflightView, ScenarioCompilationView, SetupApi } from './SetupApi'

const labels: Record<ScenarioCompilationView['status'], string> = {
  REQUESTED: '준비 대기 중', RUNNING: '자료를 분석하는 중', WAITING_RETRY: '다시 시도 대기 중',
  PUBLISHED: '게임 준비 완료', FAILED: '게임 준비 실패',
}

export function PreparationModal({ bundleId, revision, api, ownerId, onClose, onCharacter, onAdventure }: {
  bundleId: string
  revision: number
  api: SetupApi
  ownerId: string
  onClose: () => void
  onCharacter: (packageId: string) => void
  onAdventure: (packageId: string) => void
}) {
  const dialogRef = useRef<HTMLElement>(null)
  const [preflight, setPreflight] = useState<AgentEndpointPreflightView | null>(null)
  const [compilation, setCompilation] = useState<ScenarioCompilationView | null>(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const fingerprint = `scenario-bundle:${bundleId}:revision:${revision}`
  const storageKey = `dnd-preparation:${bundleId}:${revision}`

  useEffect(() => {
    dialogRef.current?.focus()
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose() }
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose])

  useEffect(() => {
    let active = true
    if (api.preflightAgentEndpoint) void api.preflightAgentEndpoint().then(value => { if (active) setPreflight(value) }).catch(error => { if (active) setMessage(error instanceof Error ? error.message : 'AI 연결 상태를 확인하지 못했습니다.') })
    const savedId = window.localStorage.getItem(storageKey)
    if (savedId && api.getScenarioCompilation) void api.getScenarioCompilation(savedId).then(value => { if (active) setCompilation(value) }).catch(() => window.localStorage.removeItem(storageKey))
    return () => { active = false }
  }, [api, storageKey])

  useEffect(() => {
    if (!compilation || !api.getScenarioCompilation || compilation.status === 'PUBLISHED' || compilation.status === 'FAILED') return
    const timer = window.setTimeout(() => void api.getScenarioCompilation!(compilation.compilationId).then(setCompilation), 1000)
    return () => window.clearTimeout(timer)
  }, [api, compilation])

  async function start(retry = false) {
    if (!api.startScenarioCompilation) return
    setBusy(true); setMessage('')
    try {
      const status = preflight ?? (api.preflightAgentEndpoint ? await api.preflightAgentEndpoint() : { configured: true, connected: true, state: 'CONNECTED' as const })
      setPreflight(status)
      if (!status.connected) { setMessage(status.detail ?? '게임 준비 전에 AI 엔드포인트를 연결하세요.'); return }
      const started = await api.startScenarioCompilation(bundleId, ownerId, retry ? `${fingerprint}:retry:${Date.now()}` : fingerprint)
      window.localStorage.setItem(storageKey, started.compilationId)
      setCompilation(started)
    } catch (error) { setMessage(error instanceof Error ? error.message : '게임 준비를 시작하지 못했습니다.') }
    finally { setBusy(false) }
  }

  return <section ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="preparation-modal-title" className="preparation-modal">
    <div className="preparation-modal-card">
      <div className="bundle-card-heading"><div><p className="eyebrow">GAME PREPARATION</p><h2 id="preparation-modal-title">게임 준비</h2></div><Button type="button" variant="outline" onClick={onClose}>닫기</Button></div>
      {!preflight ? <p role="status">AI 연결 상태를 확인하는 중…</p> : preflight.connected ? <p role="status">AI 엔드포인트 연결됨</p> : <div role="alert"><p>{preflight.state === 'LOGIN_REQUIRED' ? 'Codex OAuth 로그인이 필요합니다.' : preflight.state === 'NOT_CONFIGURED' ? 'AI 엔드포인트를 설정해야 합니다.' : preflight.state === 'EXPIRED' ? 'AI 엔드포인트 연결이 만료되었습니다.' : 'AI 엔드포인트 연결에 실패했습니다.'}</p><a href="#/profile">AI 엔드포인트 설정 열기</a></div>}
      {compilation && <div role="status"><p>{labels[compilation.status]}</p>{compilation.failureReason && <p role="alert">게임 준비에 실패했습니다. 다시 준비를 눌러 재시도해 주세요.</p>}{compilation.status === 'FAILED' && <Button type="button" onClick={() => void start(true)} disabled={busy}>다시 준비</Button>}{compilation.status === 'PUBLISHED' && <div><Button type="button" onClick={() => onCharacter(compilation.packageId!)}>캐릭터 생성 시작</Button><Button type="button" variant="outline" onClick={() => onAdventure(compilation.packageId!)}>이 자료로 모험 만들기</Button></div>}</div>}
      {message && <p role="alert">{message}</p>}
      {(!compilation || compilation.status === 'REQUESTED' || compilation.status === 'RUNNING' || compilation.status === 'WAITING_RETRY') && <Button type="button" onClick={() => void start()} disabled={busy || !preflight?.connected}>{busy ? '준비 요청 중…' : '게임 준비 시작'}</Button>}
    </div>
  </section>
}
