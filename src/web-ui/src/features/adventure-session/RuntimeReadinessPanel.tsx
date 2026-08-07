import type { RuntimeBindingView } from '../rulebooks/SetupApi'

const labels: Record<RuntimeBindingView['readiness']['status'], string> = {
  INDEXED_READY: '색인 완료 · 실행 가능',
  INDEXING_PENDING: '색인 처리 중',
  BLOCKED: '실행 차단',
  SUPPORTED_DEGRADED: '제한된 실행 가능',
}

export function RuntimeReadinessPanel({ binding, onRetry }: { binding: RuntimeBindingView; onRetry?: () => void }) {
  const readiness = binding.readiness
  const reasons = [...readiness.blockers, ...readiness.warnings]
  return <section aria-labelledby="runtime-readiness-title" data-readiness={readiness.status}>
    <h2 id="runtime-readiness-title">런타임 준비 상태</h2>
    <p role={readiness.ready ? 'status' : 'alert'}>{labels[readiness.status]}</p>
    <p>바인딩 v{readiness.bindingVersion}</p>
    {reasons.length > 0 && <ul>{reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>}
    {readiness.retryable && onRetry && <button type="button" onClick={onRetry}>준비 상태 다시 확인</button>}
  </section>
}
