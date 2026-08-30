# TT-001: Compilation Candidate Diagnostics And Outcomes

- Issue: [#238](https://github.com/omegafrog/dnd-master/issues/238)
- Status: `ready-for-agent`
- Dependencies: 없음
- Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

## 구현 목적

각 compilation candidate의 완전성, 검증 코드, 복구 가능성, required 여부를 정본으로 저장한다. Package 결과를 candidate 상태와 분리해 optional 결함은 경고 완료로, required 결함은 실패로 판정한다.

## Implementation Scope

- `CompilationCandidate`, `CandidateCompleteness`, `CandidateValidation`, `CandidateRecoverability`, `CompilationOutcome` 추가
- `ScenarioCompilation` aggregate와 candidate repository 연결
- additive `scenario_compilation_candidate` migration/repository
- `ScenarioCompilationReport`를 package outcome projection으로 변경
- legacy package report compatibility reader 유지

## Acceptance Criteria

- Candidate별 `COMPLETE/PARTIAL/INVALID`, stable validation code, required, recoverability 조회 가능
- required incomplete → `FAILED`
- optional-only incomplete → `COMPLETE_WITH_WARNINGS`
- all complete → `COMPLETE`
- legacy report/row 읽기 유지

## Test Contract

- Policy unit: required/optional × completeness outcome matrix
- Persistence integration: candidate JSON/refs/timestamps round-trip와 migration compatibility
- UI/API ~ entity E2E: compilation 요청 → candidate rows → package outcome/경고 조회

## Implementation Boundaries

- Recoverability를 validation message 문자열로 판정하지 않음
- Candidate repair와 worker delivery retry는 TT-002 범위
- 기존 dirty worker 코드 수정은 최소화
