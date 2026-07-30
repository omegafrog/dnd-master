# 028-2 - 캐릭터 컨텍스트 컴파일 오케스트레이션

- Status: completed
- Issue: [#91](https://github.com/omegafrog/dnd-master/issues/91)
- Parent: 028
- Dependencies: 028-1

## Outcome

Scenario compilation이 새 character-context 검색 계약을 사용한다. Resolution 검색과 캐릭터 검색의 입력·예산을 분리하고, 기존 전역 `.limit(3)` 문제를 제거한다.

## Scope

- `CharacterContextSearchPort`와 adventure-side gateway 연결.
- `ScenarioCompilationWorker`에서 character query intent 검색 후 AI extraction 호출.
- Resolution Unit 추출 경로와 character extraction 경로 분리 유지.
- Evidence metadata/score/type/version/locator 전달 및 grounding 검증.
- retry/failure 시 캐릭터 항목은 manual fallback, Resolution 동작 보존.

## Acceptance

- STORYBOOK 검색 결과가 많아도 RULEBOOK Evidence가 AI 요청에서 제거되지 않는다.
- character extraction은 similarity threshold와 token budget 결과만 받는다.
- AI 후보의 Evidence/quote 불일치는 폐기된다.
- 기존 Resolution Unit compile 결과와 계약은 회귀하지 않는다.

## Tests

- worker orchestration, independent budgets, no-starvation, grounding unit tests.
- `ui ~ entity` E2E: compile 요청 → character Evidence/추출 결과가 Scenario Package API에 반영.

## Implementation scope

`adventure-service` compilation worker, search port/gateway, AI request mapping, related tests and API contracts.
