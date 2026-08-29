# GMQ-005 Role-Scoped Tuning Evaluation And Activation

- Issue: #235
- Parent Issue: #211
- Status: `planned`
- Dependencies: GMQ-004

## 구현 목적

승인된 proposal만 role 단위 SFT/preference-tuning으로 실행하고 base model + optimized prompt와 같은 조건에서 비교한다. hard safety·holdout·비용·지연을 만족할 때만 활성화·rollback한다.

## 구현 범위

- trainer/provider `RoleTuningPort` 격리와 training artifact/hyperparameter 기록.
- base/tuned 동일 Eval·holdout·operational comparison.
- hard metric 비악화, soft 개선, holdout 유지, 비용/지연 gate.
- GMQ-003 registry와 role-scoped model activation/rollback 연결.
- failure taxonomy before/after delta와 lineage report.

## Acceptance Criteria

- eligible proposal 외 trainer는 호출되지 않는다.
- tuned model은 base + optimized prompt와 동일 조건 비교다.
- creative improvement가 hard regression을 상쇄하지 않는다.
- activation/rollback은 선택 role에만 영향을 준다.
- 비용/지연 기준 초과 model은 active가 될 수 없다.

## Test Contract

- Policy unit: eligibility, hard/soft/operational gate, role activation/rollback.
- Integration: trainer adapter fixture, artifact lineage persistence, failed training recovery.
- UI ~ entity E2E: approved proposal → training/evaluation fixture → holdout gate → role activation → GM lineage → rollback.
