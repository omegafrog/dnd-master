# GMQ-003 Prompt Approval And Rollback

- Issue: #233
- Parent Issue: #210
- Status: `ready-for-agent`
- Dependencies: GMQ-002

## 구현 목적

Eval 1위 후보를 자동 배포하지 않는다. reviewer의 representative sample 검토와 holdout 결과 뒤 role별 승인·활성화·rollback을 수행한다.

## 구현 범위

- `PENDING_REVIEW`, `APPROVED`, `ACTIVE`, `ROLLED_BACK` lifecycle.
- holdout/regression 결과와 reviewer decision/reason 저장.
- role별 compare-and-set activation, 이전 approved version rollback.
- runtime turn artifact에 effective prompt/model/version/run lineage 기록.
- operator approval/rollback entrypoint/audit.

## Acceptance Criteria

- hard gate와 holdout/review 미통과 candidate는 active가 될 수 없다.
- Writer activation은 다른 role을 바꾸지 않는다.
- rollback은 이전 approved version으로만 수행되고 audit trail을 남긴다.
- runtime artifact에서 실제 prompt/model/run을 확인한다.

## Test Contract

- Policy unit: transition legality, approval precondition, role rollback, stale activation conflict.
- Integration: activation persistence, concurrent reviewer conflict, lineage.
- UI ~ entity E2E: review → holdout → activation → GM turn → rollback → 이전 artifact 사용.
