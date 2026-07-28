# 026-1 - 4판/5판 기본 캐릭터 태그 추출

- Status: completed
- Issue: [#80](https://github.com/omegafrog/dnd-master/issues/80)
- Parent: [#79](https://github.com/omegafrog/dnd-master/issues/79)
- Dependencies: none

## Outcome

4판·5판 기본 템플릿의 캐릭터 생성 필드와 명시적 입력 타입을 룰북 근거와 함께 컴파일한다.

## Acceptance criteria

- 기본 태그는 name, race, class, background, starting_ability_scores, level이다.
- 4판/5판 템플릿은 `FREE_TEXT`, `SINGLE_SELECT`, `MULTI_SELECT`를 명시한다.
- 룰북에서 추출한 값·문서 유형·locator·원문 근거를 보존한다.
- 추출 실패 필드는 자유 입력과 진단을 제공한다.
- 문서에 없는 값은 생성하지 않는다.
- UI는 `options.length`가 아니라 명시적 입력 타입을 사용한다.

## Test contract

- Template/extraction/input-mode policy unit tests.
- Blueprint API response contract tests.
- `ui ~ entity` E2E: 룰북 컴파일 후 필수 태그·타입·근거 표시.

## Implementation scope

`adventure-service`의 template/extraction/compiler/API contract, blueprint persistence payload, `web-ui` DTO와 기본 입력 렌더링, 관련 unit/API/Playwright 테스트.
