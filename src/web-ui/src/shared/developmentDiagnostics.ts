const viteEnvironment = (import.meta as ImportMeta & {
  readonly env: { readonly DEV: boolean; readonly VITE_ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED?: string }
}).env
const diagnosticsEnabled = viteEnvironment.DEV
  && viteEnvironment.VITE_ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED === 'true'

/** Logs request metadata only. Never logs headers, request bodies, or response bodies. */
export async function diagnosticFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  if (!diagnosticsEnabled) return fetch(input, init)

  const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url, window.location.href)
  const method = (init?.method ?? (typeof input === 'object' && !(input instanceof URL) ? input.method : undefined) ?? 'GET').toUpperCase()
  const path = url.pathname
  const startedAt = performance.now()
  console.info(`dev_http method=${method} path=${path} outcome=started`)
  const pendingTimer = window.setInterval(() => {
    console.warn(`dev_http method=${method} path=${path} outcome=pending elapsedMs=${Math.round(performance.now() - startedAt)}`)
  }, 15_000)
  try {
    const response = await fetch(input, init)
    console.info(`dev_http method=${method} path=${path} outcome=response status=${response.status} elapsedMs=${Math.round(performance.now() - startedAt)}`)
    return response
  } catch (failure) {
    const failureClass = failure instanceof Error ? failure.constructor.name : 'UnknownError'
    console.warn(`dev_http method=${method} path=${path} outcome=network_error failureClass=${failureClass} elapsedMs=${Math.round(performance.now() - startedAt)}`)
    throw failure
  } finally {
    window.clearInterval(pendingTimer)
  }
}
