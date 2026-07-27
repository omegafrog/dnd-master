# 024-4 - Web UI 캐릭터 생성 흐름

- Status: approved
- Issue: [#66](https://github.com/omegafrog/dnd-master/issues/66)
- Dependencies: 024-1, 024-2, 024-3
- Parent: [024](024-character-creation-flow.md)

## Outcome

설정 화면에 결합된 기존 폼을 분리해 Blueprint 검토부터 세션 생성, 캐릭터 생성, 파티 추가, 모험 시작까지 연결한다.

## Acceptance criteria

- 세션 생성 전 캐릭터 생성 API를 호출하지 않는다.
- 별도 character creation route/page를 제공한다.
- Blueprint field/options/constraints/evidence를 렌더링한다.
- 충돌 선택과 `MANUAL_INPUT_REQUIRED` 필드 입력을 제공한다.
- 이름·종족·직업·배경·시작 능력치·레벨을 전송한다.
- 세션 응답의 실제 session ID를 캐릭터 생성 요청에 전달한다.
- 생성 후 party add와 control mode 선택을 제공한다.
- 시작 오류, revision mismatch, validation failure를 표시한다.
- 전체 준비 흐름을 기존 설정 화면과 분리한다.

## Test contract

- Component/API: Blueprint state, evidence, manual input, session ID propagation.
- Policy UI tests: disabled states and required fields.
- `ui ~ entity` Playwright E2E: preparation → publish → session → character → party → start.
- Existing `ScenarioSetup`, `AdventureSessionPanel`, `SavedAdventureFlow` regression coverage.

## Implementation scope

`src/web-ui/src/features/scenarios`, `src/web-ui/src/features/character`, `src/web-ui/src/features/adventure-session`, routing, API clients, component/E2E tests.
