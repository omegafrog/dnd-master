# TT-002: Bounded Candidate Repair And Worker Retry Separation

- Issue: [#239](https://github.com/omegafrog/dnd-master/issues/239)
- Status: `completed`
- Dependencies: TT-001 `completed`
- Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

## 구현 목적

복구 가능한 candidate만 최대 1회 repair한다. 동일 입력의 결정론적 불완전성을 worker delivery retry에서 제거해 같은 실패를 반복하는 loop를 막는다.

## Implementation Scope

- `CandidateRepairPolicy`와 typed repair port
- candidate별 repair attempt와 raw/final artifact reference 보존
- compilation delivery failure classifier 분리
- `ScenarioCompilationWorker`의 3회 candidate recovery 제거
- 현재 dirty `ScenarioCompilationWorkerTest`의 `PARTIAL → WAITING_RETRY` 기대를 승인된 Spec에 맞게 재정의

## Acceptance Criteria

- `REPAIRABLE`과 승인된 `MAYBE_REPAIRABLE`만 repair
- candidate당 추가 repair 최대 1회
- deterministic incomplete outcome은 `WAITING_RETRY`로 전환하지 않음
- lease/DB/transient provider delivery failure만 bounded worker retry

## Test Contract

- Policy unit: validation code → recoverability, repair count invariant
- Application test: repair 전후 artifact와 최종 outcome
- UI/API ~ entity E2E: incomplete compilation 종료 후 attempt 불변, transient delivery failure만 retry

## Implementation Boundaries

- TT-001 candidate schema 재사용
- unrelated dirty worker/E2E 변경 보존

## Completion Evidence

- Added typed `CandidateRepairPolicy` and `CandidateRepairPort` with one-attempt invariant.
- Worker now repairs eligible candidates once through `retryCandidate`; deterministic incomplete outcomes fail immediately.
- Delivery retry remains reserved for transient/infrastructure failures.
- Verification: targeted worker and candidate-repair policy tests passed.
