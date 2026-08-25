# RAG-013: Page Retry Diagnostics Recovery

실패 페이지·영역만 최대 2회 추가 시도하고 atomic checkpoint, 상태 조회, 명시적 재시도, 오버레이와 중단 복구를 제공한다.

Plan: `docs/plans/rag-013-page-retry-diagnostics-recovery.md`

GitHub: https://github.com/omegafrog/dnd-master/issues/186

Depends on: RAG-012

## Scope

- PageAttempt and failure-specific PageRetryPolicy
- atomic page checkpoints and publish-last manifest
- status/retry_pages process operations
- diagnostic overlays, idempotency and interruption recovery

## Acceptance

- retries never exceed two additional attempts
- unaffected validated page artifacts remain unchanged
- duplicate requests and interrupted runs resume safely
- status and diagnostics expose page attempts and findings
- policy unit, repository contract and process-CLI-to-entity e2e tests pass
