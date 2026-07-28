# 027-1 - Agent 캐릭터 태그 추출 계약 및 Blueprint 표시

- Status: completed
- Issue: [#86](https://github.com/omegafrog/dnd-master/issues/86)
- Parent: [#85](https://github.com/omegafrog/dnd-master/issues/85)
- Dependencies: none

## Outcome

RULEBOOK/STORYBOOK excerpt를 Agent에 보내 동적 캐릭터 입력 태그와 근거를 반환하고, 캐릭터 생성 UI에 표시한다.

## Scope

- `CharacterInputTagExtractionPort`와 request/response DTO.
- Agent API/gateway, schema validation, timeout/retry/malformed output 처리.
- `key/path`, label, parent, required, input mode, options, suggestions, confidence, source evidence 반환.
- `ScenarioPackageCompilationService`의 기존 regex 경로 제거.
- `SetupApi.ts`, `CharacterCreationPage.tsx`의 동적 Blueprint 표시.

## Acceptance

- Agent 응답이 유효한 dynamic candidate로 검증된다.
- evidence 없는 후보는 저장하지 않거나 검토 필요 상태가 된다.
- resolution extraction 계약과 Blueprint extraction 계약이 분리된다.
- 문서 선택 후 UI에 동적 태그와 근거가 나타난다.

## Tests

- Agent schema/gateway/timeout tests.
- source excerpt → blueprint API contract tests.
- `ui ~ entity` Playwright: document selection → Agent extraction → tag/evidence/mode display.

## Implementation scope

`adventure-service` extraction port/gateway/orchestration, AI extraction endpoint/adapter, `web-ui` `SetupApi.ts`와 `CharacterCreationPage.tsx`, related unit/API/Playwright tests.
