# 026-2 - 스토리북 기반 캐릭터 옵션·우선순위

- Status: completed
- Issue: [#81](https://github.com/omegafrog/dnd-master/issues/81)
- Parent: [#79](https://github.com/omegafrog/dnd-master/issues/79)
- Dependencies: [026-1](026-1-character-tag-extraction.md)

## Outcome

스토리북의 시나리오별 캐릭터 필드·선택지·제약을 기본 템플릿과 병합한다.

## Acceptance criteria

- STORYBOOK 후보가 RULEBOOK 후보보다 우선한다.
- Storybook에만 있는 필드·하위 필드를 보존한다.
- 충돌과 불확실성을 source evidence와 함께 표시한다.
- Storybook 정의가 없으면 RULEBOOK 정의를 사용한다.
- 자유 입력 필드의 문서값은 강제 옵션이 아닌 `suggestions`다.
- 문서에 없는 값은 생성하지 않는다.

## Test contract

- Source precedence, conflict, fallback policy unit tests.
- Merged blueprint API contract tests.
- `ui ~ entity` E2E: RULEBOOK+STORYBOOK 번들에서 Storybook 옵션·근거 확인.

## Implementation scope

Source excerpt/document-type contract, candidate merge policy, blueprint compiler/view/persistence, setup and character API DTOs, unit/API/Playwright tests.
