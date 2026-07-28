# 026-3 - 동적 캐릭터 입력 태그 트리·타입 보존

- Status: ready-for-agent
- Issue: [#82](https://github.com/omegafrog/dnd-master/issues/82)
- Parent: [#79](https://github.com/omegafrog/dnd-master/issues/79)
- Dependencies: [026-1](026-1-character-tag-extraction.md), [026-2](026-2-storybook-character-options.md)

## Outcome

평면 Blueprint를 동적 부모·자식 태그 트리로 제공하고 검토 저장 후에도 입력 타입과 근거를 보존한다.

## Acceptance criteria

- 노드는 안정적인 identity와 parent 관계를 가진다.
- 부분 추출과 누락 하위 필드를 표현한다.
- 사용자가 누락 하위 필드를 추가할 수 있다.
- `options`와 `suggestions`가 분리된다.
- `FREE_TEXT`는 항상 텍스트 입력으로 표시된다.
- 검토 저장 후 input mode, 값, parent, evidence가 유지된다.
- revision conflict를 감지한다.

## Test contract

- Tree aggregate, add-child, mode/value preservation, revision policy unit tests.
- Tree API 저장·조회·충돌 contract tests.
- `ui ~ entity` E2E: 보완 저장 후 동일 control/value/evidence 확인.

## Implementation scope

`CharacterCreationBlueprint` tree model/revision persistence, tree API payload, frontend recursive `CharacterInputTree`, review/save flow, related unit/API/Playwright tests.
