# RAG-024: 모험 계획의 의존성 기반 회귀 보정

- 상태: `ready-for-agent`
- 의존성: RAG-019 (completed), RAG-023
- Product Spec: UC-REPAIR-001, BR-REPAIR-001~003, AC-REPAIR-001~002
- Architecture Spec: Sections 3.5~3.8, 4.2~4.7, 6.1, 7.2, 11.1~11.2
- GitHub Issue: [#199](https://github.com/omegafrog/dnd-master/issues/199)
- Parent Issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

## 구현 목적

Projection Blocker가 적 누락처럼 한 필드를 지적해도 그 원인에 의존하는 전투 요구, 결과, Source Fact와 전술 준비 의도를 함께 일관되게 수정할 수 있게 한다. 기존 RAG-019의 전체 후보·정본 registry·bounded retry 계약을 유지하면서 허용 변경 범위를 deterministic dependency closure로 확장한다.

## 사용자·엔티티 흐름

```text
전체 plan candidate 검증 실패
→ structured Projection Blocker
→ ProjectionDependencyPolicy가 RepairScope 계산
→ 기존 전체 후보 + blocker + authoritative registries로 repair
→ scope diff guard
→ schema/source/graph/combat 전체 재검증
→ READY 또는 정직한 BLOCKED
```

## 구현 범위

- blocker path에서 같은 원인의 dependent path를 계산하는 명시적 dependency map
- participants ↔ requirement/objective/outcomes/source facts/tactical intent 연결
- 기존 전체 candidate를 입력·출력으로 유지하는 repair request/response
- RepairScope 안의 필드만 변경하도록 하는 전체 후보 diff guard
- schema, citation/map/source registry, graph, combat, tactical intent 전체 재검증
- 무관한 단계와 이미 검증된 Source Fact 보존
- 기존 RAG-019 attempt budget, regeneration, no-progress와 failure classification 유지
- blocker/repair scope/changed path/final validation의 안전한 관측

## 수용 기준

- 적 누락 blocker는 같은 단계의 requirement, outcomes, source facts와 tactical intent를 필요한 범위에서 함께 수정할 수 있다.
- 계산된 dependency closure 밖의 변경은 repair 후보 전체를 거부한다.
- repair 후에는 직접 blocker만이 아니라 전체 계획 validator가 모두 실행된다.
- 무관한 단계의 검증된 사실과 근거는 byte-stable 또는 semantic-equivalent하게 보존된다.
- 진전 없는 반복이나 근거 부족은 READY로 위장하지 않고 기존 분류로 BLOCKED 종료된다.

## 테스트 계약

- 정책 단위 테스트: dependency closure, scope containment, unrelated-stage preservation, no-progress
- application 테스트: combat blocker repair 성공, residual blocker, source-insufficient, regeneration path
- gateway 계약 테스트: full candidate와 authoritative registries round-trip
- UI ↔ Entity E2E: 계획 생성 요청 → combat blocker → dependent repair → READY 또는 사용자에게 안전한 BLOCKED 결과
- RAG-019 회귀 테스트: attempt budget과 미등록 citation/map/source 차단 유지

## 제외 범위

- validator 완화 또는 누락 근거 생성
- 부분 patch 저장
- GM runtime response repair

## 완료 증거

- blocker별 RepairScope fixture
- repair 전후 전체 후보 diff
- 전체 validator 실행과 최종 상태 기록
