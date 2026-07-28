# 026 - 문서 기반 캐릭터 생성 입력

- Status: approved
- Issue: [#79](https://github.com/omegafrog/dnd-master/issues/79)
- Product Spec: `docs/specs/product-spec.md`
- Architecture Spec: `docs/specs/architecture-spec.md`
- Slices: [026-1](026-1-character-tag-extraction.md), [026-2](026-2-storybook-character-options.md), [026-3](026-3-character-input-tree.md), [026-4](026-4-character-creation-playwright.md)

## Outcome

룰북과 스토리북에서 추출한 근거로 캐릭터 생성 입력 태그 트리를 만들고, 사용자가 검토·보완·저장한 입력을 실제 캐릭터 생성에 사용한다.

## Acceptance

- 4판·5판 룰북에서 기본 캐릭터 필드와 명시적 입력 타입을 추출한다.
- 추출값은 문서 유형, locator, 원문 근거와 함께 Blueprint에 보존한다.
- 스토리북 정의가 룰북 정의보다 우선하고, 스토리북 전용 필드와 하위 필드를 보존한다.
- 자유 입력 필드의 문서값은 강제 옵션이 아닌 suggestions로 제공한다.
- 누락·부분 추출 필드와 사용자가 추가한 하위 필드를 표현한다.
- 검토 저장 후 input mode, value, parent, evidence를 유지하고 revision 충돌을 감지한다.
- 문서에 없는 값은 생성하지 않는다.
- 문서 선택부터 Blueprint 게시와 근거 기반 캐릭터 생성까지 Playwright로 검증한다.

## Test contract

- Template, extraction, source precedence, tree, revision policy unit tests.
- Blueprint compile, persistence, API response and review/save contract tests.
- `ui ~ entity` Playwright regression covering document selection, evidence display, review, publish, and character creation.

## Implementation scope

`adventure-service` template/extraction/compiler, Blueprint model/persistence/API, `web-ui` DTO and recursive input tree, document fixtures, and related unit/API/Playwright tests.

## Dependency

`026-1 → 026-2 → 026-3 → 026-4`
