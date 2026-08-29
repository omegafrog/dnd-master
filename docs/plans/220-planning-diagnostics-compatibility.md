# Plan 220: Planning Diagnostics And Compatibility

- Issue: #220
- Status: `in-progress`
- Dependencies: #219

## 구현 목적

개발자가 persisted Planner/Resolved/Writer artifact와 lifecycle을 안전하게 조회하게 한다. legacy persistence, replay, duplicate request, presentation retry 호환을 회귀 검증한다.

## 구현 범위

- 인증된 development-only read-only internal diagnostics API.
- Planner/Resolved/Writer artifact의 안전한 projection; hidden data의 접근 정책 명시.
- old combined RuntimePlan JSON, presented turn replay, resolved-uncommitted retry regression.
- API/DB compatibility observation과 운영 error mapping.

## 제외 범위

- 일반 사용자용 Planner/Writer API.
- mutation, retry trigger, tool capability를 diagnostics API에 노출.

## Acceptance Criteria

- diagnostics endpoint는 authenticated, read-only이며 runtime 상태를 바꾸지 않는다.
- uncommitted/presented turn lifecycle과 artifact를 개발 환경에서 조회할 수 있다.
- legacy JSON rows, duplicate public request, post-commit replay가 호환된다.
- hidden future data는 Writer artifact/projection에 포함되지 않는다.

## Test Contract

- Policy unit: diagnostics authorization, no mutation, redaction/projection rules.
- Integration: persisted legacy/resolved/presented row read, replay, failure error mapping.
- UI ~ entity E2E: 기존 UI message와 turn-history/read response가 legacy·new presented turn 모두 동일 response shape으로 표시된다.

## 구현 순서

1. diagnostic read model/API authorization.
2. persistence/replay compatibility tests.
3. UI-to-entity regression journey.
