# RAG-013 Page Retry Diagnostics Recovery

- 상태: `completed`
- 의존성: RAG-012
- Product Spec: UC-06; BR-17, BR-18, BR-21
- Architecture Spec: Sections 5.5, 5.10, 6, 7, 9
- GitHub Issue: [#186](https://github.com/omegafrog/dnd-master/issues/186)

## 구현 목적

레이아웃 검증 실패 시 문서 전체를 반복 처리하지 않고 실패 원인과 영역에 맞춰 해당 페이지만 최대 2회 추가 시도한다. 페이지별 atomic checkpoint, 상태 조회, 명시적 재시도, 오버레이와 후보 진단을 제공해 중단 후에도 재현 가능하게 복구하고 Java process caller가 안전하게 상태를 추적할 수 있게 한다.

## 사용자·엔티티 흐름

```text
PageValidationFailed
→ PageRetryPolicy
→ targeted PageAttempt checkpoint
→ revalidate
→ VALIDATED or NEEDS_REVIEW
→ status/retry_pages process response + diagnostics
```

## 범위

- `PageAttempt`, `PageRetryPolicy`, failure-to-strategy mapping
- 최초 시도 이후 최대 2회의 추가 page/region attempts
- validated page 불변 보존과 explicit page selection
- atomic page checkpoint와 publish-last version manifest
- version-scoped idempotency/single-writer coordination
- `get_status` 상세 상태와 `retry_pages` process operation
- render + block/region/column/order overlay
- 후보 전략·점수·finding·attempt history diagnostics
- process interruption/timeout 후 checkpoint resume

## 수용 기준

- retry는 finding이 가리키는 페이지·영역에만 적용된다.
- 세 번째 추가 layout attempt는 aggregate/policy가 거부한다.
- retry하지 않은 validated page artifact/hash는 변경되지 않는다.
- 동일 idempotency key는 새 version을 만들지 않는다.
- process interruption 후 마지막 valid checkpoint에서 재개하고 READY manifest를 잘못 남기지 않는다.
- `status`와 `retry_pages` 응답이 page state, attempts, findings와 artifact refs를 반환한다.
- overlay만으로 원본 렌더와 block/region/order/failure bbox를 대조할 수 있다.

## 테스트 계약

- 정책 단위 테스트: retry mapping/budget, validated-page immutability, idempotency
- repository 계약 테스트: atomic replace, publish-last, corrupt temp recovery
- CLI ~ entity e2e: 실패 → 2회 targeted retry → VALIDATED 또는 `NEEDS_REVIEW`
- 중단 복구 e2e: process kill fixture → status → resume without duplicate work
- 동시성 테스트: duplicate request와 concurrent retry single-writer behavior
- 회귀 테스트: READY-only chunk publication과 기존 artifacts 유지

## 구현 범위

허용: retry/domain policy, application service, artifact repository/exporter, process status/retry operations, overlays, metrics/logging과 테스트.

금지: manual coordinate editor, network service, Java adapter implementation, 무제한 retry, future document formats.

## 완료 증거

- attempt history와 overlay artifacts
- interruption/idempotency/concurrency 테스트
- 전체 Product/Architecture acceptance matrix 결과
