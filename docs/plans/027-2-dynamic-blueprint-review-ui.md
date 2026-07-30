# 027-2 - 동적 Blueprint 검토·게시 및 입력 트리 UI

- Status: completed
- Issue: [#87](https://github.com/omegafrog/dnd-master/issues/87)
- Parent: [#85](https://github.com/omegafrog/dnd-master/issues/85)
- Dependencies: [027-1](027-1-agent-character-tag-extraction.md)

## Outcome

Agent 결과를 동적 부모·자식 입력 트리로 컴파일하고 사용자가 검토·보완·게시한다.
인덱스 기반 초안 생성 API는 `characterSheetTree`로 검수 가능한 트리를 반환한다.

## Scope

- dynamic root/child/partial extraction compile.
- STORYBOOK 우선, conflict, confidence, evidence 보존.
- deterministic node identity.
- `CharacterCreationBlueprint` persistence/revision/API.
- `POST /api/v1/scenario-packages/{id}/character-blueprint/draft`가 RULEBOOK/STORYBOOK/HANDOUT 인덱스 검색과 Agent 태그 추출을 오케스트레이션한다.
- `CharacterInputTree.tsx` recursive mode-based controls.
- free text, single select, multi select, add-child, review, publish UI.

## Acceptance

- `options.length`가 아닌 명시적 input mode로 control을 결정한다.
- 검토 저장 후 mode/value/parent/evidence가 유지된다.
- 누락 child를 UI에서 추가할 수 있다.
- revision conflict를 감지한다.
- 게시 전에는 캐릭터 생성이 차단된다.

## Tests

- compiler/aggregate/source precedence/revision tests.
- API save/load/publish/conflict tests.
- `CharacterInputTree`/`CharacterCreationPage` tests.
- `ui ~ entity` Playwright: edit → save → same mode/value/evidence → publish.

## Implementation scope

`adventure-service` Blueprint/compiler/repository/API, `web-ui` `CharacterInputTree.tsx`, `CharacterCreationPage.tsx`, `SetupApi.ts`, related unit/API/Playwright tests.
