import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { IdentitySession } from '../auth/IdentityApi'
import { CodexConnectionApi, type CodexConnectionOperation, type CodexConnectionState } from './CodexConnectionApi'

type Endpoint = {
  id: string
  name: string
  provider: 'OLLAMA' | 'OPENAI_COMPATIBLE' | 'CODEX_CLI'
  baseUrl: string
  model: string
  secretEnvironmentVariable?: string | null
  active: boolean
}

const headers = (session: IdentitySession) => ({ Authorization: `Bearer ${session.accessToken}` })
const providerLabel = (provider: Endpoint['provider']) => provider === 'CODEX_CLI' ? 'Codex OAuth' : provider === 'OPENAI_COMPATIBLE' ? 'OpenAI 호환' : 'Ollama'

export function AiEndpointSettings({ session, connectionOnly = false, connectionHint, onConnectionChange }: {
  session: IdentitySession
  connectionOnly?: boolean
  connectionHint?: CodexConnectionState | null
  onConnectionChange?: (connection: CodexConnectionState) => void
}) {
  const [endpoints, setEndpoints] = useState<Endpoint[]>([])
  const [message, setMessage] = useState('')
  const [provider, setProvider] = useState<Endpoint['provider']>('OLLAMA')
  const [connection, setConnection] = useState<CodexConnectionState | null>(null)
  const [operation, setOperation] = useState<CodexConnectionOperation | null>(null)
  const operationIdHint = useRef(connectionHint?.operationId ?? null)
  const connectionApi = useMemo(() => new CodexConnectionApi(session), [session])

  const refreshConnection = useCallback(async () => {
    try {
      const status = await connectionApi.status()
      const operationId = status.operationId ?? operationIdHint.current
      if (operationId) {
        const activeOperation = await connectionApi.operation(operationId)
        setOperation(activeOperation)
        if (!activeOperation.pending) {
          const terminalStatus = { status: activeOperation.status, cliAvailable: true,
            operationId: activeOperation.operationId, message: activeOperation.message }
          setConnection(terminalStatus)
          onConnectionChange?.(terminalStatus)
          if (activeOperation.message) setMessage(activeOperation.message)
        } else {
          const pendingStatus = { ...status, status: 'AUTHENTICATING' as const, operationId }
          setConnection(pendingStatus)
          onConnectionChange?.(pendingStatus)
        }
      } else {
        setConnection(status)
        onConnectionChange?.(status)
      }
      setMessage('')
    } catch (error) {
      setConnection({ status: 'UNAVAILABLE', cliAvailable: false })
      setMessage(error instanceof Error ? error.message : 'Codex 연결 상태를 확인하지 못했습니다.')
    }
  }, [connectionApi, onConnectionChange])

  useEffect(() => { void refreshConnection() }, [refreshConnection])

  useEffect(() => {
    const operationId = connectionHint?.operationId
    if (!operationId || operationId === operationIdHint.current) return
    operationIdHint.current = operationId
    setConnection(connectionHint)
    void connectionApi.operation(operationId).then(next => {
      setOperation(next)
      if (!next.pending) {
        const terminalStatus = { status: next.status, cliAvailable: true,
          operationId: next.operationId, message: next.message }
        setConnection(terminalStatus)
        onConnectionChange?.(terminalStatus)
        if (next.message) setMessage(next.message)
      } else {
        const pendingStatus = { ...connectionHint, status: 'AUTHENTICATING' as const, operationId }
        setConnection(pendingStatus)
        onConnectionChange?.(pendingStatus)
      }
    }).catch(() => setMessage('Codex 연결 상태를 새로고침할 수 없습니다.'))
  }, [connectionApi, connectionHint, onConnectionChange])

  useEffect(() => {
    if (!operation?.pending) return
    const timer = window.setInterval(() => {
      void connectionApi.operation(operation.operationId).then(next => {
        setOperation(next)
        if (!next.pending) {
          const terminalStatus = { status: next.status, cliAvailable: true, operationId: next.operationId, message: next.message }
          setConnection(terminalStatus)
          onConnectionChange?.(terminalStatus)
          if (next.message) setMessage(next.message)
        }
      }).catch(() => setMessage('Codex 연결 상태를 새로고침할 수 없습니다.'))
    }, 1500)
    return () => window.clearInterval(timer)
  }, [connectionApi, onConnectionChange, operation])

  async function connect(type: 'CONNECT' | 'REAUTHENTICATE' | 'SWITCH_ACCOUNT') {
    const popup = window.open('about:blank', '_blank')
    if (popup) popup.opener = null
    try {
      const started = await connectionApi.start(type)
      setOperation(started)
      operationIdHint.current = started.pending ? started.operationId : null
      const startedStatus = { status: started.status, cliAvailable: true, operationId: started.operationId, message: started.message }
      setConnection(startedStatus)
      onConnectionChange?.(startedStatus)
      if (started.authUrl) {
        if (popup) popup.location.href = started.authUrl
        else window.open(started.authUrl, '_blank', 'noopener,noreferrer')
        setMessage('브라우저에서 Codex 계정 승인을 완료해 주세요.')
      } else {
        popup?.close()
        setMessage(started.message ?? 'Codex 계정 연결 상태를 확인했습니다.')
      }
      if (!started.pending) await refreshConnection()
    } catch (error) {
      popup?.close()
      setMessage(error instanceof Error ? error.message : 'Codex 계정 연결을 시작하지 못했습니다.')
    }
  }

  async function disconnectCodex() {
    try {
      const result = await connectionApi.disconnect()
      setOperation(null)
      setConnection(result)
      onConnectionChange?.(result)
      setMessage('이 설치의 Codex 연결을 해제했습니다. Codex CLI 로그인은 유지됩니다.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Codex 연결을 해제하지 못했습니다.')
    }
  }

  const refresh = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/profile/agent-endpoints', { headers: headers(session) })
      if (!response.ok) throw new Error(await response.text() || `HTTP ${response.status}`)
      setEndpoints(await response.json() as Endpoint[])
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'AI 엔드포인트 설정을 불러오지 못했습니다.')
    }
  }, [session])

  useEffect(() => { if (!connectionOnly) void refresh() }, [connectionOnly, refresh])

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

  return <section className="setup-panel" aria-labelledby="ai-endpoint-settings-title">
    {!connectionOnly && <h2 id="ai-endpoint-settings-title">AI 엔드포인트 설정</h2>}
    {!connectionOnly && <p>모험의 AI 게임 마스터가 사용할 연결 방식을 설정합니다.</p>}
    <p role="status" aria-live="polite">{message}</p>
    <section aria-labelledby="codex-connection-title" className="codex-connection-panel">
      <h3 id="codex-connection-title">Codex 계정 연결</h3>
      <p role="status" aria-live="polite">{connection ? connectionLabel(connection.status) : 'Codex 연결 상태를 확인하고 있습니다.'}</p>
      {connection?.status === 'CLI_UNAVAILABLE' && <><p>Codex CLI를 설치한 뒤 다시 시도해 주세요. 이 클라이언트는 Codex CLI를 자동 설치하지 않습니다.</p><button type="button" onClick={() => void refreshConnection()}>다시 확인</button></>}
      {connection?.status === 'AUTHENTICATING' && <p>브라우저에서 계정 승인을 완료하면 연결 상태가 갱신됩니다.</p>}
      {connection?.status === 'CONNECTED' ? <>
        <button type="button" onClick={() => void connect('SWITCH_ACCOUNT')}>다른 Codex 계정으로 전환</button>
        <button type="button" onClick={() => void disconnectCodex()}>이 설치의 연결 해제</button>
        <p>계정 전환은 이 컴퓨터에서 사용하는 Codex CLI 계정도 변경합니다.</p>
      </> : connection?.status === 'REAUTH_REQUIRED'
        ? <button type="button" onClick={() => void connect('REAUTHENTICATE')}>Codex 계정 다시 인증</button>
        : connection?.status !== 'CLI_UNAVAILABLE' && connection?.status !== 'AUTHENTICATING'
          ? <button type="button" onClick={() => void connect('CONNECT')}>Codex 계정 연결</button>
          : null}
      {connection?.status === 'AUTHENTICATING' && operation?.authUrl
        ? <button type="button" onClick={() => window.open(operation.authUrl!, '_blank', 'noopener,noreferrer')}>승인 페이지 다시 열기</button>
        : null}
      {connection?.message && <p>{connection.message}</p>}
    </section>
    {!connectionOnly && <form onSubmit={saveEndpoint}>
      <label>이름<input name="name" placeholder="예: 내 로컬 Ollama" required /></label>
      <label>연결 방식<select name="provider" value={provider} onChange={event => setProvider(event.currentTarget.value as Endpoint['provider'])}><option value="OLLAMA">로컬 AI (Ollama)</option><option value="OPENAI_COMPATIBLE">OpenAI 호환</option><option value="CODEX_CLI">Codex OAuth (로컬 CLI)</option></select></label>
      {provider === 'CODEX_CLI' ? <p>Codex 계정 연결은 위에서 관리합니다. 주소, 모델, API 키는 입력하지 않습니다.</p> : <>
        <label>주소<input name="baseUrl" type="url" placeholder="http://127.0.0.1:11434" required /></label>
        <label>모델<input name="model" placeholder="예: llama3.2" required /></label>
        {provider === 'OPENAI_COMPATIBLE' ? <label>API 키 환경변수명<input name="secretEnvironmentVariable" placeholder="예: OPENAI_API_KEY" required /></label> : null}
      </>}
      <button type="submit">AI 엔드포인트 저장</button>
    </form>}
    {!connectionOnly && (endpoints.length > 0 ? <ul aria-label="저장된 AI 엔드포인트">{endpoints.map(item => <li key={item.id}>{item.name} · {providerLabel(item.provider)}{item.provider !== 'CODEX_CLI' ? ` · ${item.model}` : ''} {item.active ? '· 활성' : ''} <button type="button" onClick={() => void health(item.id)}>연결 확인</button></li>)}</ul> : <p>저장된 AI 엔드포인트가 없습니다.</p>)}
  </section>
}

function connectionLabel(status: CodexConnectionState['status']) {
  switch (status) {
    case 'CLI_UNAVAILABLE': return 'Codex CLI를 사용할 수 없습니다.'
    case 'AUTH_REQUIRED': return 'Codex 계정을 연결해야 합니다.'
    case 'AUTHENTICATING': return 'Codex 계정 승인을 기다리고 있습니다.'
    case 'CONNECTED': return 'Codex 계정이 연결되었습니다.'
    case 'REAUTH_REQUIRED': return 'Codex 계정을 다시 인증해야 합니다.'
    case 'DISCONNECTED': return '이 설치는 Codex 계정에 연결되지 않았습니다.'
    case 'CANCELLED': return 'Codex 계정 승인이 취소되었습니다. 다시 시도할 수 있습니다.'
    case 'FAILED': return 'Codex 계정 연결에 실패했습니다. 다시 시도해 주세요.'
    case 'UNAVAILABLE': return '사용자 PC 연결을 사용할 수 없습니다. 다시 연결해 주세요.'
  }
}
