# ADR-017: Candidate 완전성, 작업 결과, 실패 재시도 가능성을 분리

## Status

Accepted

## Context

Scenario compilation과 GM Turn 처리에서 개별 후보의 불완전성, 전체 작업 결과, 외부 처리 실패가 하나의 상태나 예외로 평탄화됐다. `PARTIAL` 같은 결정론적 검증 결과도 worker retry 대상이 되고, GM provider·schema·citation·safety·version conflict가 동일 외부 오류로 변환되면서 진단과 복구 정책이 결합됐다.

## Decision

- Compilation Candidate는 `COMPLETE`, `PARTIAL`, `INVALID` 완전성과 별도 recoverability를 가진다.
- Scenario compilation 전체 결과는 candidate requiredness를 적용해 `COMPLETE`, `COMPLETE_WITH_WARNINGS`, `FAILED`로 판정한다.
- Candidate repair는 recoverability code에 따라 candidate당 최대 1회 수행한다.
- 동일 입력의 결정론적 불완전성은 delivery retry 사유가 아니다.
- GM 실패는 stage, code, retryability, root-cause class, correlation ID를 가진 내부 artifact로 보존한다.
- transient provider failure만 최대 1회 자동 재시도한다. validation, citation, narration, safety, version conflict는 각 정책으로 종료한다.
- 외부 player error contract는 내부 failure artifact와 분리한다.

## Consequences

- 후보 품질, package 성공 여부, worker delivery 상태를 독립적으로 관측할 수 있다.
- retry 수치가 실제 transient recovery를 나타낸다.
- 기존 package/report와 runtime failure 저장소에 additive schema와 compatibility reader가 필요하다.
- validation message 문자열에 의존하는 recoverability 판정을 제거해야 한다.
- failure taxonomy와 retry policy는 Scenario Preparation과 Adventure Runtime이 각각 소유한다.
